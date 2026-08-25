package com.ihue.dashplayer.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/** Collects a precise local-video timestamp using Android Auto's host keyboard. */
class LocalVideoSeekScreen(
    carContext: CarContext,
    private val onSeek: (Long) -> Unit
) : Screen(carContext) {
    override fun onGetTemplate(): Template = SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
        override fun onSearchTextChanged(searchText: String) = Unit

        override fun onSearchSubmitted(searchText: String) {
            val position = parsePosition(searchText)
            if (position == null) {
                CarToast.makeText(carContext, "Use 1:30:00, 90:00, or 90", CarToast.LENGTH_SHORT).show()
                return
            }
            onSeek(position)
            screenManager.pop()
        }
    })
        .setHeaderAction(androidx.car.app.model.Action.BACK)
        .setSearchHint("Go to: 1:30:00")
        .setShowKeyboardByDefault(true)
        .setItemList(ItemList.Builder().setNoItemsMessage("Enter hours:minutes:seconds").build())
        .build()

    private fun parsePosition(input: String): Long? {
        val parts = input.trim().split(':').map { it.trim().toLongOrNull() ?: return null }
        val seconds = when (parts.size) {
            1 -> parts[0] * 60
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3_600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return seconds.takeIf { it >= 0 }?.times(1_000L)
    }
}
