package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.ListTemplate
import dev.local.autotube.data.AutoTubeDatabase
import dev.local.autotube.data.SavedItem
import dev.local.autotube.data.SavedItemType
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope

/**
 * Note on template choice: this screen is a plain chooser (favorites list + "Browse" entry
 * point), so it uses ListTemplate — no Surface needed here. The Surface/WebView bridge only
 * comes into play in PlaybackScreen, once the user has actually picked something to open.
 */
class HomeScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private var favorites: List<SavedItem> = emptyList()
    private var history: List<dev.local.autotube.data.WatchHistory> = emptyList()

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        // Reload every time this screen becomes visible again, not just on first creation —
        // otherwise favorites added/deleted via "Manage favorites" or history recorded during
        // playback wouldn't show up until the app was fully restarted.
        lifecycleScope.launch {
            val dao = AutoTubeDatabase.get(carContext).dao()
            favorites = dao.getSavedItems(SavedItemType.CHANNEL) + dao.getSavedItems(SavedItemType.PLAYLIST)
            history = dao.getRecentHistory(5)
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (history.isNotEmpty()) {
            for (h in history) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Continue: ${h.title}")
                        .addText("Resume at ${h.lastPositionSeconds / 60}:${(h.lastPositionSeconds % 60).toString().padStart(2, '0')}")
                        .setOnClickListener {
                            screenManager.push(
                                PlaybackScreen(carContext, "https://www.youtube.com/watch?v=${h.videoId}&t=${h.lastPositionSeconds}s")
                            )
                        }
                        .build()
                )
            }
        }

        for (fav in favorites) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(fav.title)
                    .setOnClickListener {
                        screenManager.push(PlaybackScreen(carContext, fav.url))
                    }
                    .build()
            )
        }

        // Always-available entry points
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Browse full YouTube")
                .setOnClickListener {
                    screenManager.push(PlaybackScreen(carContext, "https://www.youtube.com"))
                }
                .build()
        )
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Open another site / enter URL")
                .setOnClickListener {
                    screenManager.push(BrowserScreen(carContext))
                }
                .build()
        )

        return ListTemplate.Builder()
            .setTitle("AutoTube")
            .setSingleList(listBuilder.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Manage favorites")
                            .setOnClickListener {
                                screenManager.push(FavoritesEditScreen(carContext))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
