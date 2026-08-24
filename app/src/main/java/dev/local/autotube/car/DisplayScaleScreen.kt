package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import dev.local.autotube.data.DisplayScaleSettings

/** A deliberate, persisted display-size choice — never an automatic zoom adjustment. */
class DisplayScaleScreen(
    carContext: CarContext,
    private val onSelected: () -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val selectedScale = DisplayScaleSettings.get(carContext)
        val list = ItemList.Builder()
        DisplayScaleSettings.options.forEach { option ->
            list.addItem(
                Row.Builder()
                    .setTitle(option.title + if (option.scale == selectedScale) "  ✓" else "")
                    .addText(option.description)
                    .setOnClickListener {
                        DisplayScaleSettings.set(carContext, option.scale)
                        // Return through Menu to PlaybackScreen. Its normal surface
                        // reattachment creates one new, then stable, viewport.
                        screenManager.pop()
                        onSelected()
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("Display size")
            .setHeaderAction(Action.BACK)
            .setSingleList(list.build())
            .build()
    }
}
