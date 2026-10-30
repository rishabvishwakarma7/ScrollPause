package com.scrollpause.app.service.counter

import com.scrollpause.app.data.repository.ReelRepository
import com.scrollpause.app.service.detector.DetectionState
import com.scrollpause.app.service.detector.ReelDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Consumes confirmed Reel transition signals from [ReelDetector] and persists
 * them via [ReelRepository].
 *
 * Deduplication strategy:
 *   - [checkPendingTransition] is polled every 500ms from a coroutine.
 *   - The detector confirms transitions only after [SCROLL_CONFIRMATION_DELAY_MS] stability.
 *   - [lastConfirmedTransitionMs] prevents double-counting if the poll fires
 *     twice before the detector clears the signal.
 *   - [todayCount] is kept in-memory as a fast path for the overlay counter
 *     (avoids a Room read on every update).
 */
class ReelCounter(
    private val detector: ReelDetector,
    private val repository: ReelRepository,
    private val scope: CoroutineScope,
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private val _todayCount = MutableStateFlow(0)
    val todayCount: StateFlow<Int> = _todayCount.asStateFlow()

    private var lastConfirmedTransitionMs: Long = -1L
    private var pollJob: Job? = null

    /** Seed from persisted data on service start to avoid losing counts from earlier today. */
    fun initialize(persistedCount: Int) {
        _todayCount.value = persistedCount
    }

    /** Start polling the detector for confirmed transitions. */
    fun start() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (detector.state.value == DetectionState.REELS_VIEWER) {
                    val nowMs = System.currentTimeMillis()
                    detector.checkPendingTransition(nowMs)
                    if (detector.consumePendingTransition()) {
                        recordExposure()
                    }
                }
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun resetForNewDay() {
        _todayCount.value = 0
        lastConfirmedTransitionMs = -1L
    }

    private suspend fun recordExposure() {
        val nowMs = System.currentTimeMillis()
        // Guard against double-fire from overlapping polls
        if (nowMs - lastConfirmedTransitionMs < MIN_INTERVAL_BETWEEN_COUNTS_MS) return
        lastConfirmedTransitionMs = nowMs

        val date = LocalDate.now().format(dateFormatter)
        repository.recordReelExposure(date)
        _todayCount.value++
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
        /** Two counts within 300ms of each other are treated as the same event. */
        private const val MIN_INTERVAL_BETWEEN_COUNTS_MS = 300L
    }
}
