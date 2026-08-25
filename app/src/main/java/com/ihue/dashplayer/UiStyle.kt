package com.ihue.dashplayer

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/** Shared visual building blocks for the phone-side screens (MainActivity/AboutActivity/
 *  FavoritesActivity) — a dark, card-based look built from plain framework widgets, matching
 *  the rest of this app's programmatic-view style (no XML layouts, no Material dependency). */

/** Rounded "surface_dark" card background used to group related content. */
fun Activity.cardBackground(radiusDp: Int = 14): GradientDrawable = GradientDrawable().apply {
    cornerRadius = radiusDp * resources.displayMetrics.density
    setColor(ContextCompat.getColor(this@cardBackground, R.color.surface_dark))
}

/** A vertical section wrapped in [cardBackground], with standard internal padding/margin. */
fun Activity.sectionCard(dp: (Int) -> Int): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = cardBackground()
    setPadding(dp(16), dp(14), dp(16), dp(14))
    layoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(14) }
}

/** Filled, accent-colored primary action button. */
fun Activity.primaryButton(label: String, dp: (Int) -> Int, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        val shape = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(ContextCompat.getColor(context, R.color.accent))
        }
        background = RippleDrawable(ColorStateList.valueOf(0x33000000), shape, shape)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        setOnClickListener { onClick() }
    }

/** Outlined "ghost" secondary action button. */
fun Activity.secondaryButton(label: String, dp: (Int) -> Int, onClick: () -> Unit): Button =
    Button(this).apply {
        text = label
        isAllCaps = false
        val accent = ContextCompat.getColor(context, R.color.accent)
        setTextColor(accent)
        val shape = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setStroke((1.5f * resources.displayMetrics.density).toInt(), accent)
            setColor(Color.TRANSPARENT)
        }
        background = RippleDrawable(ColorStateList.valueOf(0x33FF3B1A), shape, shape)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        setOnClickListener { onClick() }
    }
