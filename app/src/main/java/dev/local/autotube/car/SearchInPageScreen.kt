package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template

/**
 * Generic "type or speak a query" screen, reusing the host's real keyboard/voice input
 * (SearchTemplate) the same way BrowserScreen does. Exists because PlaybackScreen's
 * WebView is a raw Surface — a bitmap feed with no attached window — so it can never
 * host a system keyboard directly; there's no window for the IME to anchor to. This
 * screen collects the query the normal (host-rendered) way, then hands it back via
 * [onSubmit] and pops itself off the stack, returning to whatever pushed it.
 */
class SearchInPageScreen(
    carContext: CarContext,
    private val hint: String,
    private val onSubmit: (String) -> Unit
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {}

            override fun onSearchSubmitted(searchText: String) {
                onSubmit(searchText)
                screenManager.pop()
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(hint)
            .setShowKeyboardByDefault(true)
            .setItemList(ItemList.Builder().build())
            .build()
    }
}
