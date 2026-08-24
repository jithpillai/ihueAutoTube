package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

class LocalVideoControlsScreen(
    carContext: CarContext,
    private val onForward: () -> Unit,
    private val onStop: () -> Unit,
    private val progressText: () -> String
) : Screen(carContext) {
    override fun onGetTemplate(): Template = ListTemplate.Builder()
        .setTitle("Video controls")
        .setHeaderAction(Action.BACK)
        .setSingleList(
            ItemList.Builder()
                .addItem(Row.Builder().setTitle("Forward 10 seconds").setOnClickListener { onForward() }.build())
                .addItem(Row.Builder().setTitle("Playback position").addText(progressText()).build())
                .addItem(Row.Builder().setTitle("Stop playback").setOnClickListener { onStop() }.build())
                .build()
        )
        .build()
}
