package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

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
    private val onBack: () -> Unit,
    private val onHome: () -> Unit,
    private val onSearch: () -> Unit,
    private val onSave: () -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Back")
                    .setOnClickListener {
                        screenManager.pop()
                        onBack()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Home")
                    .setOnClickListener { onHome() }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Search")
                    .setOnClickListener {
                        screenManager.pop()
                        onSearch()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Save to favorites")
                    .setOnClickListener {
                        screenManager.pop()
                        onSave()
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
