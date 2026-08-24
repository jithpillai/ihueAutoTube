package dev.local.autotube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.model.SearchTemplate
import androidx.lifecycle.lifecycleScope
import dev.local.autotube.data.AutoTubeDatabase
import dev.local.autotube.data.SavedItem
import dev.local.autotube.data.SavedItemType
import dev.local.autotube.data.SearchHistory
import kotlinx.coroutines.launch

/**
 * SearchTemplate is the Car App Library's text-input template — it brings up the
 * platform's real on-screen keyboard (your call: full typing, no voice-to-text needed,
 * since the car is parked whenever this screen is reachable).
 */
class BrowserScreen(carContext: CarContext) : Screen(carContext) {

    private var currentQuery: String = ""
    private var knownSites: List<SavedItem> = emptyList()
    private var recentSearches: List<SearchHistory> = emptyList()

    init {
        lifecycleScope.launch {
            val dao = AutoTubeDatabase.get(carContext).dao()
            knownSites = dao.getSavedItems(SavedItemType.SITE)
            recentSearches = dao.getRecentSearches()
            invalidate()
        }
    }

    /** Shared by both "go to what I typed" and tapping a recent-search/site suggestion —
     *  records the query so it shows up in recent searches next time, then navigates. */
    private fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        lifecycleScope.launch {
            AutoTubeDatabase.get(carContext).dao()
                .upsertSearch(SearchHistory(trimmed, System.currentTimeMillis()))
        }
        PlaybackScreen.openFresh(carContext, UrlUtils.normalizeUrl(trimmed))
    }

    override fun onGetTemplate(): Template {
        // ItemList.Builder in this Car App Library version only supports appending
        // (addItem(Item), no index overload), so assemble rows in display order first.
        val rows = mutableListOf<Row>()

        // Always offer "go to exactly what I typed" as the top-level action
        if (currentQuery.isNotBlank()) {
            rows.add(
                Row.Builder()
                    .setTitle("Go to \"$currentQuery\"")
                    .setOnClickListener { submitSearch(currentQuery) }
                    .build()
            )
        }

        // Recent searches only make sense as a suggestion before the user starts typing —
        // once there's a query, the "go to" row + filtered sites above are more relevant.
        if (currentQuery.isBlank()) {
            for (entry in recentSearches) {
                rows.add(
                    Row.Builder()
                        .setTitle(entry.query)
                        .addText("Recent search")
                        .setOnClickListener { submitSearch(entry.query) }
                        .build()
                )
            }
        }

        // Saved site shortcuts, filtered live as the user types
        val filtered = knownSites.filter {
            currentQuery.isBlank() || it.title.contains(currentQuery, ignoreCase = true)
        }
        for (site in filtered) {
            rows.add(
                Row.Builder()
                    .setTitle(site.title)
                    .addText(site.url)
                    .setOnClickListener {
                        PlaybackScreen.openFresh(carContext, site.url)
                    }
                    .build()
            )
        }

        val resultsList = ItemList.Builder()
        rows.forEach { resultsList.addItem(it) }

        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) {
                currentQuery = searchText
                invalidate()
            }

            override fun onSearchSubmitted(searchText: String) {
                currentQuery = searchText
                submitSearch(searchText)
            }
        })
            .setHeaderAction(androidx.car.app.model.Action.BACK)
            .setSearchHint("Type a URL or site name")
            .setShowKeyboardByDefault(true)
            .setItemList(resultsList.build())
            .build()
    }
}
