package dev.local.autotube.local

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.local.autotube.data.LocalVideo
import dev.local.autotube.data.LocalVideoProgress

/** Process-wide player shared across Android Auto screen transitions. */
object LocalVideoPlayer {
    private var playerInstance: ExoPlayer? = null
    private var activeVideoUri: String? = null

    fun player(context: Context): ExoPlayer = synchronized(this) {
        playerInstance ?: ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build().also { playerInstance = it }
    }

    fun play(context: Context, video: LocalVideo): ExoPlayer {
        val player = player(context)
        val uri = video.uri.toString()
        if (activeVideoUri != uri) {
            player.setMediaItem(
                MediaItem.fromUri(video.uri),
                LocalVideoProgress.position(context, video.uri)
            )
            activeVideoUri = uri
            player.prepare()
        }
        player.play()
        return player
    }

    fun release() = synchronized(this) {
        playerInstance?.release()
        playerInstance = null
        activeVideoUri = null
    }

    fun stop() = synchronized(this) {
        playerInstance?.stop()
        playerInstance?.clearMediaItems()
        activeVideoUri = null
    }
}
