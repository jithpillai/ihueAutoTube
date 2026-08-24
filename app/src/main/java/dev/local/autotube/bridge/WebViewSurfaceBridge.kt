package dev.local.autotube.bridge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.car.app.SurfaceContainer
import androidx.car.app.navigation.model.NavigationTemplate

/**
 * The actual "trick" that makes video/arbitrary sites show up on the car screen.
 *
 * Android Auto's NavigationTemplate gives a raw Surface via SurfaceCallback — it does NOT
 * give you a normal View hierarchy. A WebView is a View, so it can't be attached to that
 * Surface directly. Instead:
 *
 *   1. We keep a real WebView alive off-screen (never attached to any visible layout).
 *   2. On a timer, we draw the WebView's current contents into a Bitmap
 *      (webView.draw(canvas)), then blit that bitmap onto the car's Surface.
 *   3. Taps/scrolls arrive from the car as onClick/onScroll callbacks with x/y coordinates;
 *      we replay them as synthetic MotionEvents dispatched to the hidden WebView.
 *
 * This is a polling render loop, not a real-time compositor — expect ~10-15fps at best,
 * which is fine for reading/browsing and adequate (not great) for video. This is the
 * inherent ceiling of the approach; there's no way around it without a lower-level
 * (and much riskier) hook into WebView's internal rendering pipeline.
 */
class WebViewSurfaceBridge(
    private val webView: WebView,
    private val frameIntervalMs: Long = 80L // ~12fps
) {
    private var surfaceContainer: SurfaceContainer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    /** Fires whenever the WebView finishes loading a page — lets callers track the live URL
     *  (which drifts from the URL PlaybackScreen was constructed with once the user navigates
     *  within the page, e.g. clicking to another video). */
    var onPageFinished: ((String) -> Unit)? = null

    /** Fires whenever the page's title changes — used for watch-history entries. */
    var onTitleChanged: ((String) -> Unit)? = null

    private val renderLoop = object : Runnable {
        override fun run() {
            if (!running) return
            renderFrame()
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    init {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                onPageFinished?.invoke(url)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String) {
                onTitleChanged?.invoke(title)
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        // Layout the hidden WebView even though it's never added to a visible parent —
        // WebView needs an explicit measure/layout pass to render anything via draw().
    }

    fun attachSurface(container: SurfaceContainer) {
        surfaceContainer = container
        val w = container.width
        val h = container.height
        webView.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, w, h)
        start()
    }

    fun detachSurface() {
        stop()
        surfaceContainer = null
    }

    fun loadUrl(url: String) {
        webView.loadUrl(url)
    }

    private fun start() {
        if (running) return
        running = true
        handler.post(renderLoop)
    }

    private fun stop() {
        running = false
        handler.removeCallbacks(renderLoop)
    }

    private fun renderFrame() {
        val container = surfaceContainer ?: return
        val surface = container.surface ?: return
        if (!surface.isValid) return

        var canvas: Canvas? = null
        try {
            canvas = surface.lockCanvas(Rect(0, 0, container.width, container.height))
            canvas.drawColor(android.graphics.Color.BLACK)
            webView.draw(canvas)
        } catch (t: Throwable) {
            // Surface can go away mid-frame (screen switch, car disconnect) — skip this frame.
        } finally {
            canvas?.let { surface.unlockCanvasAndPost(it) }
        }
    }

    /** Replays a tap from the car screen onto the hidden WebView. */
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
