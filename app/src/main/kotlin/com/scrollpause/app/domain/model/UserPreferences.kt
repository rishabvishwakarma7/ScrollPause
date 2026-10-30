package com.scrollpause.app.domain.model

/**
 * Domain model for all user-configurable preferences.
 * Loaded from DataStore; consumed by ViewModels.
 */
data class UserPreferences(
    val dailyTarget: Int = DEFAULT_DAILY_TARGET,
    val showFloatingCounter: Boolean = true,
    val trackingPaused: Boolean = false,
    val remindersEnabled: Boolean = false,
    val reminderIntervalMinutes: Int = DEFAULT_REMINDER_INTERVAL,
    val onboardingCompleted: Boolean = false,
    val bubbleX: Float = -1f,  // -1 = use default position
    val bubbleY: Float = -1f,
) {
    companion object {
        const val DEFAULT_DAILY_TARGET = 30
        const val DEFAULT_REMINDER_INTERVAL = 15
        val REMINDER_INTERVAL_OPTIONS = listOf(5, 10, 15, 30)
    }
}
