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
                // Even with a desktop UA, real-car testing showed YouTube's own JS
                // re-detects touch capability client-side and switches back to its
                // mobile layout mid-session ("initially renders as web view, but then
                // automatically switches to mobile" per user report, confirmed via
                // screenshots — desktop header initially, mobile layout shortly after).
                // Spoofing away touch-capability signals before the page's own scripts
                // run is the standard "force desktop site" trick browsers use. Not
                // airtight (onPageStarted fires early but isn't guaranteed before every
                // inline <head> script), but low-risk and worth trying alongside the
                // density fix below.
                view.evaluateJavascript(
                    "(function(){try{" +
                        "Object.defineProperty(navigator,'maxTouchPoints',{get:function(){return 0;}});" +
                        "}catch(e){}})();",
                    null
                )
            }

            override fun onPageFinished(view: WebView, url: String) {
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
        // Belt-and-suspenders alongside the desktop UA: make sure WebView actually
        // renders at the Surface's real pixel width instead of a default/narrow assumed
        // viewport, and don't fight it with WebView's own pinch-zoom UI (touch replay
        // has no pinch gesture anyway).
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = true
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

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        val appContext = webView.context.applicationContext
        val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        // Deliberately NOT using `container.dpi` here. That value is tuned for the car
        // host's own map/template rendering (typically phone-like density), which makes
        // the WebView's CSS dp-width come out narrow enough to still trip YouTube's
        // mobile/responsive breakpoint even with a desktop UA (confirmed: real-car
        // screenshots showed the mobile bottom-nav despite the desktop UA change).
        // Using baseline density (160 = 1dp per physical pixel) maximizes the reported
        // CSS viewport width for the same physical pixel count, keeping it solidly in
        // desktop-breakpoint territory regardless of what the host reports.
        val webViewDensityDpi = android.util.DisplayMetrics.DENSITY_DEFAULT
        val vd = displayManager.createVirtualDisplay(
            "AutoTubePlaybackDisplay",
            w, h, webViewDensityDpi,
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
        val now = android.os.SystemClock.uptimeMillis()
        val downConsumed = webView.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        )
        val upConsumed = webView.dispatchTouchEvent(
            MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0)
        )
        android.util.Log.d(
            "AutoTubeDebug",
            "dispatchClick x=$x y=$y webViewSize=${webView.width}x${webView.height} downConsumed=$downConsumed upConsumed=$upConsumed"
        )
    }

    /** Replays a scroll/fling gesture from the car's rotary controller or touchpad. */
    fun dispatchScroll(distanceX: Float, distanceY: Float, startX: Float, startY: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        webView.dispatchTouchEvent(
            MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, startX, startY, 0)
        )
        webView.dispatchTouchEvent(
            MotionEvent.obtain(
                now, now + 16, MotionEvent.ACTION_MOVE,
                startX + distanceX, startY + distanceY, 0
            )
        )
        webView.dispatchTouchEvent(
            MotionEvent.obtain(
                now, now + 32, MotionEvent.ACTION_UP,
                startX + distanceX, startY + distanceY, 0
            )
        )
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
