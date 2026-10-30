package com.scrollpause.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.scrollpause.app.domain.model.DailyStats

/**
 * Room entity representing one calendar day's aggregated stats.
 *
 * [date] is the primary key stored as "YYYY-MM-DD" in the device's local
 * timezone. Using a text key (not epoch millis) makes date-based queries
 * readable and avoids timezone conversion in SQL.
 */
@Entity(tableName = "daily_stats")
data class DailyStat(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String,

    @ColumnInfo(name = "reel_count")
    val reelCount: Int = 0,

    /** Total Accessibility-detected session time in milliseconds. */
    @ColumnInfo(name = "session_ms")
    val sessionMs: Long = 0L,

    /** 1 if the user's daily target was exceeded, 0 otherwise. */
    @ColumnInfo(name = "target_exceeded")
    val targetExceeded: Int = 0,
) {
    fun toDomain(): DailyStats = DailyStats(
        date = date,
        reelCount = reelCount,
        sessionMs = sessionMs,
        targetExceeded = targetExceeded == 1,
    )
}
