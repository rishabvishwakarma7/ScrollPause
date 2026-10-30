package com.scrollpause.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.scrollpause.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property creates/reuses a single DataStore instance per Context.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scrollpause_prefs"
)

/**
 * Repository for all user preferences, backed by Jetpack DataStore.
 *
 * DataStore writes are serialized — no race conditions on concurrent updates.
 * The [preferences] Flow emits the complete [UserPreferences] on every change.
 */
class PreferencesRepository(context: Context) {

    private val dataStore = context.dataStore

    object Keys {
        val DAILY_TARGET = intPreferencesKey("daily_target")
        val SHOW_FLOATING_COUNTER = booleanPreferencesKey("show_floating_counter")
        val TRACKING_PAUSED = booleanPreferencesKey("tracking_paused")
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_INTERVAL_MINUTES = intPreferencesKey("reminder_interval_minutes")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val BUBBLE_X = floatPreferencesKey("bubble_x")
        val BUBBLE_Y = floatPreferencesKey("bubble_y")
    }

    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            dailyTarget = prefs[Keys.DAILY_TARGET] ?: UserPreferences.DEFAULT_DAILY_TARGET,
            showFloatingCounter = prefs[Keys.SHOW_FLOATING_COUNTER] ?: true,
            trackingPaused = prefs[Keys.TRACKING_PAUSED] ?: false,
            remindersEnabled = prefs[Keys.REMINDERS_ENABLED] ?: false,
            reminderIntervalMinutes = prefs[Keys.REMINDER_INTERVAL_MINUTES]
                ?: UserPreferences.DEFAULT_REMINDER_INTERVAL,
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            bubbleX = prefs[Keys.BUBBLE_X] ?: -1f,
            bubbleY = prefs[Keys.BUBBLE_Y] ?: -1f,
        )
    }

    suspend fun setDailyTarget(value: Int) {
        dataStore.edit { it[Keys.DAILY_TARGET] = value.coerceIn(1, 500) }
    }

    suspend fun setShowFloatingCounter(value: Boolean) {
        dataStore.edit { it[Keys.SHOW_FLOATING_COUNTER] = value }
    }

    suspend fun setTrackingPaused(value: Boolean) {
        dataStore.edit { it[Keys.TRACKING_PAUSED] = value }
    }

    suspend fun setRemindersEnabled(value: Boolean) {
        dataStore.edit { it[Keys.REMINDERS_ENABLED] = value }
    }

    suspend fun setReminderIntervalMinutes(value: Int) {
        dataStore.edit { it[Keys.REMINDER_INTERVAL_MINUTES] = value }
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    suspend fun setBubblePosition(x: Float, y: Float) {
        dataStore.edit {
            it[Keys.BUBBLE_X] = x
            it[Keys.BUBBLE_Y] = y
        }
    }
}
