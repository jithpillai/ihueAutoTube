package com.ihue.dashplayer.data

import android.content.Context
import android.net.Uri

object LocalVideoProgress {
    private const val prefs = "dashplayer_local_video_progress"

    fun position(context: Context, uri: Uri): Long = context.applicationContext
        .getSharedPreferences(prefs, Context.MODE_PRIVATE).getLong(uri.toString(), 0L)

    fun save(context: Context, uri: Uri, positionMs: Long) {
        context.applicationContext.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
            .putLong(uri.toString(), positionMs.coerceAtLeast(0L)).apply()
    }

    fun clear(context: Context, uri: Uri) {
        context.applicationContext.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit()
            .remove(uri.toString()).apply()
    }
}
