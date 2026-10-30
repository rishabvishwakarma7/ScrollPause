package com.scrollpause.app.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.scrollpause.app.BuildConfig
import com.scrollpause.app.ui.theme.MintPrimary
import com.scrollpause.app.ui.theme.NavyBackground
import com.scrollpause.app.ui.theme.NavySurface
import com.scrollpause.app.ui.theme.ScrollPauseTheme

/**
 * Debug-only diagnostics screen.
 * Shows the detection state machine state and a structural event log
 * (event type + class names only — no user content).
 *
 * This screen is only reachable in debug builds (gated in Navigation.kt
 * with BuildConfig.DEBUG).
 */
@Composable
fun DiagnosticsScreen() {
    // In a real integration, DiagnosticsViewModel would observe a shared
    // StateFlow published by the AccessibilityService. For the initial build,
    // it shows a placeholder that documents the intended interface.
    val vm: DiagnosticsViewModel = viewModel()
    val state by vm.detectionState.collectAsState()
    val log by vm.eventLog.collectAsState()

    DiagnosticsContent(
        stateLabel = state,
        eventLog = log,
        onClear = vm::clearLog,
    )
}

@Composable
private fun DiagnosticsContent(
    stateLabel: String,
    eventLog: List<String>,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                color = MintPrimary,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onClear) {
                Text("Clear")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Detection state",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MintPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Event log (structural — no user content)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(eventLog.reversed()) { entry ->
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticsPreview() {
    ScrollPauseTheme(darkTheme = true) {
        DiagnosticsContent(
            stateLabel = "REELS_VIEWER",
            eventLog = listOf(
                "[12:34:01] WINDOW_STATE_CHANGED pkg=com.instagram.android cls=ReelsActivity",
                "[12:34:00] VIEW_SCROLLED cls=ViewPager2 from=0 to=1",
                "[12:33:59] WINDOW_STATE_CHANGED pkg=com.instagram.android cls=MainTabActivity",
            ),
            onClear = {},
        )
    }
}
