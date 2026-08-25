package com.ihue.dashplayer.data

import android.content.Context

/**
 * The one authoritative control for playback content size. The value is the
 * VirtualDisplay-to-car-surface ratio: larger values render a wider desktop viewport
 * and therefore make the final content smaller.
 */
object DisplayScaleSettings {
    data class Option(val scale: Float, val title: String, val description: String)

    val options = listOf(
        Option(1.0f, "Normal (100%)", "Largest content; may use mobile layouts"),
        Option(1.25f, "Balanced (80%)", "Smaller content with a wider viewport"),
        Option(1.5f, "Compact (67%)", "Recommended desktop layout"),
        Option(1.75f, "Extra compact (57%)", "Smallest content; uses more processing")
    )

    private const val preferencesName = "dashplayer_display"
    private const val scaleKey = "virtual_display_scale"
    private const val defaultScale = 1.5f

    fun get(context: Context): Float = context.applicationContext
        .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        .getFloat(scaleKey, defaultScale)
        .takeIf { saved -> options.any { it.scale == saved } }
        ?: defaultScale

    fun set(context: Context, scale: Float) {
        require(options.any { it.scale == scale })
        context.applicationContext
            .getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
            .edit()
            .putFloat(scaleKey, scale)
            .apply()
    }

    fun currentOption(context: Context): Option =
        options.first { it.scale == get(context) }
}
