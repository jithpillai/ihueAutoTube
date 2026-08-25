package com.ihue.dashplayer

import android.app.Activity
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Shared "Powered by ihue" branding row, used on the phone-side landing and About screens.
 * [heroSize] switches between the small footer use on About and the larger, more legible
 * hero placement directly under MainActivity's app logo/title.
 */
fun Activity.poweredByIhueView(dp: (Int) -> Int, heroSize: Boolean = false): LinearLayout {
    val logoSize = if (heroSize) dp(40) else dp(28)
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, if (heroSize) dp(12) else dp(32), 0, 0)
        addView(
            TextView(this@poweredByIhueView).apply {
                text = "Powered by"
                textSize = if (heroSize) 13f else 12f
                setTextColor(0x99FFFFFF.toInt())
                setPadding(0, 0, dp(6), 0)
            }
        )
        addView(
            ImageView(this@poweredByIhueView).apply {
                setImageResource(R.drawable.ihue_logo_white)
                layoutParams = LinearLayout.LayoutParams(logoSize, logoSize)
            }
        )
    }
}
