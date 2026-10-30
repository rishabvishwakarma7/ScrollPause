package com.scrollpause.app

import com.scrollpause.app.service.detector.DetectionEvent
import com.scrollpause.app.service.detector.DetectionState
import com.scrollpause.app.service.detector.ReelDetector
import com.scrollpause.app.service.detector.ReelDetector.Companion.EVENT_VIEW_SCROLLED
import com.scrollpause.app.service.detector.ReelDetector.Companion.EVENT_WINDOW_STATE_CHANGED
import com.scrollpause.app.service.detector.ReelDetector.Companion.EVENT_WINDOWS_CHANGED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [ReelDetector].
 *
 * These tests use synthetic [DetectionEvent] objects to verify state machine
 * transitions. They do NOT prove Instagram compatibility — only that the logic
 * behaves correctly with the given inputs.
 */
class ReelDetectorTest {

    private lateinit var detector: ReelDetector

    @Before
    fun setup() {
        detector = ReelDetector()
    }

    // ── Helper builders ───────────────────────────────────────────────────────

    private fun windowStateEvent(
        pkg: String = "com.instagram.android",
        windowClassName: String = "",
    ) = DetectionEvent(
        eventType = EVENT_WINDOW_STATE_CHANGED,
        packageName = pkg,
        sourceClassName = windowClassName,
        windowClassName = windowClassName,
        scrolledViewClassName = null,
        receivedAtMs = System.currentTimeMillis(),
    )

    private fun scrollEvent(
        pkg: String = "com.instagram.android",
        fromIndex: Int = 0,
        toIndex: Int = 1,
    ) = DetectionEvent(
        eventType = EVENT_VIEW_SCROLLED,
        packageName = pkg,
        sourceClassName = "androidx.viewpager2.widget.ViewPager2",
        windowClassName = null,
        scrolledViewClassName = "androidx.viewpager2.widget.ViewPager2",
        scrollFromIndex = fromIndex,
        scrollToIndex = toIndex,
        receivedAtMs = System.currentTimeMillis(),
    )

    private fun windowsChangedEvent(
        pkg: String = "com.instagram.android",
        sourceClass: String = "CommentBottomSheet",
    ) = DetectionEvent(
        eventType = EVENT_WINDOWS_CHANGED,
        packageName = pkg,
        sourceClassName = sourceClass,
        windowClassName = null,
        scrolledViewClassName = null,
        receivedAtMs = System.currentTimeMillis(),
    )

