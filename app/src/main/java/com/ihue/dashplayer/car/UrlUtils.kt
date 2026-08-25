package com.ihue.dashplayer.car

import com.ihue.dashplayer.data.SavedItemType
import java.net.URLEncoder

/** Shared URL handling for anything that turns free-typed text into a URL/favorite. */
object UrlUtils {

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
    }

    fun guessType(url: String): SavedItemType = when {
        url.contains("list=") -> SavedItemType.PLAYLIST
        url.contains("/channel/") || url.contains("/@") || url.contains("/c/") -> SavedItemType.CHANNEL
        else -> SavedItemType.SITE
    }

    /** A reasonable starting-point title from a URL's host, for prefilling the title prompt. */
    fun guessTitle(url: String): String {
        val host = url
            .substringAfter("://")
            .substringBefore("/")
            .removePrefix("www.")
        return host.ifBlank { url }
    }
}
