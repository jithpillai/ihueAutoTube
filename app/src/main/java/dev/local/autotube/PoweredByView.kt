package dev.local.autotube

import android.app.Activity
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shared "Powered by ihue" footer used on the phone-side landing and About screens. */
fun Activity.poweredByIhueView(dp: (Int) -> Int): LinearLayout {
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(32), 0, 0)
        addView(
            TextView(this@poweredByIhueView).apply {
                text = "Powered by"
                textSize = 12f
                setPadding(0, 0, dp(6), 0)
            }
        )
        addView(
            ImageView(this@poweredByIhueView).apply {
                setImageResource(R.drawable.ihue_logo)
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            }
        )
    }
}
