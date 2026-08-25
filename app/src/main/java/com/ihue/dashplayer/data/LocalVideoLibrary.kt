package dev.local.autotube.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import androidx.documentfile.provider.DocumentFile

data class LocalVideo(
    val uri: Uri,
    val title: String,
    val mimeType: String?,
    val durationMs: Long = 0L,
    val thumbnail: Bitmap? = null
)
data class LocalVideoFolder(val uri: Uri, val title: String)
data class LocalVideoEntry(val folder: LocalVideoFolder? = null, val video: LocalVideo? = null)

/** Stores a user-chosen media folder and exposes only its video files to the car UI. */
object LocalVideoLibrary {
    private const val preferencesName = "autotube_local_videos"
    private const val treeUriKey = "tree_uri"
    private const val maxItems = 500

    fun selectedTreeUri(context: Context): Uri? = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        .getString(treeUriKey, null)
        ?.let(Uri::parse)

    fun saveSelectedTree(context: Context, uri: Uri) {
        context.applicationContext
            .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putString(treeUriKey, uri.toString())
            .apply()
    }

    /** Runs on a worker thread. Lists one folder at a time for a responsive car UI. */
    fun listEntries(context: Context, directoryUri: Uri? = null): List<LocalVideoEntry> {
        val rootUri = directoryUri ?: selectedTreeUri(context) ?: return emptyList()
        val directory = if (directoryUri == null) DocumentFile.fromTreeUri(context, rootUri)
        else DocumentFile.fromSingleUri(context, rootUri)
        return directory?.listFiles()?.take(maxItems)?.mapNotNull { file ->
            when {
                file.isDirectory -> LocalVideoEntry(folder = LocalVideoFolder(file.uri, file.name ?: "Folder"))
                file.isFile && isVideo(file) -> LocalVideoEntry(video = videoFrom(context, file))
                else -> null
            }
        }?.sortedWith(compareBy<LocalVideoEntry>({ it.folder == null }, { it.folder?.title ?: it.video?.title ?: "" })) ?: emptyList()
    }

    /** Compatibility flat view used by the initial car library screen. */
    fun listVideos(context: Context): List<LocalVideo> {
        val rootUri = selectedTreeUri(context) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        val videos = mutableListOf<LocalVideo>()
        fun visit(directory: DocumentFile) {
            if (videos.size >= maxItems) return
            directory.listFiles().forEach { file ->
                if (file.isDirectory) visit(file)
                else if (file.isFile && isVideo(file) && videos.size < maxItems) videos += videoFrom(context, file)
            }
        }
        visit(root)
        return videos.sortedBy { it.title.lowercase() }
    }

    /** Search is recursive but intentionally capped, keeping type-ahead usable. */
    fun search(context: Context, query: String): List<LocalVideo> {
        val tree = selectedTreeUri(context) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        val found = mutableListOf<LocalVideo>()
        fun visit(dir: DocumentFile) {
            if (found.size >= maxItems) return
            dir.listFiles().forEach { file ->
                if (file.isDirectory) visit(file)
                else if (file.isFile && isVideo(file) && (file.name ?: "").contains(query, true)) found += videoFrom(context, file)
            }
        }
        visit(root)
        return found
    }

    private fun videoFrom(context: Context, file: DocumentFile): LocalVideo {
        var duration = 0L
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, file.uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            }
        } catch (_: Throwable) { }
        val thumbnail = try { context.contentResolver.loadThumbnail(file.uri, Size(160, 90), null) } catch (_: Throwable) { null }
        return LocalVideo(file.uri, file.name ?: "Untitled video", file.type, duration, thumbnail)
    }

    private fun isVideo(file: DocumentFile): Boolean {
        if (file.type?.startsWith("video/") == true) return true
        return file.name?.substringAfterLast('.', "")?.lowercase() in
            setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "3gp")
    }
}
