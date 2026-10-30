package com.scrollpause.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStatDao {

    /** Observe today's stat as a Flow — emits null if no row exists yet. */
    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyStat?>

    /** One-shot fetch of a single date (used for recovery on startup). */
    @Query("SELECT * FROM daily_stats WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyStat?

    /** Fetch the most recent [limit] days with data, newest first. */
    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 30): List<DailyStat>

    /** Observe the 7 most recent days for the home screen chart. */
    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT 7")
    fun observeLastSevenDays(): Flow<List<DailyStat>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: DailyStat)

    /**
     * Atomically increment reel count for [date] by [delta].
     * Inserts a new row if one does not exist.
     *
     * Using a transaction prevents lost increments if two increments
     * arrive concurrently (e.g., very fast swipes).
     */
    @Transaction
    suspend fun incrementReelCount(date: String, delta: Int = 1) {
        val existing = getByDate(date)
        val updated = existing?.copy(reelCount = existing.reelCount + delta)
            ?: DailyStat(date = date, reelCount = delta)
        upsert(updated)
    }

    /**
     * Atomically add [deltaMs] to the session time for [date].
     */
    @Transaction
    suspend fun addSessionTime(date: String, deltaMs: Long) {
        val existing = getByDate(date)
        val updated = existing?.copy(sessionMs = existing.sessionMs + deltaMs)
            ?: DailyStat(date = date, sessionMs = deltaMs)
        upsert(updated)
    }

    /**
     * Mark [date] as having exceeded the daily target.
     */
    @Transaction
    suspend fun markTargetExceeded(date: String) {
        val existing = getByDate(date)
        val updated = existing?.copy(targetExceeded = 1)
            ?: DailyStat(date = date, targetExceeded = 1)
        upsert(updated)
    }

    /** Delete all rows — used by "Delete history" in Settings. */
    @Query("DELETE FROM daily_stats")
    suspend fun deleteAll()
}
