package com.scrollpause.app.ui.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollpause.app.data.prefs.PreferencesRepository
import com.scrollpause.app.data.repository.ReelRepository
import com.scrollpause.app.domain.model.DailyStats
import com.scrollpause.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val todayCount: Int = 0,
    val todaySessionMs: Long = 0L,
    val dailyTarget: Int = UserPreferences.DEFAULT_DAILY_TARGET,
    val targetProgress: Float = 0f,   // 0.0 to 1.0
    val trackingPaused: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val lastSevenDays: List<DailyStats> = emptyList(),
    val sessionFormatted: String = "0 min",
    val targetExceeded: Boolean = false,
)

class HomeViewModel(
    private val reelRepository: ReelRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _accessibilityEnabled = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        reelRepository.observeToday(),
        reelRepository.observeLastSevenDays(),
        preferencesRepository.preferences,
        _accessibilityEnabled,
    ) { today, sevenDays, prefs, a11yEnabled ->
        val count = today?.reelCount ?: 0
        val sessionMs = today?.sessionMs ?: 0L
        val target = prefs.dailyTarget
        HomeUiState(
            todayCount = count,
            todaySessionMs = sessionMs,
            dailyTarget = target,
            targetProgress = if (target > 0) (count.toFloat() / target).coerceIn(0f, 1f) else 0f,
            trackingPaused = prefs.trackingPaused,
            accessibilityEnabled = a11yEnabled,
            lastSevenDays = sevenDays,
            sessionFormatted = formatSessionMs(sessionMs),
            targetExceeded = count >= target,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun checkAccessibilityStatus(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        _accessibilityEnabled.value = services.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    fun toggleTracking() {
        viewModelScope.launch {
            val current = preferencesRepository.preferences.first()
            preferencesRepository.setTrackingPaused(!current.trackingPaused)
        }
    }

    private fun formatSessionMs(ms: Long): String {
        val minutes = ms / 60_000
        val hours = minutes / 60
        val remainingMins = minutes % 60
        return when {
            hours > 0 -> "${hours}h ${remainingMins}m"
            minutes > 0 -> "$minutes min"
            else -> "< 1 min"
        }
    }
}

class HomeViewModelFactory(
    private val reelRepository: ReelRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HomeViewModel(reelRepository, preferencesRepository) as T
}
