package dev.local.autotube.car

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import dev.local.autotube.bridge.WebViewSurfaceBridge
import dev.local.autotube.data.AutoTubeDatabase
import dev.local.autotube.data.SavedItem
import dev.local.autotube.data.WatchHistory
import kotlinx.coroutines.launch

/**
 * This is the screen that actually gets a WebView onto the car display.
 *
 * NavigationTemplate is the one Car App Library template that leaves the bulk of the
 * screen free for a raw Surface (the part normally used for a live map). We register a
 * SurfaceCallback via AppManager to get that Surface, and hand its frames to
 * WebViewSurfaceBridge, which is the piece doing the WebView -> Bitmap -> Surface blit
 * and the touch replay in the other direction.
 */
class PlaybackScreen(
    carContext: CarContext,
    private val url: String
) : Screen(carContext), DefaultLifecycleObserver {

    private val hiddenWebView = WebView(carContext)
    private val bridge = WebViewSurfaceBridge(hiddenWebView)
    private val drivingGate = DrivingStateGate(carContext)

    // Tracks the page actually showing (drifts from the constructor's `url` once the user
    // navigates within the WebView, e.g. clicking another video) — used for history + "Save".
    private var currentUrl: String = url
    private var currentTitle: String = url

    private val historyHandler = Handler(Looper.getMainLooper())
    private val historyIntervalMs = 15_000L
    private val historyRunnable = object : Runnable {
        override fun run() {
            saveHistoryIfApplicable()
            historyHandler.postDelayed(this, historyIntervalMs)
        }
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            android.util.Log.d("AutoTubeDebug", "onSurfaceAvailable w=${surfaceContainer.width} h=${surfaceContainer.height} dpi=${surfaceContainer.dpi}")
            if (!drivingGate.isRenderingAllowed) return
            bridge.attachSurface(surfaceContainer)
            bridge.loadUrl(url)
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            bridge.detachSurface()
        }

        override fun onClick(x: Float, y: Float) {
            android.util.Log.d("AutoTubeDebug", "onClick x=$x y=$y allowed=${drivingGate.isRenderingAllowed}")
            if (drivingGate.isRenderingAllowed) bridge.dispatchClick(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            android.util.Log.d("AutoTubeDebug", "onScroll dx=$distanceX dy=$distanceY")
            if (drivingGate.isRenderingAllowed) {
                // Approximate scroll origin as screen center; refine once you've tested
                // against your Jimny's actual input hardware (touch vs rotary controller).
                bridge.dispatchScroll(distanceX, distanceY, 0f, 0f)
            }
        }
    }

    init {
        lifecycle.addObserver(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
        bridge.onPageFinished = { newUrl -> currentUrl = newUrl }
        bridge.onTitleChanged = { title -> currentTitle = title }
    }

    override fun onStart(owner: LifecycleOwner) {
        drivingGate.onScreenStarted()
        historyHandler.post(historyRunnable)
    }

    override fun onStop(owner: LifecycleOwner) {
        // Non-negotiable safety behavior: stop rendering the instant this screen isn't
        // the active foreground screen — see DrivingStateGate for the full rationale
        // and the TODO to wire in real speed/parking-brake sensor data.
        drivingGate.onScreenStopped()
        bridge.detachSurface()
        historyHandler.removeCallbacks(historyRunnable)
        saveHistoryIfApplicable()
    }

    /** Extracts a YouTube video ID from a watch URL (both youtube.com/watch?v= and
     *  youtu.be/ forms) — null if the current page isn't a video (e.g. browsing/search). */
    private fun extractYouTubeVideoId(url: String): String? {
        Regex("""[?&]v=([\w-]{6,})""").find(url)?.let { return it.groupValues[1] }
        Regex("""youtu\.be/([\w-]{6,})""").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun saveHistoryIfApplicable() {
        val videoId = extractYouTubeVideoId(currentUrl) ?: return
        val title = currentTitle
        bridge.currentPositionSeconds { position ->
            lifecycleScope.launch {
                AutoTubeDatabase.get(carContext).dao().upsertHistory(
                    WatchHistory(
                        videoId = videoId,
                        title = title,
                        thumbnailUrl = null,
                        lastPositionSeconds = if (position >= 0) position else 0,
                        watchedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override fun onGetTemplate(): Template {
        return NavigationTemplate.Builder()
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.BACK)
                    .addAction(
                        Action.Builder()
                            .setTitle("Save")
                            .setOnClickListener { saveCurrentAsFavorite() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun saveCurrentAsFavorite() {
        val url = currentUrl
        val title = currentTitle
        lifecycleScope.launch {
            AutoTubeDatabase.get(carContext).dao().upsertSavedItem(
                SavedItem(
                    type = UrlUtils.guessType(url),
                    title = title,
                    url = url,
                    thumbnailUrl = null,
                    lastOpenedAt = System.currentTimeMillis()
                )
            )
            CarToast.makeText(carContext, "Saved to favorites", CarToast.LENGTH_SHORT).show()
        }
    }
}
