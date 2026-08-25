package com.ihue.dashplayer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.ihue.dashplayer.R

/**
 * A single "Menu" action strip button on PlaybackScreen opens this instead of showing
 * Back/Home/Search/Save as four separate buttons — consolidating them per explicit user
 * request (they felt cluttered, especially with the car's already-large button styling).
 * Each row pops this menu (revealing PlaybackScreen again) before invoking its action, so
 * PlaybackScreen is the visible screen underneath by the time e.g. Search pushes on top,
 * or a "Saved" toast appears.
 */
class PlaybackMenuScreen(
    carContext: CarContext,
    private val onHome: () -> Unit,
    private val onManageFavorites: () -> Unit,
    private val onSave: () -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Home")
                    .setImage(carIcon(carContext, R.drawable.ic_home))
                    .setOnClickListener { onHome() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Save to favorites")
                    .setImage(carIcon(carContext, R.drawable.ic_star))
                    .setOnClickListener {
                        screenManager.pop()
                        onSave()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Manage favorites")
                    .setImage(carIcon(carContext, R.drawable.ic_folder))
                    .setOnClickListener { onManageFavorites() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Display size")
                    .setImage(carIcon(carContext, R.drawable.ic_zoom))
                    .setOnClickListener {
                        screenManager.push(DisplayScaleScreen(carContext) {
                            screenManager.pop()
                        })
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("Menu")
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
