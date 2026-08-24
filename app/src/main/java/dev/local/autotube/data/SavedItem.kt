package dev.local.autotube.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SavedItemType { CHANNEL, PLAYLIST, SITE }

@Entity(tableName = "saved_items")
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: SavedItemType,
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val lastOpenedAt: Long = 0L
)
