package dev.local.autotube.local

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import dev.local.autotube.data.LocalVideo
import dev.local.autotube.data.LocalVideoProgress

/** Native player for document-provider videos, with file-descriptor-backed seeking. */
object LocalVideoPlayer {
    private var session: Session? = null

    fun open(context: Context, video: LocalVideo, onReady: () -> Unit): Session = synchronized(this) {
        val existing = session
        if (existing != null && existing.uri == video.uri.toString()) {
            existing.onReady = onReady
            if (existing.isPrepared) onReady()
            return existing
        }
        existing?.release()
        return Session(context.applicationContext, video, onReady).also { session = it }
    }

    fun stop() = synchronized(this) {
        session?.release()
        session = null
    }

    class Session internal constructor(
        private val context: Context,
        private val video: LocalVideo,
        internal var onReady: () -> Unit
    ) {
        internal val uri = video.uri.toString()
        private val player = MediaPlayer()
        private var playWhenReady = true
        private var pendingSeekMs = LocalVideoProgress.position(context, video.uri)
        var isPrepared = false
            private set

        init {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            player.setOnPreparedListener {
                isPrepared = true
                if (pendingSeekMs > 0L) player.seekTo(pendingSeekMs, MediaPlayer.SEEK_CLOSEST_SYNC)
                if (playWhenReady) player.start()
                onReady()
            }
            player.setDataSource(context, video.uri)
            player.prepareAsync()
        }

        val isPlaying: Boolean
            get() = isPrepared && runCatching { player.isPlaying }.getOrDefault(false)

        val currentPosition: Long
            get() = if (isPrepared) runCatching { player.currentPosition.toLong() }.getOrDefault(0L) else 0L

        val duration: Long
            get() = if (isPrepared) runCatching { player.duration.toLong() }.getOrDefault(video.durationMs) else video.durationMs

        fun setSurface(surface: Surface?) {
            runCatching { player.setSurface(surface) }
        }

        fun play() {
            playWhenReady = true
            if (isPrepared) runCatching { player.start() }
        }

        fun pause() {
            playWhenReady = false
            if (isPrepared) runCatching { player.pause() }
        }

        fun seekTo(positionMs: Long) {
            pendingSeekMs = positionMs.coerceAtLeast(0L)
            LocalVideoProgress.save(context, video.uri, pendingSeekMs)
            if (isPrepared) runCatching { player.seekTo(pendingSeekMs, MediaPlayer.SEEK_CLOSEST_SYNC) }
        }

        internal fun release() {
            runCatching { player.release() }
        }
    }
}
