package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import dev.local.autotube.data.AutoTubeDatabase
import dev.local.autotube.data.SavedItem
import kotlinx.coroutines.launch

/** Open or delete a single favorite. Returns true via setResult() if it was deleted. */
class FavoriteDetailScreen(carContext: CarContext, private val item: SavedItem) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("Open")
                    .addText(item.url)
                    .setOnClickListener {
                        PlaybackScreen.openFresh(carContext, item.url)
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("Delete")
                    .setOnClickListener {
                        lifecycleScope.launch {
                            AutoTubeDatabase.get(carContext).dao().deleteSavedItem(item.id)
                            setResult(true)
                            finish()
                        }
                    }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle(item.title)
            .setHeaderAction(Action.BACK)
            .setSingleList(list)
            .build()
    }
}
