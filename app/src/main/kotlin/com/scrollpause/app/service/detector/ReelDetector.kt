package com.scrollpause.app.service.detector

import com.scrollpause.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detection state machine for Instagram Reels.
 *
 * IMPORTANT — Real-device validation required:
 * Instagram does not expose a stable public API for screen identification.
 * The heuristics below are based on observable AccessibilityService metadata.
 * They MUST be validated with a real device running the current Instagram
 * version using the DiagnosticsScreen in a debug build.
 *
 * The detector is intentionally conservative:
 *   - It transitions to REELS_VIEWER only when confident.
 *   - It does not fabricate a REELS_VIEWER state when signals are ambiguous.
 *   - Callers (ReelCounter) must observe [state]; they do not call into this
 *     class directly for counting decisions.
 *
 * Heuristics used (subject to real-device update):
 *
 *  1. Window class name contains known Reels activity indicators.
 *     Instagram has shipped activities whose class names include "Reels" or
 *     "ClipsViewer" in some versions. These change with app updates.
 *     The [REELS_WINDOW_CLASSES] set is intentionally a starting list —
 *     update it after running DiagnosticsScreen.
 *
 *  2. TYPE_VIEW_SCROLLED events on a full-screen vertical pager class.
 *     The Reels viewer uses a ViewPager2 or similar. Scroll events from
 *     the home feed use a different container class.
 *
 *  3. Window overlay count increase → OBSCURED.
 *     When the comments sheet or share sheet opens, additional windows appear.
 *
 * Scroll-based transition detection:
 *   A scroll event alone is not sufficient evidence of a new Reel because:
 *   - Videos seek (partial scroll without navigation).
 *   - Scroll events repeat continuously during a swipe gesture.
 *   The detector records a candidate transition and confirms it only after
 *   a stability period ([SCROLL_CONFIRMATION_DELAY_MS]) without further events.
 *   The counter reads [pendingReelTransition] and acts on it.
 */
class ReelDetector {

    private val _state = MutableStateFlow(DetectionState.OUTSIDE_APP)
    val state: StateFlow<DetectionState> = _state.asStateFlow()

    /** Set to true when a scroll transition is candidate but not yet confirmed. */
    private var pendingScrollTimestamp: Long = -1L
    private var lastScrollIndex: Int = -1

    /**
     * Emitted as a one-shot signal when the detector is confident a new Reel
     * was shown. The [ReelCounter] observes this to increment.
     * -1 = no pending transition.
     */
    var pendingReelTransitionAt: Long = -1L
        private set

