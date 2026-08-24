package dev.local.autotube.local

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import dev.local.autotube.data.LocalVideo
import dev.local.autotube.data.LocalVideoProgress

/** Process-wide native player shared by the car Surface and MediaSessionService. */
object LocalVideoPlayer {
    private var playerInstance: ExoPlayer? = null

    fun player(context: Context): ExoPlayer = synchronized(this) {
        playerInstance ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build().also {
            playerInstance = it
        }
    }

    fun play(context: Context, video: LocalVideo): ExoPlayer {
        val player = player(context)
        if (player.currentMediaItem?.localConfiguration?.uri != video.uri) {
            player.setMediaItem(MediaItem.fromUri(video.uri), LocalVideoProgress.position(context, video.uri))
            player.prepare()
        }
        player.play()
        return player
    }

    fun release() = synchronized(this) {
        playerInstance?.release()
        playerInstance = null
    }

    fun stop() = synchronized(this) {
        playerInstance?.stop()
        playerInstance?.clearMediaItems()
    }
}
