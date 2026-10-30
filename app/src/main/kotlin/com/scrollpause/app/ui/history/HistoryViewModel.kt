package com.scrollpause.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scrollpause.app.data.repository.ReelRepository
import com.scrollpause.app.domain.model.DailyStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryUiState(
    val days: List<DailyStats> = emptyList(),
    val isLoading: Boolean = true,
)

class HistoryViewModel(
    private val reelRepository: ReelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState(isLoading = true)
            val history = reelRepository.getHistory(limit = 60)
            _uiState.value = HistoryUiState(days = history, isLoading = false)
        }
    }
}

class HistoryViewModelFactory(
    private val reelRepository: ReelRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        HistoryViewModel(reelRepository) as T
}
