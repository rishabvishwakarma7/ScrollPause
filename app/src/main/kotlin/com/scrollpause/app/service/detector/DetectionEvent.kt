package com.scrollpause.app.service.detector

/**
 * Privacy-safe snapshot of an AccessibilityEvent.
 *
 * We capture only structural metadata — never node text, content descriptions
 * that include user content, or view IDs that could change with app updates.
 *
 * This intermediary object:
 *   1. Lets us process events off the binder thread (copy before the event is recycled).
 *   2. Enforces the privacy boundary — no raw AccessibilityEvent escapes this layer.
 *   3. Makes state machine logic unit-testable with synthetic inputs.
 */
data class DetectionEvent(
    /** AccessibilityEvent.getEventType() */
    val eventType: Int,

    /** AccessibilityEvent.getPackageName() as String, or null. */
    val packageName: String?,

    /**
     * The className of the event source (Activity, Fragment, View class).
     * This is safe to log — it reveals app structure, not user content.
     */
    val sourceClassName: String?,

    /**
     * For TYPE_WINDOW_STATE_CHANGED: the window title / activity class.
     * Empty string if not available.
     * We never log actual window text (which could be a chat message title).
     * We only use this to identify the Reels activity class.
     */
    val windowClassName: String?,

    /**
     * For TYPE_WINDOWS_CHANGED / TYPE_VIEW_SCROLLED: number of visible windows.
     * Used to infer overlay presence.
     */
    val windowCount: Int = 0,

    /**
     * For TYPE_VIEW_SCROLLED: the class name of the scrolled view.
     * We use this (not the scroll position or content) to identify the Reels pager.
     */
    val scrolledViewClassName: String?,

    /**
     * For TYPE_VIEW_SCROLLED: the scroll delta (positive = downward / next Reel).
     * We do NOT use this to infer content position.
     */
    val scrollFromIndex: Int = -1,
    val scrollToIndex: Int = -1,

    /** System.currentTimeMillis() when the event was received. */
    val receivedAtMs: Long,
)
