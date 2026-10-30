package com.scrollpause.app.service.session

import android.os.SystemClock
import com.scrollpause.app.data.repository.ReelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tracks time spent in the Reels viewer using a monotonic clock.
 *
 * Why monotonic ([SystemClock.elapsedRealtime])?
 *   Wall-clock time ([System.currentTimeMillis]) can jump backward or forward
 *   due to NTP sync, timezone changes, or daylight saving adjustments.
 *   [elapsedRealtime] only increases and is immune to these changes,
 *   making it reliable for elapsed duration calculation.
 *
 * Midnight boundary:
 *   When [start] is called, we record the current calendar date.
 *   When [stop] is called, we use the start-recorded date for persistence,
 *   even if midnight has passed. This keeps a session's time attributed to
 *   the day it started rather than splitting it across midnight.
 *   (Edge case: very long overnight sessions — acceptable for MVP.)
 *
 * Process death recovery:
 *   The [onCheckpoint] callback is invoked with the current elapsed ms so the
 *   caller (the AccessibilityService) can write it to DataStore.
 *   On restart, the Service reads the checkpoint, writes it to Room, and resets.
 */
class SessionTimer(
    private val repository: ReelRepository,
    private val scope: CoroutineScope,
    private val onCheckpoint: (elapsedMs: Long, date: String) -> Unit = { _, _ -> },
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private var sessionStartElapsed: Long = -1L
    private var sessionStartEpoch: Long = -1L
    private var sessionDate: String = ""

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** Accumulated ms for today (in-memory fast path). */
    private val _todaySessionMs = MutableStateFlow(0L)
    val todaySessionMs: StateFlow<Long> = _todaySessionMs.asStateFlow()

    fun initializeTodayMs(persistedMs: Long) {
        _todaySessionMs.value = persistedMs
    }

    /**
     * Start a new session segment. No-op if already running.
     */
    fun start() {
        if (_isRunning.value) return
        sessionStartElapsed = SystemClock.elapsedRealtime()
        sessionStartEpoch = System.currentTimeMillis()
        sessionDate = LocalDate.now().format(dateFormatter)
        _isRunning.value = true
    }

    /**
     * Stop the current segment and persist its duration.
     * No-op if not running.
     */
    fun stop() {
        if (!_isRunning.value) return
        val durationMs = SystemClock.elapsedRealtime() - sessionStartElapsed
        _isRunning.value = false

        if (durationMs > MIN_TRACKABLE_SEGMENT_MS) {
            _todaySessionMs.value += durationMs
            scope.launch {
                repository.recordSessionSegment(
                    date = sessionDate,
                    startEpochMs = sessionStartEpoch,
                    durationMs = durationMs,
                )
            }
        }

        sessionStartElapsed = -1L
        sessionStartEpoch = -1L
        sessionDate = ""
    }

    /**
     * Get the current segment's elapsed ms without stopping.
     * Used for checkpoint writes.
     */
    fun currentSegmentElapsedMs(): Long {
        if (!_isRunning.value) return 0L
        return SystemClock.elapsedRealtime() - sessionStartElapsed
    }

    /**
     * Emit a checkpoint so the Service can persist partial progress to DataStore.
     * Called periodically (e.g., every 30s) by the Service's coroutine.
     */
    fun checkpoint() {
        if (_isRunning.value && sessionDate.isNotEmpty()) {
            val elapsed = currentSegmentElapsedMs()
            onCheckpoint(elapsed, sessionDate)
        }
    }

    /** Reset in-memory state for a new day. */
    fun resetForNewDay() {
        stop()
        _todaySessionMs.value = 0L
    }

    companion object {
        /** Segments shorter than this are noise (e.g., brief touch events). */
        private const val MIN_TRACKABLE_SEGMENT_MS = 1_000L
    }
}
