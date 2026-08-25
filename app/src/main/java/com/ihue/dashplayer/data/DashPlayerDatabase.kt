package com.ihue.dashplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedItem::class, WatchHistory::class, SearchHistory::class],
    version = 2,
    exportSchema = false
)
abstract class DashPlayerDatabase : RoomDatabase() {
    abstract fun dao(): DashPlayerDao

    companion object {
        @Volatile private var INSTANCE: DashPlayerDatabase? = null

        fun get(context: Context): DashPlayerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DashPlayerDatabase::class.java,
                    "dashplayer.db"
                )
                    // No real migrations exist yet for this personal-use app; wiping local
                    // favorites/history on a schema bump beats crashing on open.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
