package com.ihue.dashplayer.car

import android.os.Handler
import android.os.Looper
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ihue.dashplayer.bridge.PlaybackSession
import com.ihue.dashplayer.data.DashPlayerDatabase
import com.ihue.dashplayer.data.SavedItem
import com.ihue.dashplayer.data.WatchHistory
import kotlinx.coroutines.launch

/**
 * This is the screen that actually gets a WebView onto the car display.
 *
 * NavigationTemplate is the one Car App Library template that leaves the bulk of the
 * screen free for a raw Surface (the part normally used for a live map). We register a
 * SurfaceCallback via AppManager to get that Surface, and hand its frames to
 * WebViewSurfaceBridge, which is the piece doing the WebView -> Bitmap -> Surface blit
 * and the touch replay in the other direction.
 *
 * The WebView itself lives in [PlaybackSession], shared across PlaybackScreen instances,
 * not owned per-instance — see that class and the [openOrResume]/[openFresh] factories
 * below for why (PROGRESS.md bug #4: an earlier per-instance-WebView design either
 * leaked background WebViews indefinitely, or — the opposite bug — over-eagerly killed
 * the WebView on any lifecycle event, breaking in-page search and login sessions).
 */
class PlaybackScreen private constructor(
    carContext: CarContext,
    private val url: String,
    forceLoad: Boolean
) : Screen(carContext), DefaultLifecycleObserver {

    private val bridge = PlaybackSession.bridge(carContext)
    private val drivingGate = DrivingStateGate(carContext)

    // One-shot: only the very first onSurfaceAvailable after construction should load
    // `url`. The raw Surface gets torn down and recreated any time another screen
    // briefly covers this one (e.g. the Search flow), which re-fires onSurfaceAvailable
    // on this same instance — without this being one-shot, that re-fire would reload
    // `url` every time, silently overwriting whatever the user just navigated to (this
    // was the "search glimpses then reverts" bug from real-car testing).
    private var pendingForceLoad = forceLoad

    private var surfaceWidth = 0
    private var surfaceHeight = 0

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
            android.util.Log.d("DashPlayerDebug", "onSurfaceAvailable w=${surfaceContainer.width} h=${surfaceContainer.height} dpi=${surfaceContainer.dpi}")
            surfaceWidth = surfaceContainer.width
            surfaceHeight = surfaceContainer.height
            if (!drivingGate.isRenderingAllowed) return
            bridge.attachSurface(surfaceContainer)
            if (pendingForceLoad) {
                bridge.loadUrl(url)
                pendingForceLoad = false
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            bridge.detachSurface()
        }

        override fun onClick(x: Float, y: Float) {
            android.util.Log.d("DashPlayerDebug", "onClick x=$x y=$y allowed=${drivingGate.isRenderingAllowed}")
            if (drivingGate.isRenderingAllowed) bridge.dispatchClick(x, y)
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            android.util.Log.d("DashPlayerDebug", "onScroll dx=$distanceX dy=$distanceY")
            if (drivingGate.isRenderingAllowed) {
                bridge.dispatchScroll(distanceX, distanceY)
            }
        }

        override fun onFling(velocityX: Float, velocityY: Float) {
            android.util.Log.d("DashPlayerDebug", "onFling vx=$velocityX vy=$velocityY")
            if (drivingGate.isRenderingAllowed) {
                bridge.dispatchFling(velocityX, velocityY)
            }
        }
    }

    init {
        lifecycle.addObserver(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }

    companion object {
        /** Re-enter playback without disturbing whatever's already running — reuses the
         *  existing shared WebView/session if one is open (same page/video, still
         *  playing) instead of spawning a new one. Use for "just go back to browsing"
         *  entry points (Home's "Browse full YouTube"), not for a deliberately chosen
         *  new destination. */
        fun openOrResume(carContext: CarContext, url: String) {
            val reusing = PlaybackSession.isOpen
            if (reusing) PlaybackSession.bridge(carContext).loadUrl(url)
            carContext.getCarService(ScreenManager::class.java)
                .push(PlaybackScreen(carContext, url, forceLoad = !reusing))
        }

        /** Always starts a fresh WebView, killing whatever was previously running first.
         *  Use whenever the user deliberately picks a specific destination — a favorite,
         *  a history item, a typed URL/search — since that's a new thing they asked for,
         *  not a continuation of whatever was already open. */
        fun openFresh(carContext: CarContext, url: String) {
            // TEMPORARILY behaves like openOrResume — see PROGRESS.md. Real-car testing
            // showed killing the WebView on every fresh destination was compounding with
            // the one-shot-forceLoad bug (now fixed) to make the whole thing feel broken;
            // per explicit user direction, suppressing the auto-kill for now to validate
            // the reuse path in isolation. Restore `PlaybackSession.close()` here once
            // that's confirmed stable on real hardware.
            openOrResume(carContext, url)
        }
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
        val videoId = extractYouTubeVideoId(bridge.currentUrl) ?: return
        val title = bridge.currentTitle
        bridge.currentPositionSeconds { position ->
            lifecycleScope.launch {
                DashPlayerDatabase.get(carContext).dao().upsertHistory(
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
                    .addAction(
                        Action.Builder()
                            .setIcon(CarIcon.BACK)
                            .setOnClickListener { goBack() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(
                                CarIcon.Builder(
                                    IconCompat.createWithResource(
                                        carContext,
                                        com.ihue.dashplayer.R.drawable.ic_search
                                    )
                                ).build()
                            )
                            .setOnClickListener { openSearch() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("Menu")
                            .setIcon(carIcon(carContext, com.ihue.dashplayer.R.drawable.ic_menu))
                            .setOnClickListener { openMenu() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    /** Back/Home/Search/Save used to each be their own action-strip button — consolidated
     *  into one "Menu" button + a small list screen per explicit user request (too much
     *  clutter with 4 large buttons on the car's already-oversized action styling). */
    private fun openMenu() {
        screenManager.push(
            PlaybackMenuScreen(
                carContext,
                onHome = { screenManager.popToRoot() },
                onManageFavorites = { screenManager.push(FavoritesEditScreen(carContext)) },
                onSave = { saveCurrentAsFavorite() }
            )
        )
    }

    /** Browser-style back: undo in-page navigation first (e.g. leave a video, back to
     *  YouTube's home) rather than always leaving the screen outright — matches what the
     *  user actually expects from "Back" here. Only pops this screen off the Car App
     *  stack once there's no more in-page history. Neither this nor "Home" kill the
     *  shared session (see PlaybackSession) — leaving is always just navigation, not a
     *  stop; there's currently no dedicated "stop" action (removed "Close" to cut down
     *  action-strip clutter — see PROGRESS.md). */
    private fun goBack() {
        if (bridge.exitFullscreen()) {
            return
        } else if (bridge.canGoBack()) {
            bridge.goBack()
        } else {
            screenManager.pop()
        }
    }

    /** Raw-Surface content has no attached window for a system keyboard to anchor to, so
     *  in-page search (e.g. YouTube's own search box) can't be typed into directly — see
     *  PROGRESS.md bug #1. Instead, collect the query via a real host SearchTemplate
     *  screen (keyboard + voice, same as BrowserScreen) and navigate this same WebView. */
    private fun openSearch() {
        screenManager.push(
            SearchInPageScreen(carContext, hint = "Search YouTube") { query ->
                bridge.loadUrl(buildSearchUrl(query))
            }
        )
    }

    private fun buildSearchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return if (bridge.currentUrl.contains("youtube.com") || bridge.currentUrl.contains("youtu.be")) {
            "https://www.youtube.com/results?search_query=$encoded"
        } else {
            UrlUtils.normalizeUrl(query)
        }
    }

    private fun saveCurrentAsFavorite() {
        val url = bridge.currentUrl
        val title = bridge.currentTitle
        lifecycleScope.launch {
            DashPlayerDatabase.get(carContext).dao().upsertSavedItem(
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