    /**
     * Accessibility event type constants.
     *
     * We define these locally instead of referencing AccessibilityEvent.TYPE_*
     * so that the detector is fully testable in a pure JVM environment
     * (AccessibilityEvent is an Android stub that throws in unit tests).
     * The integer values match the Android framework constants exactly.
     */
    companion object {
        const val EVENT_WINDOW_STATE_CHANGED = 32    // AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        const val EVENT_VIEW_SCROLLED = 4096          // AccessibilityEvent.TYPE_VIEW_SCROLLED
        const val EVENT_WINDOWS_CHANGED = 2048        // AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    /** Reels viewer activity class name fragments (case-insensitive contains check). */
    private val REELS_WINDOW_CLASSES = setOf(
        "reels",
        "clipsviewer",
        "reelviewer",
        "reelsfeed",
        "clips_viewer",
        "reels_viewer",
    )

    /** Comment / share sheet window class fragments — used to detect OBSCURED. */
    private val OVERLAY_WINDOW_CLASSES = setOf(
        "comment",
        "share",
        "bottom_sheet",
        "dialog",
        "menu",
    )

    /**
     * Minimum ms of stability after last scroll event before a transition is confirmed.
     * Prevents counting every individual scroll tick as a new Reel.
     */
    private val SCROLL_CONFIRMATION_DELAY_MS = 400L

    /**
     * Process a [DetectionEvent] and update the state machine.
     *
     * Called from a background coroutine in the AccessibilityService.
     * This function is synchronous — all state transitions happen here.
     */
    fun process(event: DetectionEvent) {
        logDebug("process(type=${event.eventType}, pkg=${event.packageName}, " +
                "winClass=${event.windowClassName}, srcClass=${event.sourceClassName})")

        when (event.eventType) {
            EVENT_WINDOW_STATE_CHANGED -> handleWindowStateChanged(event)
            EVENT_VIEW_SCROLLED -> handleViewScrolled(event)
            EVENT_WINDOWS_CHANGED -> handleWindowsChanged(event)
        }
    }

    /**
     * Called by [ReelCounter] every 500ms to check if a pending scroll
     * transition has been stable long enough to confirm.
     */
    fun checkPendingTransition(nowMs: Long) {
        if (pendingScrollTimestamp > 0 &&
            (nowMs - pendingScrollTimestamp) >= SCROLL_CONFIRMATION_DELAY_MS &&
            _state.value == DetectionState.REELS_VIEWER
        ) {
            pendingReelTransitionAt = nowMs
            pendingScrollTimestamp = -1L
            logDebug("Confirmed Reel transition at $nowMs")
        }
    }

    /** Consume the pending transition signal. Returns true if one was pending. */
    fun consumePendingTransition(): Boolean {
        return if (pendingReelTransitionAt > 0L) {
            pendingReelTransitionAt = -1L
            true
        } else {
            false
        }
    }

    /** Reset to OUTSIDE_APP — called when tracking is paused or permission revoked. */
    fun reset() {
        _state.value = DetectionState.OUTSIDE_APP
        pendingScrollTimestamp = -1L
        pendingReelTransitionAt = -1L
        lastScrollIndex = -1
    }

    // ── Private handlers ──────────────────────────────────────────────────────

    private fun handleWindowStateChanged(event: DetectionEvent) {
        val pkg = event.packageName ?: return
        val winClass = event.windowClassName?.lowercase() ?: ""

        if (!pkg.contains("instagram")) {
            transitionTo(DetectionState.OUTSIDE_APP)
            return
        }

        // Within Instagram — determine which screen
        val isReelsScreen = REELS_WINDOW_CLASSES.any { winClass.contains(it) }

        if (isReelsScreen) {
            if (_state.value != DetectionState.REELS_VIEWER) {
                transitionTo(DetectionState.REELS_VIEWER)
                // Count the initial entry into the Reels viewer
                pendingReelTransitionAt = event.receivedAtMs
            }
        } else if (_state.value == DetectionState.REELS_VIEWER) {
            // We were in Reels but now see a different Activity
            transitionTo(DetectionState.IN_APP_UNKNOWN)
        } else {
            transitionTo(DetectionState.IN_APP_UNKNOWN)
        }
    }

    private fun handleViewScrolled(event: DetectionEvent) {
        if (_state.value != DetectionState.REELS_VIEWER) return
        if (event.packageName?.contains("instagram") != true) return

        val newIndex = event.scrollToIndex
        // Only consider vertical pager scrolls with a meaningful index change
        if (newIndex >= 0 && newIndex != lastScrollIndex) {
            lastScrollIndex = newIndex
            // Record as candidate transition — confirmed after stability period
            pendingScrollTimestamp = event.receivedAtMs
            logDebug("Scroll candidate at index $newIndex, timestamp=${event.receivedAtMs}")
        }
    }

    private fun handleWindowsChanged(event: DetectionEvent) {
        if (_state.value == DetectionState.OUTSIDE_APP) return
        if (event.packageName?.contains("instagram") != true) return

        val sourceClass = event.sourceClassName?.lowercase() ?: ""
        val isOverlay = OVERLAY_WINDOW_CLASSES.any { sourceClass.contains(it) }

        if (isOverlay && _state.value == DetectionState.REELS_VIEWER) {
            transitionTo(DetectionState.OBSCURED)
        } else if (!isOverlay && _state.value == DetectionState.OBSCURED) {
            transitionTo(DetectionState.REELS_VIEWER)
        }
    }

    private fun transitionTo(newState: DetectionState) {
        val old = _state.value
        if (old != newState) {
            logDebug("State: $old → $newState")
            _state.value = newState
            if (newState != DetectionState.REELS_VIEWER) {
                // Cancel any pending scroll transition when leaving the viewer
                pendingScrollTimestamp = -1L
            }
        }
    }

    private fun logDebug(msg: String) {
        if (BuildConfig.DEBUG) {
            // Use println instead of android.util.Log for JVM test compatibility.
            // In production, this could be replaced with a proper logging framework.
            println("ScrollPause/Detector: $msg")
        }
    }
}
