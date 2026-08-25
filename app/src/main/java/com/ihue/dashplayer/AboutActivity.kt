package com.ihue.dashplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class AboutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(48), dp(32), dp(48))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        root.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.app_logo)
                layoutParams = LinearLayout.LayoutParams(dp(100), dp(100))
            }
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.app_name)
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(4))
            }
        )

        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "0.1"
        root.addView(
            TextView(this).apply {
                text = "Version $versionName"
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(24))
            }
        )

        root.addView(
            TextView(this).apply {
                text = "A personal Android Auto app for browsing YouTube and the web from the car screen."
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(32))
            }
        )

        root.addView(
            TextView(this).apply {
                text = "Developed by ihue.in"
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(4))
            }
        )
        root.addView(
            TextView(this).apply {
                text = "ihue.india@gmail.com"
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(0xFF1A73E8.toInt())
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:ihue.india@gmail.com")))
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
    }
}
