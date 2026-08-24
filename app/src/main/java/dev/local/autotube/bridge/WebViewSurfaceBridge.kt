package dev.local.autotube.bridge

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.car.app.SurfaceContainer
import dev.local.autotube.data.DisplayScaleSettings

/**
 * The actual "trick" that makes video/arbitrary sites show up on the car screen.
 *
 * Android Auto's NavigationTemplate gives a raw Surface via SurfaceCallback — it does NOT
 * give you a normal View hierarchy, and a WebView can't be attached to that Surface
 * directly. What this class does:
 *
 *   1. Hosts the WebView as the content view of a `Presentation` shown on a private,
 *      in-process `VirtualDisplay` — this gives the WebView a REAL attached window (so
 *      it renders exactly as it would on a normal screen, hardware video decode
 *      included), while never being visible anywhere physical.
 *   2. That VirtualDisplay's output feeds an `ImageReader`. Each time a new composited
 *      frame is available, its buffer is copied into a Bitmap and blitted onto the car's
 *      actual Surface.
 *   3. Taps/scrolls arrive from the car as onClick/onScroll callbacks with x/y
 *      coordinates; we replay them as synthetic MotionEvents dispatched to the WebView.
 *
 * Earlier version history: this used to call `webView.draw(canvas)` into a software
 * canvas from `Surface.lockCanvas()`, with the WebView never attached to any window at
 * all. That capture path only sees software-rendered content — Chromium's <video>
 * decode goes through its own internal SurfaceTexture/hardware-overlay path regardless
 * of the WebView's Android-level layer type, so video was invisible (audio-only) no
 * matter what `View.setLayerType` was set to. Reading back the real composited buffer
 * via ImageReader — which requires an actually-attached window, hence the
 * VirtualDisplay/Presentation — is what's needed to capture it. Unverified until tested
 * on the real car (see PROGRESS.md bug #2).
 */
