package dev.local.autotube.local

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Keeps local playback alive while the phone is locked or the car screen is covered. */
class LocalVideoPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, LocalVideoPlayer.player(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        LocalVideoPlayer.release()
        super.onDestroy()
    }
}
