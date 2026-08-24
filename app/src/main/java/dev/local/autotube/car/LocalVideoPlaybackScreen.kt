package dev.local.autotube.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
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
import dev.local.autotube.data.LocalVideo
import dev.local.autotube.data.LocalVideoProgress
import dev.local.autotube.local.LocalVideoPlayer

/** Native local-file playback directly to Android Auto's raw Surface. */
class LocalVideoPlaybackScreen(
    carContext: CarContext,
    private val video: LocalVideo
) : Screen(carContext), DefaultLifecycleObserver {
    private val player = LocalVideoPlayer.open(carContext, video) { invalidate() }
    private val drivingGate = DrivingStateGate(carContext)

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            if (!drivingGate.isRenderingAllowed) return
            player.setSurface(surfaceContainer.surface)
            player.play()
            invalidate()
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            player.setSurface(null)
        }
    }

    init {
        lifecycle.addObserver(this)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }

    override fun onStop(owner: LifecycleOwner) {
        LocalVideoProgress.save(carContext, video.uri, player.currentPosition)
        drivingGate.onScreenStopped()
        player.setSurface(null)
        invalidate()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // Navigating to the controls/time-entry screen can destroy and recreate this Screen.
        // The player must outlive that UI transition so a seek never reloads from zero.
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
                        .setTitle("+10")
                        .setOnClickListener { seekBy(10_000L) }
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
                onForward = { seekBy(-10_000L) },
                onGoto = {
                    screenManager.push(LocalVideoSeekScreen(carContext) { position ->
                        seekTo(position)
                    })
                },
                onStop = {
                    LocalVideoProgress.clear(carContext, video.uri)
                    LocalVideoPlayer.stop()
                    screenManager.popToRoot()
                },
                progressText = {
                    formatDuration(player.currentPosition) + " / " + formatDuration(playableDuration())
                }
            )
        )
    }

    private fun seekBy(deltaMs: Long) {
        val duration = playableDuration().takeIf { it > 0 } ?: Long.MAX_VALUE
        val requested = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        player.seekTo(if (duration == Long.MAX_VALUE) requested else requested.coerceAtMost(duration))
        invalidate()
    }

    private fun seekTo(positionMs: Long) {
        val duration = playableDuration().takeIf { it > 0 }
        val target = if (duration == null) positionMs else positionMs.coerceAtMost(duration)
        player.seekTo(target)
        invalidate()
    }

    /**
     * Some document-provider video sources report TIME_UNSET to ExoPlayer even
     * though the library scan already extracted the file duration.
     */
    private fun playableDuration(): Long = player.duration.takeIf { it > 0 } ?: video.durationMs

    private fun formatDuration(value: Long): String {
        if (value <= 0L) return "0:00"
        val seconds = value / 1_000L
        val hours = seconds / 3_600
        val minutes = (seconds % 3_600) / 60
        return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
        else "$minutes:${(seconds % 60).toString().padStart(2, '0')}"
    }
}
