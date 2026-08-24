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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.local.autotube.data.LocalVideo

/** Native local-file playback directly to Android Auto's raw Surface. */
class LocalVideoPlaybackScreen(
    carContext: CarContext,
    private val video: LocalVideo
) : Screen(carContext), DefaultLifecycleObserver {
    private val player = ExoPlayer.Builder(carContext).build().apply {
        setMediaItem(MediaItem.fromUri(video.uri))
        prepare()
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
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
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
    }

    override fun onStop(owner: LifecycleOwner) {
        player.pause()
        player.clearVideoSurface()
        invalidate()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        player.release()
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
                .build()
        )
        .build()
}
