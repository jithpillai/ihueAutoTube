package com.ihue.dashplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Brief branded loading screen shown before MainActivity, mirroring the car-side splash. */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val goToMain = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(ContextCompat.getColor(this@SplashActivity, R.color.bg_dark))
        }

        root.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.app_logo)
                layoutParams = LinearLayout.LayoutParams(dp(140), dp(140))
            }
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.app_name)
                textSize = 28f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
            }
        )
        root.addView(poweredByIhueView(dp, heroSize = true))

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(goToMain, 1600)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(goToMain)
    }
}
