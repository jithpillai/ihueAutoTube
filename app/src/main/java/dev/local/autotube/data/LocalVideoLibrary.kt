package dev.local.autotube.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class LocalVideo(val uri: Uri, val title: String, val mimeType: String?)

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

    /** Runs on a worker thread. Supports folders inside the selected root. */
    fun listVideos(context: Context): List<LocalVideo> {
        val tree = selectedTreeUri(context) ?: return emptyList()
        val root = DocumentFile.fromTreeUri(context, tree) ?: return emptyList()
        val videos = mutableListOf<LocalVideo>()

        fun visit(directory: DocumentFile) {
            if (videos.size >= maxItems) return
            directory.listFiles().forEach { file ->
                if (videos.size >= maxItems) return@forEach
                when {
                    file.isDirectory -> visit(file)
                    file.isFile && isVideo(file) -> videos += LocalVideo(
                        uri = file.uri,
                        title = file.name ?: "Untitled video",
                        mimeType = file.type
                    )
                }
            }
        }
        visit(root)
        return videos.sortedBy { it.title.lowercase() }
    }

    private fun isVideo(file: DocumentFile): Boolean {
        if (file.type?.startsWith("video/") == true) return true
        return file.name?.substringAfterLast('.', "")?.lowercase() in
            setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "3gp")
    }
}
