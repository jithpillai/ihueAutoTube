package dev.local.autotube

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.local.autotube.data.AutoTubeDatabase
import dev.local.autotube.data.SavedItem
import dev.local.autotube.data.SavedItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Favorites (channels/playlists/sites) are saved from the car app, but they live in the same
 * local Room DB this phone-side process reads too — so they can be viewed and removed here
 * without needing to be back in the car.
 */
class FavoritesActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var listContainer: LinearLayout
    private lateinit var dp: (Int) -> Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        dp = { value: Int -> (value * density).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        root.addView(
            TextView(this).apply {
                text = "Favorites"
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(4))
            }
        )
        root.addView(
            TextView(this).apply {
                text = "Channels, playlists, and sites saved from the car app."
                textSize = 14f
                setPadding(0, 0, 0, dp(20))
            }
        )

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        val scrollRoot = ScrollView(this).apply { addView(root) }
        setContentView(scrollRoot)

        // targetSdk 35 draws edge-to-edge by default, so the status bar can overlap the top of
        // the content unless we pad for it ourselves.
        ViewCompat.setOnApplyWindowInsetsListener(scrollRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        reload()
    }

    private fun reload() {
        scope.launch {
            val dao = AutoTubeDatabase.get(applicationContext).dao()
            val items = dao.getSavedItems(SavedItemType.CHANNEL) +
                dao.getSavedItems(SavedItemType.PLAYLIST) +
                dao.getSavedItems(SavedItemType.SITE)
            render(items)
        }
    }

    private fun render(items: List<SavedItem>) {
        listContainer.removeAllViews()

        if (items.isEmpty()) {
            listContainer.addView(
                TextView(this).apply {
                    text = "No favorites yet — save a channel, playlist, or site from the " +
                        "car app's Menu → Save to favorites."
                    textSize = 15f
                }
            )
            return
        }

        for (item in items) {
            listContainer.addView(favoriteRow(item))
        }
    }

    private fun favoriteRow(item: SavedItem): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))

            addView(
                LinearLayout(this@FavoritesActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(
                        TextView(this@FavoritesActivity).apply {
                            text = item.title
                            textSize = 16f
                            setTypeface(typeface, Typeface.BOLD)
                        }
                    )
                    addView(
                        TextView(this@FavoritesActivity).apply {
                            text = item.url
                            textSize = 12f
                            setTextColor(Color.GRAY)
                            maxLines = 1
                        }
                    )
                }
            )

            addView(
                Button(this@FavoritesActivity).apply {
                    text = "Remove"
                    setOnClickListener {
                        scope.launch {
                            AutoTubeDatabase.get(applicationContext).dao().deleteSavedItem(item.id)
                            reload()
                        }
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
