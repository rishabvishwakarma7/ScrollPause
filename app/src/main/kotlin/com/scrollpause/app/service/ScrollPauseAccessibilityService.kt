package com.scrollpause.app.service

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scrollpause.app.BuildConfig
import com.scrollpause.app.MainActivity
import com.scrollpause.app.R
import com.scrollpause.app.ScrollPauseApplication
import com.scrollpause.app.ScrollPauseApplication.Companion.CHANNEL_REMINDERS
import com.scrollpause.app.service.counter.ReelCounter
import com.scrollpause.app.service.detector.DetectionEvent
import com.scrollpause.app.service.detector.DetectionState
import com.scrollpause.app.service.detector.ReelDetector
import com.scrollpause.app.service.overlay.OverlayManager
import com.scrollpause.app.service.session.SessionTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Core AccessibilityService for ScrollPause.
 *
 * Lifecycle:
 *   onServiceConnected  — initialize components, restore today's state
 *   onAccessibilityEvent — forward privacy-safe events to the detector
 *   onInterrupt         — service interrupted (do nothing; let state machine stay)
 *   onServiceDestroyed  — clean up overlay, cancel coroutines
 *
 * Threading:
 *   AccessibilityService callbacks arrive on the main thread.
 *   All Repository/Room work runs on [Dispatchers.IO].
 *   The scope uses [SupervisorJob] so a single coroutine failure doesn't
 *   tear down the whole service.
 *
 * Screen lock handling:
 *   We use PowerManager.isInteractive() at event-processing time.
 *   When the screen locks, no more events arrive, so the timer stops
 *   naturally at the next REELS_VIEWER → other transition or service restart.
 */
class ScrollPauseAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var detector: ReelDetector
    private lateinit var counter: ReelCounter
    private lateinit var sessionTimer: SessionTimer
    private lateinit var overlayManager: OverlayManager

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Reminder state — track last notification time to avoid repeats
    private var lastTargetNotifiedDate: String = ""
    private var lastReminderMs: Long = 0L

    private var stateObserverJob: Job? = null
    private var checkpointJob: Job? = null

    override fun onServiceConnected() {
        log("Service connected")

        val app = application as? ScrollPauseApplication ?: run {
            log("Application not ScrollPauseApplication — aborting")
            return
        }

        detector = ReelDetector()
        overlayManager = OverlayManager(
            context = this,
            onPositionChanged = { x, y ->
                serviceScope.launch(Dispatchers.IO) {
                    app.container.preferencesRepository.setBubblePosition(x, y)
                }
            },
        )

        sessionTimer = SessionTimer(
            repository = app.container.reelRepository,
            scope = serviceScope,
        )

        counter = ReelCounter(
            detector = detector,
            repository = app.container.reelRepository,
            scope = serviceScope,
        )

        // Restore today's persisted count and session time
        serviceScope.launch(Dispatchers.IO) {
            val today = app.container.reelRepository.getToday()
            counter.initialize(today?.reelCount ?: 0)
            sessionTimer.initializeTodayMs(today?.sessionMs ?: 0L)
        }

        counter.start()
        startCheckpointJob()
        observeStateAndUpdateOverlay(app)

        log("Service initialization complete")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Check tracking paused state from a fast in-memory flag
        // (We don't block here on a coroutine — just gate quickly)
        if (isTrackingPaused) {
            if (detector.state.value != DetectionState.OUTSIDE_APP) {
                detector.reset()
            }
            return
        }

        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        val isInteractive = pm?.isInteractive ?: true
        if (!isInteractive) return

        // Build a privacy-safe snapshot before the event may be recycled
        val detectionEvent = DetectionEvent(
            eventType = event.eventType,
            packageName = event.packageName?.toString(),
            sourceClassName = event.className?.toString(),
            // Window class name: for TYPE_WINDOW_STATE_CHANGED, the package+class combo
            // tells us which Activity is in focus. We use className as the window class.
            windowClassName = event.className?.toString(),
            windowCount = windows?.size ?: 0,
            scrolledViewClassName = event.className?.toString(),
            scrollFromIndex = event.fromIndex,
            scrollToIndex = event.toIndex,
            receivedAtMs = System.currentTimeMillis(),
        )

        serviceScope.launch {
            detector.process(detectionEvent)
        }
    }

    override fun onInterrupt() {
        log("Service interrupted")
        // The system interrupted the service (e.g., another accessibility feature took priority).
        // We don't reset state here — the next event will correct it.
    }

    override fun onDestroy() {
        log("Service destroying")
        stateObserverJob?.cancel()
        checkpointJob?.cancel()

        // Stop the session if one was active
        sessionTimer.stop()

        // Release overlay resources
        overlayManager.release()

        counter.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── State observation ────────────────────────────────────────────────────

    @Volatile
    private var isTrackingPaused = false

    @Volatile
    private var showFloatingCounter = true

    @Volatile
    private var dailyTarget = 30

    @Volatile
    private var remindersEnabled = false

    @Volatile
    private var reminderIntervalMs = 15 * 60 * 1000L

    @Volatile
    private var savedBubbleX = -1f

    @Volatile
    private var savedBubbleY = -1f

    private fun observeStateAndUpdateOverlay(app: ScrollPauseApplication) {
        // Observe preferences
        stateObserverJob = serviceScope.launch {
            launch {
                app.container.preferencesRepository.preferences.collectLatest { prefs ->
                    isTrackingPaused = prefs.trackingPaused
                    showFloatingCounter = prefs.showFloatingCounter
                    dailyTarget = prefs.dailyTarget
                    remindersEnabled = prefs.remindersEnabled
                    reminderIntervalMs = prefs.reminderIntervalMinutes * 60 * 1000L
                    savedBubbleX = prefs.bubbleX
                    savedBubbleY = prefs.bubbleY

                    if (isTrackingPaused) {
                        detector.reset()
                        sessionTimer.stop()
                        overlayManager.hide()
                    }
                }
            }

            // Observe state machine + counter together to manage overlay and timer
            launch {
                combine(
                    detector.state,
                    counter.todayCount,
                ) { state, count -> Pair(state, count) }.collectLatest { (state, count) ->
                    handleStateChange(state, count)
                }
            }
        }
    }

    private fun handleStateChange(state: DetectionState, count: Int) {
        when (state) {
            DetectionState.REELS_VIEWER -> {
                sessionTimer.start()
                if (showFloatingCounter && !isTrackingPaused) {
                    overlayManager.show(count, savedBubbleX, savedBubbleY)
                }
                checkTargetAndReminders(count)
            }
            DetectionState.OBSCURED -> {
                sessionTimer.stop()
                overlayManager.hide()
            }
            DetectionState.IN_APP_UNKNOWN, DetectionState.OUTSIDE_APP -> {
                sessionTimer.stop()
                overlayManager.hide()
            }
        }
    }

    private fun checkTargetAndReminders(currentCount: Int) {
        val today = LocalDate.now().format(dateFormatter)

        // Target notification — once per day
        if (currentCount >= dailyTarget && lastTargetNotifiedDate != today) {
            lastTargetNotifiedDate = today
            showTargetNotification(currentCount)
        }

        // Reminder notification
        if (remindersEnabled) {
            val nowMs = System.currentTimeMillis()
            if (lastReminderMs == 0L || nowMs - lastReminderMs >= reminderIntervalMs) {
                lastReminderMs = nowMs
                showReminderNotification(currentCount)
            }
        }
    }

    // ── Notifications ────────────────────────────────────────────────────────

    private fun showTargetNotification(count: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notification_target_reached_title))
            .setContentText(
                getString(R.string.notification_target_reached_body, count, dailyTarget)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID_TARGET, notification)
        } catch (e: SecurityException) {
            log("Notification permission denied: ${e.message}")
        }
    }

    private fun showReminderNotification(count: Int) {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notification_reminder_title))
            .setContentText(getString(R.string.notification_reminder_body, count))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID_REMINDER, notification)
        } catch (e: SecurityException) {
            log("Notification permission denied: ${e.message}")
        }
    }

    // ── Periodic checkpoint ───────────────────────────────────────────────────

    private fun startCheckpointJob() {
        checkpointJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(CHECKPOINT_INTERVAL_MS)
                sessionTimer.checkpoint()
            }
        }
    }

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d("ScrollPause/Service", msg)
    }

    companion object {
        private const val NOTIF_ID_TARGET = 1001
        private const val NOTIF_ID_REMINDER = 1002
        private const val CHECKPOINT_INTERVAL_MS = 30_000L
    }
}