class WebViewSurfaceBridge(
    private val webView: WebView
) {
    // The car Surface is relatively narrow in raw pixels even on a wide display.  Web
    // sites use those pixels for responsive breakpoints, so rendering at native size
    // makes YouTube select its phone UI.  Give the hidden WebView a real desktop-width
    // viewport, then scale its frame down to the fixed-resolution car Surface.
    private var virtualDisplayScale = 1.5f
    private var surfaceContainer: SurfaceContainer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var reusableBitmap: Bitmap? = null
    private var reusableBitmapPaddedWidth: Int = 0

    /** The page actually showing right now — single source of truth, since this bridge
     *  (and its WebView) can outlive any one PlaybackScreen instance (PlaybackSession
     *  reuses it across "Browse YouTube" re-entries). Drifts from whatever URL was last
     *  passed to [loadUrl] once the user navigates within the page itself (e.g. clicking
     *  another video) — tracked via WebViewClient/WebChromeClient below. */
    var currentUrl: String = ""
        private set
    var currentTitle: String = ""
        private set

    init {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                currentUrl = url
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                // YouTube is a single-page app. This callback follows its committed
                // history URL, whereas onPageFinished can arrive late for the previous
                // Home document and overwrite a video/favorite URL.
                currentUrl = url
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                currentTitle = title
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        // WebView's default user-agent identifies as a phone, so YouTube (and most
        // sites) serve their mobile-app-style layout — bottom tab bar, single-column
        // feed, video pinned to a narrow centered box — regardless of the actual pixel
        // size of the car's Surface. Confirmed via real-car screenshots: video played
        // correctly but was letterboxed into a small portrait-ish area, and the YouTube
        // homepage showed the mobile Home/Shorts/You bottom nav. Requesting the desktop
        // UA gets YouTube's widescreen grid/player layout instead, which actually fits
        // a landscape car display.
        webView.settings.userAgentString =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        // Do not use WebView's overview or initial-scale modes here. Both calculate and
        // apply a scale asynchronously after page layout, which created the visible
        // multi-step zoom/re-render effect. DisplayScaleSettings is the only scale.
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = false
        webView.settings.textZoom = 100
        webView.settings.builtInZoomControls = false
        webView.settings.displayZoomControls = false
        // Third-party cookies matter for Google/YouTube login flows (cross-domain
        // iframes/redirects); flush() on destroy() below guarantees whatever session
        // exists is actually on disk even if the process gets killed right after.
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    /** Creates the VirtualDisplay/Presentation/ImageReader sized to the car's Surface and
     *  starts blitting frames into it. Safe to call repeatedly (e.g. surface resize) —
     *  tears down any previous render target first. */
    fun attachSurface(container: SurfaceContainer) {
        detachRenderTarget()
        surfaceContainer = container
        val w = container.width
        val h = container.height
        if (w <= 0 || h <= 0) return
        virtualDisplayScale = DisplayScaleSettings.get(webView.context)

        // This is deliberately a larger *source* frame, not an attempt to claim a
        // higher physical car-screen resolution. It changes WebView's true viewport
        // width (which YouTube's responsive code observes) and produces a compact
        // desktop/tablet layout once scaled to the host's fixed surface.
        val renderWidth = (w * virtualDisplayScale).toInt()
        val renderHeight = (h * virtualDisplayScale).toInt()
        val reader = ImageReader.newInstance(renderWidth, renderHeight, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        val appContext = webView.context.applicationContext
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        // Deliberately NOT using `container.dpi` here. That value is tuned for the car
        // host's own map/template rendering (typically phone-like density), which makes
        // the WebView's CSS dp-width come out narrow enough to still trip YouTube's
        // mobile/responsive breakpoint even with a desktop UA (confirmed: real-car
        // screenshots showed the mobile bottom-nav despite the desktop UA change).
        // Keep a conventional mdpi density. The enlarged *pixel dimensions* above are
        // what reliably increase window.innerWidth/CSS viewport width; changing density
        // alone did not affect YouTube's client-side mobile-layout decision.
        val webViewDensityDpi = android.util.DisplayMetrics.DENSITY_DEFAULT
        val vd = displayManager.createVirtualDisplay(
            "AutoTubePlaybackDisplay",
            renderWidth, renderHeight, webViewDensityDpi,
            reader.surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        )
        virtualDisplay = vd
        val display = vd.display ?: return

        // Presentation (a Dialog subclass) needs a themed Context to create its window.
        val themedContext = ContextThemeWrapper(appContext, android.R.style.Theme_DeviceDefault_NoActionBar)
        val pres = Presentation(themedContext, display)
        presentation = pres

        // The WebView is a single long-lived instance reused across attach/detach cycles
        // (PlaybackSession) — must be detached from any previous parent before reparenting.
        (webView.parent as? ViewGroup)?.removeView(webView)
        pres.setContentView(
            webView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        pres.show()

        reader.setOnImageAvailableListener({ blitLatestImage(reader) }, handler)
    }

    private fun blitLatestImage(reader: ImageReader) {
        val container = surfaceContainer
        val carSurface = container?.surface
        if (container == null || carSurface == null || !carSurface.isValid) {
            drainReader(reader)
            return
        }

        val image = try {
            reader.acquireLatestImage()
        } catch (t: Throwable) {
            null
        } ?: return

        try {
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride

            var bmp = reusableBitmap
            if (bmp == null || reusableBitmapPaddedWidth != paddedWidth || bmp.height != image.height) {
                bmp?.recycle()
                bmp = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                reusableBitmap = bmp
                reusableBitmapPaddedWidth = paddedWidth
            }
            bmp.copyPixelsFromBuffer(plane.buffer)

            var canvas: Canvas? = null
            try {
                canvas = carSurface.lockCanvas(Rect(0, 0, container.width, container.height))
                canvas.drawColor(android.graphics.Color.BLACK)
                canvas.drawBitmap(
                    bmp,
                    Rect(0, 0, image.width, image.height),
                    Rect(0, 0, container.width, container.height),
                    null
                )
            } catch (t: Throwable) {
                // Surface can go away mid-frame (screen switch, car disconnect) — skip this frame.
            } finally {
                canvas?.let { carSurface.unlockCanvasAndPost(it) }
            }
        } finally {
            image.close()
        }
    }

    private fun drainReader(reader: ImageReader) {
        try {
            reader.acquireLatestImage()?.close()
        } catch (t: Throwable) {
            // Reader may already be closing.
        }
    }

    fun detachSurface() {
        detachRenderTarget()
        surfaceContainer = null
    }

    private fun detachRenderTarget() {
        imageReader?.setOnImageAvailableListener(null, null)
        presentation?.dismiss()
        presentation = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        reusableBitmap?.recycle()
        reusableBitmap = null
    }

    /** Permanently shuts this WebView down — tears down the render target, stops any
     *  playing media/audio, and releases the WebView's native resources. Call this only
     *  on an explicit user action (PlaybackSession.close()) — never automatically on a
     *  lifecycle event, which previously caused real regressions (in-page search and
     *  login sessions silently breaking). See PROGRESS.md bug #4. */
    fun destroy() {
        detachRenderTarget()
        surfaceContainer = null
        android.webkit.CookieManager.getInstance().flush()
        webView.stopLoading()
        webView.onPause()
        webView.loadUrl("about:blank")
        webView.destroy()
    }

    fun loadUrl(url: String) {
        currentUrl = url
        currentTitle = url
        webView.loadUrl(url)
    }

    /** In-page browser history, for "Back" to undo navigation within the page (e.g.
     *  leave a video, back to YouTube's home) before falling back to leaving the screen
     *  entirely. */
    fun canGoBack(): Boolean = webView.canGoBack()

    fun goBack() {
        webView.goBack()
    }

    /** Replays a tap from the car screen onto the WebView. */
    fun dispatchClick(x: Float, y: Float) {
        // SurfaceCallback coordinates are in the car Surface's coordinate system;
        // WebView is now rendered into the larger virtual-display source frame.
        val webX = x * virtualDisplayScale
        val webY = y * virtualDisplayScale
        val now = android.os.SystemClock.uptimeMillis()
        val downConsumed = webView.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, webX, webY, 0)
        )
        val upConsumed = webView.dispatchTouchEvent(
            MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, webX, webY, 0)
        )
        android.util.Log.d(
            "AutoTubeDebug",
            "dispatchClick surface=$x,$y web=$webX,$webY webViewSize=${webView.width}x${webView.height} downConsumed=$downConsumed upConsumed=$upConsumed"
        )
    }

    /**
     * Scrolls the WebView viewport by the host-provided delta.
     *
     * SurfaceCallback's distances are already scroll distances (not pointer
     * coordinates). The former DOWN/MOVE/UP replay both inverted that meaning and was
     * frequently below Chromium's touch-slop threshold, so many legitimate car scroll
     * events were discarded as taps. Scrolling the viewport directly matches the
     * callback contract and works for pages whose scrolling element is the document,
     * including YouTube.
     */
    fun dispatchScroll(distanceX: Float, distanceY: Float) {
        webView.scrollBy(distanceX.toInt(), distanceY.toInt())
    }

    /** Uses the optional host fling callback when available for natural continuous scroll. */
    fun dispatchFling(velocityX: Float, velocityY: Float) {
        webView.flingScroll(velocityX.toInt(), velocityY.toInt())
    }

    /** Reads the current playback position (seconds) of the page's <video> element, if any —
     *  used to save a resumable position into watch history. Calls back with -1 if there's
     *  no video on the page (e.g. browsing, not watching). */
    fun currentPositionSeconds(callback: (Int) -> Unit) {
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');" +
                "return v ? Math.floor(v.currentTime) : -1;})();"
        ) { result ->
            callback(result?.toDoubleOrNull()?.toInt() ?: -1)
        }
    }
}
