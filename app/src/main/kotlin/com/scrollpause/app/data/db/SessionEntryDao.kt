package com.scrollpause.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SessionEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SessionEntry): Long

    @Query("SELECT * FROM session_entries WHERE date = :date ORDER BY start_epoch_ms ASC")
    suspend fun getForDate(date: String): List<SessionEntry>

    /** Delete all session entries — used by "Delete history". */
    @Query("DELETE FROM session_entries")
    suspend fun deleteAll()

    /** Remove entries older than [cutoffDate] ("YYYY-MM-DD") to avoid unbounded growth. */
    @Query("DELETE FROM session_entries WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
