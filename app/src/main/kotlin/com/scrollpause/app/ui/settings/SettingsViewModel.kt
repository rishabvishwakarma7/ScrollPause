package com.scrollpause.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollpause.app.data.prefs.PreferencesRepository
import com.scrollpause.app.data.repository.ReelRepository
import com.scrollpause.app.domain.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val reelRepository: ReelRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UserPreferences(),
        )

    fun setDailyTarget(value: Int) {
        viewModelScope.launch { preferencesRepository.setDailyTarget(value) }
    }

    fun setShowFloatingCounter(value: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowFloatingCounter(value) }
    }

    fun setTrackingPaused(value: Boolean) {
        viewModelScope.launch { preferencesRepository.setTrackingPaused(value) }
    }

    fun setRemindersEnabled(value: Boolean) {
        viewModelScope.launch { preferencesRepository.setRemindersEnabled(value) }
    }

    fun setReminderInterval(minutes: Int) {
        viewModelScope.launch { preferencesRepository.setReminderIntervalMinutes(minutes) }
    }

    fun deleteAllHistory() {
        viewModelScope.launch {
            reelRepository.deleteAllHistory()
        }
    }
}

class SettingsViewModelFactory(
    private val reelRepository: ReelRepository,
    private val preferencesRepository: PreferencesRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(reelRepository, preferencesRepository) as T
}
