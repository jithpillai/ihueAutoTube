package dev.local.autotube

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.local.autotube.data.AutoTubeDatabase
import dev.local.autotube.data.SavedItemType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * This app's real UI only ever renders inside Android Auto (car screen / DHU). This phone-side
 * activity exists so tapping the icon outside the car isn't a dead end — it explains what the
 * app does, how to reach it, and shows a quick glance at your saved library.
 */
class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private lateinit var libraryStatusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        fun sectionHeader(text: String) = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(20), 0, dp(6))
        }

        fun bodyText(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setLineSpacing(dp(4).toFloat(), 1f)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(48), dp(32), dp(48))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.app_logo)
                layoutParams = LinearLayout.LayoutParams(dp(112), dp(112))
            }
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.app_name)
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(4))
            }
        )
        root.addView(
            TextView(this).apply {
                text = "Watch YouTube and browse the web on your car's screen via Android Auto."
                textSize = 15f
                gravity = Gravity.CENTER
            }
        )

        root.addView(sectionHeader("How to use"))
        root.addView(
            bodyText(
                "1. Connect to Android Auto — plug in via USB, or wirelessly if your car " +
                    "supports it.\n" +
                    "2. Find the \"AutoTube Player\" icon on your car's screen.\n" +
                    "3. Pick a favorite, continue a video, or browse."
            )
        )

        root.addView(sectionHeader("What it does"))
        root.addView(
            bodyText(
                "• Saves channels, playlists, and sites as favorites\n" +
                    "• Resumes videos where you left off\n" +
                    "• Built for Android Auto and Android Automotive OS"
            )
        )

        root.addView(sectionHeader("Your library"))
        libraryStatusView = bodyText("Loading…")
        root.addView(libraryStatusView)

        root.addView(
            Button(this).apply {
                text = "About"
                setPadding(0, dp(24), 0, 0)
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                }
            }
        )

        root.addView(poweredByIhueView(dp))

        val scrollRoot = ScrollView(this).apply { addView(root) }
        setContentView(scrollRoot)

        // targetSdk 35 draws edge-to-edge by default, so the status bar can overlap the top of
        // the content unless we pad for it ourselves.
        ViewCompat.setOnApplyWindowInsetsListener(scrollRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        scope.launch {
            val dao = AutoTubeDatabase.get(applicationContext).dao()
            val favoritesCount = dao.getSavedItems(SavedItemType.CHANNEL).size +
                dao.getSavedItems(SavedItemType.PLAYLIST).size +
                dao.getSavedItems(SavedItemType.SITE).size
            val hasHistory = dao.getRecentHistory(1).isNotEmpty()

            libraryStatusView.text = when {
                favoritesCount == 0 -> "No favorites saved yet — add some next time you're in the car."
                else -> {
                    val plural = if (favoritesCount == 1) "favorite" else "favorites"
                    "$favoritesCount $plural saved" + if (hasHistory) ", with watch history tracked." else "."
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
