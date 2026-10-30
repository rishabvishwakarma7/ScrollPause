package com.scrollpause.app.service.detector

/**
 * States of the Reel detection state machine.
 *
 * Transitions:
 *
 *   OUTSIDE_APP  ──(Instagram foreground)──►  IN_APP_UNKNOWN
 *   IN_APP_UNKNOWN  ──(Reels signals seen)──►  REELS_VIEWER
 *   IN_APP_UNKNOWN  ──(Non-Reels screen)──►    IN_APP_UNKNOWN  (self-loop)
 *   REELS_VIEWER  ──(Comments/sheet)──►  OBSCURED
 *   REELS_VIEWER  ──(Left Instagram / non-Reels)──►  IN_APP_UNKNOWN / OUTSIDE_APP
 *   OBSCURED  ──(Sheet dismissed)──►  REELS_VIEWER
 *   OBSCURED  ──(Left Instagram)──►  OUTSIDE_APP
 *   Any  ──(Service paused / Accessibility revoked)──►  OUTSIDE_APP
 */
enum class DetectionState {
    /** Instagram is not the foreground app. */
    OUTSIDE_APP,

    /** Instagram is foreground but the screen has not been identified as the Reels viewer. */
    IN_APP_UNKNOWN,

    /**
     * The full-screen Reels viewer is confidently active.
     * The floating counter is shown and the session timer runs in this state.
     */
    REELS_VIEWER,

    /**
     * The Reels viewer is likely still open underneath a transient overlay
     * such as the comments sheet, share sheet, or a brief screen transition.
     * The floating counter is hidden; the timer is paused.
     */
    OBSCURED,
}
