package com.scrollpause.app.ui.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.scrollpause.app.R
import com.scrollpause.app.domain.model.DailyStats
import com.scrollpause.app.ui.theme.AmberWarning
import com.scrollpause.app.ui.theme.ErrorRed
import com.scrollpause.app.ui.theme.MintPrimary
import com.scrollpause.app.ui.theme.NavyBackground
import com.scrollpause.app.ui.theme.NavySurface
import com.scrollpause.app.ui.theme.ScrollPauseTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check accessibility status when the screen is resumed
    // (user may have just returned from Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAccessibilityStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    HomeContent(
        uiState = uiState,
        onToggleTracking = { viewModel.toggleTracking() },
        onOpenAccessibilitySettings = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    onToggleTracking: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── App title + status chip ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ScrollPause",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TrackingStatusChip(
                paused = uiState.trackingPaused,
                a11yEnabled = uiState.accessibilityEnabled,
            )
        }

        // ── Main count card ───────────────────────────────────────────────────
        ReelCountCard(uiState = uiState)

        // ── Session time ──────────────────────────────────────────────────────
        InfoCard(
            label = stringResource(R.string.home_tracked_time),
            value = uiState.sessionFormatted,
        )

        // ── 7-day chart ───────────────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.home_last_7_days),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SevenDayChart(
                    days = uiState.lastSevenDays,
                    dailyTarget = uiState.dailyTarget,
                )
            }
        }

        // ── Permission status ──────────────────────────────────────────────────
        if (!uiState.accessibilityEnabled) {
            PermissionWarningCard(
                text = stringResource(R.string.home_accessibility_missing),
                onClick = onOpenAccessibilitySettings,
            )
        }

        // ── Pause / Resume button ──────────────────────────────────────────────
        Button(
            onClick = onToggleTracking,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.trackingPaused) MintPrimary else NavySurface,
            ),
        ) {
            Icon(
                imageVector = if (uiState.trackingPaused) Icons.Default.PlayArrow
                else Icons.Default.Pause,
                contentDescription = null,
            )
            Text(
                text = if (uiState.trackingPaused)
                    stringResource(R.string.home_resume_tracking)
                else stringResource(R.string.home_pause_tracking),
                modifier = Modifier.padding(start = 8.dp),
                color = if (uiState.trackingPaused) NavyBackground else MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ReelCountCard(uiState: HomeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Animated counter — slides up when count changes
            AnimatedContent(
                targetState = uiState.todayCount,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(200)) +
                            slideInVertically(animationSpec = tween(200)) { it / 2 }) togetherWith
                            fadeOut(animationSpec = tween(100))
                },
                label = "reel_count",
                modifier = Modifier.semantics {
                    contentDescription = "${uiState.todayCount} reels today"
                },
            ) { count ->
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = if (uiState.targetExceeded) AmberWarning else MintPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Text(
                text = stringResource(R.string.home_reels_today),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Progress bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${stringResource(R.string.home_daily_target)}: ${uiState.dailyTarget}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${(uiState.targetProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (uiState.targetExceeded) AmberWarning else MintPrimary,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { uiState.targetProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .semantics {
                        contentDescription =
                            "Progress: ${(uiState.targetProgress * 100).toInt()}% of daily target"
                    },
                color = if (uiState.targetExceeded) AmberWarning else MintPrimary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TrackingStatusChip(paused: Boolean, a11yEnabled: Boolean) {
    val (label, color) = when {
        !a11yEnabled -> Pair(stringResource(R.string.home_tracking_no_permission), ErrorRed)
        paused -> Pair(stringResource(R.string.home_tracking_paused), AmberWarning)
        else -> Pair(stringResource(R.string.home_tracking_active), MintPrimary)
    }

    SuggestionChip(
        onClick = {},
        label = { Text(text = label, color = color) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = color.copy(alpha = 0.15f),
        ),
        border = SuggestionChipDefaults.suggestionChipBorder(
            enabled = true,
            borderColor = color.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun PermissionWarningCard(text: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = ErrorRed.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = ErrorRed,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed,
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "Home — Active")
@Composable
private fun HomeScreenActivePreview() {
    ScrollPauseTheme(darkTheme = true) {
        val today = LocalDate.now()
        HomeContent(
            uiState = HomeUiState(
                todayCount = 42,
                dailyTarget = 60,
                targetProgress = 0.70f,
                trackingPaused = false,
                accessibilityEnabled = true,
                sessionFormatted = "18 min",
                lastSevenDays = (0..6).map { i ->
                    DailyStats(
                        date = today.minusDays(i.toLong())
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                        reelCount = listOf(42, 67, 15, 31, 55, 8, 72)[i],
                        sessionMs = 0L,
                        targetExceeded = listOf(false, true, false, true, false, false, true)[i],
                    )
                },
            ),
            onToggleTracking = {},
            onOpenAccessibilitySettings = {},
        )
    }
}

@Preview(showBackground = true, name = "Home — Paused")
@Composable
private fun HomeScreenPausedPreview() {
    ScrollPauseTheme(darkTheme = true) {
        HomeContent(
            uiState = HomeUiState(
                todayCount = 12,
                dailyTarget = 30,
                targetProgress = 0.4f,
                trackingPaused = true,
                accessibilityEnabled = true,
                sessionFormatted = "6 min",
                lastSevenDays = emptyList(),
            ),
            onToggleTracking = {},
            onOpenAccessibilitySettings = {},
        )
    }
}
