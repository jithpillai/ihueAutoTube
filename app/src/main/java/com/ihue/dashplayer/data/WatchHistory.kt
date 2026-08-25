package com.ihue.dashplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val videoId: String,
    val title: String,
    val thumbnailUrl: String?,
    val lastPositionSeconds: Int = 0,
    val watchedAt: Long = System.currentTimeMillis()
)
