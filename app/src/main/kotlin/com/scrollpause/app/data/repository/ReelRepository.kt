package com.scrollpause.app.data.repository

import com.scrollpause.app.data.db.DailyStatDao
import com.scrollpause.app.data.db.SessionEntry
import com.scrollpause.app.data.db.SessionEntryDao
import com.scrollpause.app.domain.model.DailyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Central repository for Reel count and session data.
 *
 * The AccessibilityService writes through this repository.
 * ViewModels read from this repository.
 */
class ReelRepository(
    private val dailyStatDao: DailyStatDao,
    private val sessionEntryDao: SessionEntryDao,
) {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun today(): String = LocalDate.now().format(dateFormatter)

    // ── Observation ──────────────────────────────────────────────────────────

    /** Live today's stats; emits null until the first Reel is counted. */
    fun observeToday(): Flow<DailyStats?> =
        dailyStatDao.observeByDate(today()).map { it?.toDomain() }

    /** Live 7-day history for the home screen bar chart. */
    fun observeLastSevenDays(): Flow<List<DailyStats>> =
        dailyStatDao.observeLastSevenDays().map { list -> list.map { it.toDomain() } }

    // ── One-shot reads ────────────────────────────────────────────────────────

    suspend fun getToday(): DailyStats? = dailyStatDao.getByDate(today())?.toDomain()

    suspend fun getHistory(limit: Int = 30): List<DailyStats> =
        dailyStatDao.getRecent(limit).map { it.toDomain() }

    // ── Writes ────────────────────────────────────────────────────────────────

    /**
     * Record one confirmed Reel exposure.
     * Called from the AccessibilityService on a background coroutine.
     */
    suspend fun recordReelExposure(date: String = today()) {
        dailyStatDao.incrementReelCount(date)
    }

    /**
     * Add a completed session segment duration.
     * Called when the state machine leaves REELS_VIEWER or the screen locks.
     */
    suspend fun recordSessionSegment(
        date: String = today(),
        startEpochMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0L) return
        dailyStatDao.addSessionTime(date, durationMs)
        sessionEntryDao.insert(
            SessionEntry(
                date = date,
                startEpochMs = startEpochMs,
                durationMs = durationMs,
            )
        )
        // Keep session entries bounded to the last 90 days
        val cutoff = LocalDate.now().minusDays(90).format(dateFormatter)
        sessionEntryDao.deleteOlderThan(cutoff)
    }

    suspend fun markTargetExceeded(date: String = today()) {
        dailyStatDao.markTargetExceeded(date)
    }

    /** Wipe all data. Triggered by "Delete history" in Settings. */
    suspend fun deleteAllHistory() {
        dailyStatDao.deleteAll()
        sessionEntryDao.deleteAll()
    }
}
