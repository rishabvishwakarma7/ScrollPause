package com.scrollpause.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import com.scrollpause.app.service.detector.DetectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the debug diagnostics screen.
 *
 * In a full integration this would be connected to a shared singleton
 * DiagnosticsBus (a SharedFlow published by the AccessibilityService).
 * For Stage 2 of the build, it provides a testable interface with a
 * placeholder state.
 *
 * Integration note:
 *   The AccessibilityService processes events on a background thread.
 *   To avoid coupling the service to the ViewModel, use a singleton
 *   DiagnosticsBus object with a MutableSharedFlow<String>:
 *
 *   object DiagnosticsBus {
 *       val events = MutableSharedFlow<String>(replay = 50, extraBufferCapacity = 100)
 *   }
 *
 *   The service posts to DiagnosticsBus.events; this ViewModel collects from it.
 */
class DiagnosticsViewModel : ViewModel() {

    private val _detectionState = MutableStateFlow(DetectionState.OUTSIDE_APP.name)
    val detectionState: StateFlow<String> = _detectionState.asStateFlow()

    private val _eventLog = MutableStateFlow<List<String>>(emptyList())
    val eventLog: StateFlow<List<String>> = _eventLog.asStateFlow()

    fun appendEvent(entry: String) {
        val current = _eventLog.value.toMutableList()
        current.add(entry)
        // Cap log at 200 entries to avoid unbounded memory growth
        if (current.size > 200) current.removeAt(0)
        _eventLog.value = current
    }

    fun updateState(state: String) {
        _detectionState.value = state
    }

    fun clearLog() {
        _eventLog.value = emptyList()
    }
}
