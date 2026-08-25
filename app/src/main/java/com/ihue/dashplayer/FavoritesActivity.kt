package com.ihue.dashplayer

import android.app.Activity
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ihue.dashplayer.data.DashPlayerDatabase
import com.ihue.dashplayer.data.SavedItem
import com.ihue.dashplayer.data.SavedItemType
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
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.bg_dark))
            setPadding(dp(24), dp(48), dp(24), dp(48))
        }

        root.addView(
            TextView(this).apply {
                text = "Favorites"
                textSize = 24f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, 0, 0, dp(4))
            }
        )
        root.addView(
            TextView(this).apply {
                text = "Channels, playlists, and sites saved from the car app."
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                setPadding(0, 0, 0, dp(20))
            }
        )

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        val scrollRoot = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.bg_dark))
            addView(root)
        }
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
            val dao = DashPlayerDatabase.get(applicationContext).dao()
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
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
            )
            return
        }

        for (item in items) {
            listContainer.addView(favoriteRow(item))
        }
    }

    private fun favoriteRow(item: SavedItem): LinearLayout {
        return sectionCard(dp).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val typeIcon = if (item.type == SavedItemType.SITE) {
                R.drawable.ic_public
            } else {
                R.drawable.ic_play_circle
            }
            addView(
                ImageView(this@FavoritesActivity).apply {
                    setImageResource(typeIcon)
                    setColorFilter(
                        ContextCompat.getColor(context, R.color.accent),
                        PorterDuff.Mode.SRC_IN
                    )
                    layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                        marginEnd = dp(12)
                    }
                }
            )

            addView(
                LinearLayout(this@FavoritesActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    addView(
                        TextView(this@FavoritesActivity).apply {
                            text = item.title
                            textSize = 16f
                            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                            setTypeface(typeface, Typeface.BOLD)
                        }
                    )
                    addView(
                        TextView(this@FavoritesActivity).apply {
                            text = item.url
                            textSize = 12f
                            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                            maxLines = 1
                        }
                    )
                }
            )

            addView(
                ImageButton(this@FavoritesActivity).apply {
                    setImageResource(R.drawable.ic_delete)
                    setColorFilter(
                        ContextCompat.getColor(context, R.color.text_secondary),
                        PorterDuff.Mode.SRC_IN
                    )
                    background = null
                    contentDescription = "Remove"
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                    setOnClickListener {
                        scope.launch {
                            DashPlayerDatabase.get(applicationContext).dao().deleteSavedItem(item.id)
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
