package dev.local.autotube.car

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import dev.local.autotube.data.LocalVideo
import dev.local.autotube.data.LocalVideoProgress
import dev.local.autotube.local.LocalVideoPlaybackService
import dev.local.autotube.local.LocalVideoPlayer

/** Native local-file playback directly to Android Auto's raw Surface. */
class LocalVideoPlaybackScreen(
    carContext: CarContext,
    private val video: LocalVideo
) : Screen(carContext), DefaultLifecycleObserver {
    private val player = LocalVideoPlayer.play(carContext, video)
    private val drivingGate = DrivingStateGate(carContext)
    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            CarToast.makeText(carContext, "Can't play this video", CarToast.LENGTH_LONG).show()
        }
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            if (!drivingGate.isRenderingAllowed) return
            player.setVideoSurface(surfaceContainer.surface)
            player.play()
            invalidate()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            player.clearVideoSurface()
        }
    }

    init {
        lifecycle.addObserver(this)
        player.addListener(playerListener)
        ContextCompat.startForegroundService(
            carContext,
            Intent(carContext, LocalVideoPlaybackService::class.java)
        )
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }

    override fun onStop(owner: LifecycleOwner) {
        LocalVideoProgress.save(carContext, video.uri, player.currentPosition)
        drivingGate.onScreenStopped()
        player.clearVideoSurface()
        invalidate()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        player.removeListener(playerListener)
        // The MediaSessionService owns the shared player. Do not release it merely
        // because this car Screen is covered or popped; that would stop locked-phone
        // and background playback.
    }

    override fun onStart(owner: LifecycleOwner) {
        drivingGate.onScreenStarted()
    }

    override fun onGetTemplate(): Template = NavigationTemplate.Builder()
        .setActionStrip(
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setIcon(CarIcon.BACK)
                        .setOnClickListener { screenManager.pop() }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle(if (player.isPlaying) "Pause" else "Play")
                        .setOnClickListener {
                            if (player.isPlaying) player.pause() else player.play()
                            invalidate()
                        }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle("−10")
                        .setOnClickListener { seekBy(-10_000L) }
                        .build()
                )
                .addAction(
                    Action.Builder()
                        .setTitle("Menu")
                        .setOnClickListener { openControls() }
                        .build()
                )
                .build()
        )
        .build()

    private fun openControls() {
        screenManager.push(
            LocalVideoControlsScreen(
                carContext,
                onForward = { seekBy(10_000L) },
                onStop = {
                    LocalVideoProgress.clear(carContext, video.uri)
                    LocalVideoPlayer.stop()
                    carContext.stopService(Intent(carContext, LocalVideoPlaybackService::class.java))
                    screenManager.popToRoot()
                },
                progressText = { formatDuration(player.currentPosition) + " / " + formatDuration(player.duration) }
            )
        )
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val requested = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(if (duration == Long.MAX_VALUE) requested else requested.coerceAtMost(duration))
        invalidate()
    }

    private fun formatDuration(value: Long): String {
        if (value <= 0L || value == androidx.media3.common.C.TIME_UNSET) return "0:00"
        val seconds = value / 1_000L
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }
}
