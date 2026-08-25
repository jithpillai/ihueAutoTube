package com.ihue.dashplayer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DashPlayerDao {

    @Query("SELECT * FROM saved_items WHERE type = :type ORDER BY lastOpenedAt DESC")
    suspend fun getSavedItems(type: SavedItemType): List<SavedItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedItem(item: SavedItem)

    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun deleteSavedItem(id: Long)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 10): List<WatchHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: WatchHistory)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    suspend fun getRecentSearches(limit: Int = 10): List<SearchHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(entry: SearchHistory)
}
