package com.scrollpause.app.domain.model

/**
 * Domain model for a single day's aggregated statistics.
 * This is what the UI consumes — decoupled from the Room entity.
 */
data class DailyStats(
    /** ISO-8601 date string "YYYY-MM-DD" in the device's local timezone. */
    val date: String,
    /** Number of confirmed Reel exposures recorded this day. */
    val reelCount: Int,
    /** Total tracked time in the Reels viewer (milliseconds). */
    val sessionMs: Long,
    /** Whether the user's daily target was exceeded. */
    val targetExceeded: Boolean,
) {
    /** Session time formatted as "X min" or "Xh Ym" for display. */
    val sessionFormatted: String
        get() {
            val totalSeconds = sessionMs / 1000
            val minutes = totalSeconds / 60
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return when {
                hours > 0 -> "${hours}h ${remainingMins}m"
                minutes > 0 -> "${minutes} min"
                else -> "< 1 min"
            }
        }
}
