package com.ihue.dashplayer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.ListTemplate
import androidx.core.graphics.drawable.IconCompat
import com.ihue.dashplayer.R
import com.ihue.dashplayer.data.DashPlayerDatabase
import com.ihue.dashplayer.data.SavedItem
import com.ihue.dashplayer.data.SavedItemType
import kotlinx.coroutines.delay
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
    private var history: List<com.ihue.dashplayer.data.WatchHistory> = emptyList()

    // Shown once, briefly, the first time this session's root screen is created — not on
    // every return to Home (this Screen instance is reused for popToRoot(), so init{} and
    // this flag only ever run/flip once per car session).
    private var showSplash = true

    init {
        lifecycle.addObserver(this)
        lifecycleScope.launch {
            delay(2500)
            showSplash = false
            invalidate()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // Reload every time this screen becomes visible again, not just on first creation —
        // otherwise favorites added/deleted via "Manage favorites" or history recorded during
        // playback wouldn't show up until the app was fully restarted.
        lifecycleScope.launch {
            val dao = DashPlayerDatabase.get(carContext).dao()
            favorites = dao.getSavedItems(SavedItemType.CHANNEL) + dao.getSavedItems(SavedItemType.PLAYLIST)
            history = dao.getRecentHistory(5)
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (showSplash) {
            return MessageTemplate.Builder("Powered by ihue")
                .setTitle("Dash Player")
                .setIcon(carIcon(carContext, R.drawable.app_logo))
                .build()
        }

        val listBuilder = ItemList.Builder()

        // Always-available entry points — kept first so they never move around as
        // favorites/history are added or grow the list below them.
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Browse full YouTube")
                .setImage(carIcon(carContext, R.drawable.ic_play_circle))
                .setOnClickListener {
                    PlaybackScreen.openOrResume(carContext, "https://www.youtube.com")
                }
                .build()
        )
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Open another site / enter URL")
                .setImage(carIcon(carContext, R.drawable.ic_public))
                .setOnClickListener {
                    screenManager.push(BrowserScreen(carContext))
                }
                .build()
        )
        listBuilder.addItem(
            Row.Builder()
                .setTitle("Play videos from phone")
                .addText("Choose a folder in the Dash Player phone app")
                .setImage(carIcon(carContext, R.drawable.ic_folder))
                .setOnClickListener {
                    screenManager.push(LocalVideoLibraryScreen(carContext))
                }
                .build()
        )

        for (fav in favorites) {
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(fav.title)
                    .setImage(carIcon(carContext, R.drawable.ic_star))
                    .setOnClickListener {
                        PlaybackScreen.openFresh(carContext, fav.url)
                    }
                    .build()
            )
        }

        if (history.isNotEmpty()) {
            for (h in history) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Continue: ${h.title}")
                        .addText("Resume at ${h.lastPositionSeconds / 60}:${(h.lastPositionSeconds % 60).toString().padStart(2, '0')}")
                        .setImage(carIcon(carContext, R.drawable.ic_history))
                        .setOnClickListener {
                            PlaybackScreen.openFresh(
                                carContext,
                                "https://www.youtube.com/watch?v=${h.videoId}&t=${h.lastPositionSeconds}s"
                            )
                        }
                        .build()
                )
            }
        }

        listBuilder.addItem(
            Row.Builder()
                .setTitle("Powered by ihue")
                .setImage(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ihue_logo_white)).build())
                .build()
        )

        return ListTemplate.Builder()
            .setTitle("Dash Player")
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
