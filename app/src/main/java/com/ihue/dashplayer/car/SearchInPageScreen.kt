package com.ihue.dashplayer.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.ihue.dashplayer.R
import com.ihue.dashplayer.data.DashPlayerDatabase
import com.ihue.dashplayer.data.SearchHistory
import kotlinx.coroutines.launch

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

    private var currentQuery: String = ""
    private var recentSearches: List<SearchHistory> = emptyList()

    init {
        lifecycleScope.launch {
            recentSearches = DashPlayerDatabase.get(carContext).dao().getRecentSearches()
            invalidate()
        }
    }

    private fun submit(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            lifecycleScope.launch {
                DashPlayerDatabase.get(carContext).dao()
                    .upsertSearch(SearchHistory(trimmed, System.currentTimeMillis()))
            }
        }
        onSubmit(query)
        screenManager.pop()
    }

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        val filtered = recentSearches.filter {
            currentQuery.isBlank() || it.query.contains(currentQuery, ignoreCase = true)
        }
        for (entry in filtered) {
            list.addItem(
                Row.Builder()
                    .setTitle(entry.query)
                    .addText("Recent search")
                    .setImage(carIcon(carContext, R.drawable.ic_history))
                    .setOnClickListener { submit(entry.query) }
                    .build()
            )
        }

        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                currentQuery = searchText
                invalidate()
            }

            override fun onSearchSubmitted(searchText: String) {
                submit(searchText)
            }
        })
            .setHeaderAction(Action.BACK)
            .setSearchHint(hint)
            .setShowKeyboardByDefault(true)
            .setItemList(list.build())
            .build()
    }
}
