package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import dev.local.autotube.data.AutoTubeDatabase
import dev.local.autotube.data.SavedItem
import kotlinx.coroutines.launch

/**
 * Single-step add: paste a URL, title is derived from it automatically (rename isn't
 * supported yet — re-add to fix a bad title). Deliberately NOT a two-step "URL then title"
 * flow: swapping in a second SearchTemplate with a different SearchCallback via invalidate()
 * doesn't rebind cleanly on this host — it keeps dispatching to the first template's callback
 * and appending to its old text instead of using the new one. One SearchTemplate for the
 * whole screen's lifetime sidesteps that entirely.
 *
 * Returns true via setResult() so the caller (FavoritesEditScreen) knows to reload its list.
 */
class AddFavoriteScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}
            override fun onSearchSubmitted(searchText: String) {
                if (searchText.isBlank()) return
                val url = UrlUtils.normalizeUrl(searchText)
                lifecycleScope.launch {
                    AutoTubeDatabase.get(carContext).dao().upsertSavedItem(
                        SavedItem(
                            type = UrlUtils.guessType(url),
                            title = UrlUtils.guessTitle(url),
                            url = url,
                            thumbnailUrl = null,
                            lastOpenedAt = System.currentTimeMillis()
                        )
                    )
                    setResult(true)
                    finish()
                }
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint("Paste a channel, playlist, or site URL")
            .setShowKeyboardByDefault(true)
            .setItemList(ItemList.Builder().build())
            .build()
    }
}
