package com.scrollpause.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single contiguous Reels viewing session segment.
 *
 * Segments are used to:
 *   1. Recover partial session data on process death.
 *   2. Accumulate multiple viewing segments within one day.
 *
 * Once a session ends normally, its [durationMs] is also added to
 * [DailyStat.sessionMs] for quick aggregation.
 */
@Entity(tableName = "session_entries")
data class SessionEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "date")
    val date: String,

    /** Wall-clock epoch millis of session start (for record-keeping / display). */
    @ColumnInfo(name = "start_epoch_ms")
    val startEpochMs: Long,

    /** Elapsed duration in milliseconds (monotonic-clock based). */
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
)
