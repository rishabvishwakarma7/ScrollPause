package com.scrollpause.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Single Room database for ScrollPause.
 *
 * Version history:
 *   1 — Initial schema (daily_stats, session_entries)
 *
 * Migrations: destructive migration is used for the MVP because there is no
 * production data to protect. Before a Play Store release, proper migrations
 * should be added.
 */
@Database(
    entities = [DailyStat::class, SessionEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dailyStatDao(): DailyStatDao
    abstract fun sessionEntryDao(): SessionEntryDao

    companion object {
        private const val DATABASE_NAME = "scrollpause.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
