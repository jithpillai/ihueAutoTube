package com.ihue.dashplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ihue.dashplayer.data.DashPlayerDatabase
import com.ihue.dashplayer.data.LocalVideoLibrary
import com.ihue.dashplayer.data.SavedItemType
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
    private lateinit var localVideoStatusView: TextView
    private val chooseVideoFolderRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }
        val textSecondary = ContextCompat.getColor(this, R.color.text_secondary)
        val accent = ContextCompat.getColor(this, R.color.accent)

        fun sectionHeader(text: String) = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, dp(6))
        }

        fun bodyText(text: String) = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setLineSpacing(dp(4).toFloat(), 1f)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bg_dark))
            setPadding(dp(24), dp(48), dp(24), dp(48))
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
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(4))
            }
        )
        root.addView(
            TextView(this).apply {
                text = "Watch YouTube and browse the web on your car's screen via Android Auto."
                textSize = 15f
                setTextColor(textSecondary)
                gravity = Gravity.CENTER
            }
        )
        root.addView(poweredByIhueView(dp, heroSize = true))

        root.addView(
            sectionCard(dp).apply {
                addView(sectionHeader("How to use"))
                addView(
                    bodyText(
                        "1. Connect to Android Auto — plug in via USB, or wirelessly if your car " +
                            "supports it.\n" +
                            "2. Find the \"Dash Player\" icon on your car's screen.\n" +
                            "3. Pick a favorite, continue a video, or browse."
                    )
                )
            }
        )

        root.addView(
            sectionCard(dp).apply {
                addView(sectionHeader("What it does"))
                addView(
                    bodyText(
                        "• Saves channels, playlists, and sites as favorites\n" +
                            "• Resumes videos where you left off\n" +
                            "• Built for Android Auto and Android Automotive OS"
                    )
                )
            }
        )

        root.addView(
            sectionCard(dp).apply {
                addView(sectionHeader("Your library"))
                libraryStatusView = bodyText("Loading…")
                addView(libraryStatusView)
                addView(
                    primaryButton("Manage favorites", dp) {
                        startActivity(Intent(this@MainActivity, FavoritesActivity::class.java))
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(14) }
                    }
                )
            }
        )

        root.addView(
            sectionCard(dp).apply {
                addView(sectionHeader("Phone videos"))
                localVideoStatusView = bodyText("")
                addView(localVideoStatusView)
                addView(
                    primaryButton("Choose video folder", dp) {
                        startActivityForResult(
                            Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            ),
                            chooseVideoFolderRequestCode
                        )
                    }.apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(14) }
                    }
                )
            }
        )

        root.addView(
            secondaryButton("About", dp) {
                startActivity(Intent(this@MainActivity, AboutActivity::class.java))
            }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(20) }
            }
        )

        val scrollRoot = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bg_dark))
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

        scope.launch {
            val dao = DashPlayerDatabase.get(applicationContext).dao()
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
        updateLocalVideoStatus()
    }

    private fun updateLocalVideoStatus() {
        localVideoStatusView.text = if (LocalVideoLibrary.selectedTreeUri(this) == null) {
            "No folder selected. Choose a folder once; Dash Player will retain access."
        } else {
            "A video folder is selected. Open “Play videos from phone” in Android Auto."
        }
    }

    @Deprecated("Uses the system folder picker callback for compatibility with Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != chooseVideoFolderRequestCode || resultCode != RESULT_OK) return
        val result = data ?: return
        val uri = result.data ?: return
        val grantedFlags = result.flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, grantedFlags)
        LocalVideoLibrary.saveSelectedTree(this, uri)
        updateLocalVideoStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