    /**
     * Enter the Reels viewer and consume the initial entry transition so that
     * subsequent scroll/transition tests start from a clean state.
     */
    private fun enterReelsViewerAndConsumeInitialTransition() {
        detector.process(windowStateEvent(windowClassName = "ReelsActivity"))
        assertEquals(DetectionState.REELS_VIEWER, detector.state.value)
        // The initial entry into REELS_VIEWER sets a pending transition — consume it
        assertTrue(detector.consumePendingTransition())
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `initial state is OUTSIDE_APP`() {
        assertEquals(DetectionState.OUTSIDE_APP, detector.state.value)
    }

    @Test
    fun `non-instagram package keeps state OUTSIDE_APP`() {
        detector.process(windowStateEvent(pkg = "com.example.other"))
        assertEquals(DetectionState.OUTSIDE_APP, detector.state.value)
    }

    @Test
    fun `instagram foreground without reels class moves to IN_APP_UNKNOWN`() {
        detector.process(windowStateEvent(windowClassName = "com.instagram.android.activity.MainTabActivity"))
        assertEquals(DetectionState.IN_APP_UNKNOWN, detector.state.value)
    }

    @Test
    fun `reels class name moves to REELS_VIEWER`() {
        detector.process(windowStateEvent(windowClassName = "com.instagram.reels.ReelsActivity"))
        assertEquals(DetectionState.REELS_VIEWER, detector.state.value)
    }

    @Test
    fun `non-reels instagram class from REELS_VIEWER moves to IN_APP_UNKNOWN`() {
        detector.process(windowStateEvent(windowClassName = "com.instagram.reels.ReelsActivity"))
        assertEquals(DetectionState.REELS_VIEWER, detector.state.value)

        detector.process(windowStateEvent(windowClassName = "com.instagram.android.activity.DirectInboxActivity"))
        assertEquals(DetectionState.IN_APP_UNKNOWN, detector.state.value)
    }

    @Test
    fun `reset always returns to OUTSIDE_APP`() {
        detector.process(windowStateEvent(windowClassName = "ReelsActivity"))
        assertEquals(DetectionState.REELS_VIEWER, detector.state.value)

        detector.reset()
        assertEquals(DetectionState.OUTSIDE_APP, detector.state.value)
    }

    @Test
    fun `comment sheet event from REELS_VIEWER moves to OBSCURED`() {
        enterReelsViewerAndConsumeInitialTransition()
        detector.process(windowsChangedEvent(sourceClass = "CommentBottomSheet"))
        assertEquals(DetectionState.OBSCURED, detector.state.value)
    }

    @Test
    fun `dismissing comment sheet returns to REELS_VIEWER`() {
        enterReelsViewerAndConsumeInitialTransition()
        detector.process(windowsChangedEvent(sourceClass = "CommentBottomSheet"))
        assertEquals(DetectionState.OBSCURED, detector.state.value)

        // Dismissing: non-overlay source returns to REELS_VIEWER
        detector.process(windowsChangedEvent(sourceClass = "ReelsViewPager"))
        assertEquals(DetectionState.REELS_VIEWER, detector.state.value)
    }

    @Test
    fun `scroll event without prior REELS_VIEWER state does not produce transition`() {
        // Start in IN_APP_UNKNOWN
        detector.process(windowStateEvent(windowClassName = "MainTabActivity"))
        assertEquals(DetectionState.IN_APP_UNKNOWN, detector.state.value)

        detector.process(scrollEvent())
        // No transition pending when not in REELS_VIEWER
        assertFalse(detector.consumePendingTransition())
    }

    @Test
    fun `scroll event in REELS_VIEWER produces pending transition after delay`() {
        enterReelsViewerAndConsumeInitialTransition()

        detector.process(scrollEvent(fromIndex = 0, toIndex = 1))

        // Immediately after scroll — not yet confirmed (need stability delay)
        assertFalse(detector.consumePendingTransition())

        // After confirmation delay
        val futureMs = System.currentTimeMillis() + 500L
        detector.checkPendingTransition(futureMs)
        assertTrue(detector.consumePendingTransition())
    }

    @Test
    fun `duplicate scroll events to same index do not produce multiple transitions`() {
        enterReelsViewerAndConsumeInitialTransition()

        // Same toIndex repeated — no new transition
        repeat(5) {
            detector.process(scrollEvent(fromIndex = 0, toIndex = 1))
        }

        val futureMs = System.currentTimeMillis() + 500L
        detector.checkPendingTransition(futureMs)
        assertTrue(detector.consumePendingTransition())

        // Consumed — no more pending
        assertFalse(detector.consumePendingTransition())
    }

    @Test
    fun `non-instagram scroll event does not trigger transition`() {
        enterReelsViewerAndConsumeInitialTransition()

        detector.process(scrollEvent(pkg = "com.example.other"))
        val futureMs = System.currentTimeMillis() + 500L
        detector.checkPendingTransition(futureMs)
        assertFalse(detector.consumePendingTransition())
    }

    @Test
    fun `entering REELS_VIEWER sets initial transition`() {
        // The initial entry into the Reels viewer is counted as one exposure
        assertEquals(-1L, detector.pendingReelTransitionAt)
        detector.process(windowStateEvent(windowClassName = "ReelsActivity"))
        assertTrue(detector.pendingReelTransitionAt > 0L)
    }

    @Test
    fun `leaving reels viewer cancels pending scroll transition`() {
        enterReelsViewerAndConsumeInitialTransition()

        detector.process(scrollEvent(fromIndex = 0, toIndex = 1))

        // Leave before confirmation
        detector.process(windowStateEvent(windowClassName = "MainTabActivity"))
        assertEquals(DetectionState.IN_APP_UNKNOWN, detector.state.value)

        // Transition should have been cancelled
        val futureMs = System.currentTimeMillis() + 500L
        detector.checkPendingTransition(futureMs)
        // State is now IN_APP_UNKNOWN, so checkPendingTransition won't fire
        assertFalse(detector.consumePendingTransition())
    }
}
