package com.ihue.dashplayer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.ListTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ihue.dashplayer.data.DashPlayerDatabase
import com.ihue.dashplayer.data.SavedItem
import com.ihue.dashplayer.data.SavedItemType
import kotlinx.coroutines.launch

class FavoritesEditScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private var items: List<SavedItem> = emptyList()

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // Reload every time this screen becomes visible again — covers returning from
        // AddFavoriteScreen/FavoriteDetailScreen where the underlying data just changed.
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val dao = DashPlayerDatabase.get(carContext).dao()
            items = dao.getSavedItems(SavedItemType.CHANNEL) +
                dao.getSavedItems(SavedItemType.PLAYLIST) +
                dao.getSavedItems(SavedItemType.SITE)
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        if (items.isEmpty()) {
            list.setNoItemsMessage("No favorites yet — tap Add to save a channel, playlist, or site.")
        }
        for (item in items) {
            list.addItem(
                Row.Builder()
                    .setTitle(item.title)
                    .addText(item.url)
                    .setOnClickListener {
                        screenManager.pushForResult(FavoriteDetailScreen(carContext, item)) { result ->
                            if (result == true) reload()
                        }
                    }
                    .build()
            )
        }
        return ListTemplate.Builder()
            .setTitle("Favorites")
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Add")
                            .setOnClickListener {
                                screenManager.pushForResult(AddFavoriteScreen(carContext)) { result ->
                                    if (result == true) reload()
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .setSingleList(list.build())
            .build()
    }
}
