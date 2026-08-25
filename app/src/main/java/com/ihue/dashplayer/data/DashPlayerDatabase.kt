package dev.local.autotube.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedItem::class, WatchHistory::class, SearchHistory::class],
    version = 2,
    exportSchema = false
)
abstract class AutoTubeDatabase : RoomDatabase() {
    abstract fun dao(): AutoTubeDao

    companion object {
        @Volatile private var INSTANCE: AutoTubeDatabase? = null

        fun get(context: Context): AutoTubeDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutoTubeDatabase::class.java,
                    "autotube.db"
                )
                    // No real migrations exist yet for this personal-use app; wiping local
                    // favorites/history on a schema bump beats crashing on open.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
