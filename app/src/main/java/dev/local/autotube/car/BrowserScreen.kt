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
import kotlinx.coroutines.launch

/**
 * SearchTemplate is the Car App Library's text-input template — it brings up the
 * platform's real on-screen keyboard (your call: full typing, no voice-to-text needed,
 * since the car is parked whenever this screen is reachable).
 */
class BrowserScreen(carContext: CarContext) : Screen(carContext) {

    private var currentQuery: String = ""
    private var knownSites: List<SavedItem> = emptyList()

    init {
        lifecycleScope.launch {
            knownSites = AutoTubeDatabase.get(carContext).dao().getSavedItems(SavedItemType.SITE)
            invalidate()
        }
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
                    .setOnClickListener {
                        val url = UrlUtils.normalizeUrl(currentQuery)
                        screenManager.push(PlaybackScreen(carContext, url))
                    }
                    .build()
            )
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
                        screenManager.push(PlaybackScreen(carContext, site.url))
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
                screenManager.push(PlaybackScreen(carContext, UrlUtils.normalizeUrl(searchText)))
            }
        })
            .setHeaderAction(androidx.car.app.model.Action.BACK)
            .setSearchHint("Type a URL or site name")
            .setShowKeyboardByDefault(true)
            .setItemList(resultsList.build())
            .build()
    }
}
