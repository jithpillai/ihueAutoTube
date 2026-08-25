package dev.local.autotube.bridge

import android.content.Context
import android.webkit.WebView

/**
 * Holds the single WebView/bridge shared across all playback in the app, so re-entering
 * playback (e.g. "Browse YouTube" from Home, or opening another favorite) returns to the
 * same live WebView — showing whatever's currently on it, video/audio still running —
 * instead of spawning a new background WebView every time (see PROGRESS.md bug #4).
 *
 * Deliberately NOT torn down by Screen lifecycle events (onStop/onDestroy fire on
 * ordinary back navigation and on any screen merely covering PlaybackScreen, e.g. the
 * in-page Search flow — tying teardown to those caused real-car regressions: the search
 * flow's loadUrl silently no-op'd against an already-destroyed WebView, and login
 * sessions never got the chance to persist). The only way this WebView dies is an
 * explicit user action — PlaybackScreen's "Close" button calling [close].
 */
object PlaybackSession {
    private var bridgeInstance: WebViewSurfaceBridge? = null

    val isOpen: Boolean get() = bridgeInstance != null

    /** Returns the shared bridge, creating its WebView on first use. Uses the
     *  application Context (not the CarContext passed in), since this WebView must
     *  outlive any single Screen/CarContext instance. */
    fun bridge(context: Context): WebViewSurfaceBridge {
        bridgeInstance?.let { return it }
        val webView = WebView(context.applicationContext)
        val bridge = WebViewSurfaceBridge(webView)
        bridgeInstance = bridge
        return bridge
    }

    /** Explicit, user-initiated teardown only — stops playback and releases the WebView. */
    fun close() {
        bridgeInstance?.destroy()
        bridgeInstance = null
    }
}
