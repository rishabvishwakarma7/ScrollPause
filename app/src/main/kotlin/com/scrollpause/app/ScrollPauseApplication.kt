package com.scrollpause.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

/**
 * Application class — entry point for app-wide initialization.
 *
 * Responsibilities:
 *   - Create the [AppContainer] (manual DI).
 *   - Register notification channels (required on Android 8+ before showing notifications).
 */
class ScrollPauseApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                getString(R.string.notification_channel_reminders_name),
                NotificationManager.IMPORTANCE_LOW, // Low = no sound, brief banner
            ).apply {
                description = getString(R.string.notification_channel_reminders_desc)
                setShowBadge(false)
            }

            manager.createNotificationChannel(remindersChannel)
        }
    }

    companion object {
        const val CHANNEL_REMINDERS = "scrollpause_reminders"
    }
}
