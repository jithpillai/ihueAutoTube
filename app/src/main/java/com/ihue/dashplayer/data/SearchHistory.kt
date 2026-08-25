package com.ihue.dashplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** query is the primary key so re-searching the same text bumps it to the top instead of
 *  creating a duplicate row. */
@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
