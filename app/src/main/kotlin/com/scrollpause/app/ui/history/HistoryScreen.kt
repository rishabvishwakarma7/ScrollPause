package com.scrollpause.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrollpause.app.R
import com.scrollpause.app.domain.model.DailyStats
import com.scrollpause.app.ui.theme.AmberWarning
import com.scrollpause.app.ui.theme.MintPrimary
import com.scrollpause.app.ui.theme.NavyBackground
import com.scrollpause.app.ui.theme.NavySurface
import com.scrollpause.app.ui.theme.OutlineColor
import com.scrollpause.app.ui.theme.ScrollPauseTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HistoryScreen(viewModel: HistoryViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    HistoryContent(uiState = uiState)
}

@Composable
private fun HistoryContent(uiState: HistoryUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MintPrimary)
                }
            }
            uiState.days.isEmpty() -> {
                EmptyHistoryState()
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                ) {
                    LazyColumn {
                        items(uiState.days, key = { it.date }) { stat ->
                            HistoryDayRow(stat = stat)
                            HorizontalDivider(color = OutlineColor, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryDayRow(stat: DailyStats) {
    val dateFormatted = try {
        val date = LocalDate.parse(stat.date, DateTimeFormatter.ISO_LOCAL_DATE)
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
    } catch (e: Exception) {
        stat.date
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Date
        Column {
            Text(
                text = dateFormatted,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stat.sessionFormatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Count and exceeded indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (stat.targetExceeded) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = stringResource(R.string.history_target_exceeded),
                    tint = AmberWarning,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stat.reelCount.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (stat.targetExceeded) AmberWarning else MintPrimary,
                )
                Text(
                    text = stringResource(R.string.history_reels),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = OutlineColor,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Preview(showBackground = true, name = "History — With data")
@Composable
private fun HistoryScreenPreview() {
    ScrollPauseTheme(darkTheme = true) {
        val today = LocalDate.now()
        HistoryContent(
            uiState = HistoryUiState(
                isLoading = false,
                days = (0..9).map { i ->
                    DailyStats(
                        date = today.minusDays(i.toLong())
                            .format(DateTimeFormatter.ISO_LOCAL_DATE),
                        reelCount = listOf(42, 67, 15, 31, 55, 8, 72, 44, 22, 91)[i],
                        sessionMs = (i + 1) * 5 * 60_000L,
                        targetExceeded = listOf(false, true, false, true, false, false, true, false, false, true)[i],
                    )
                },
            ),
        )
    }
}

@Preview(showBackground = true, name = "History — Empty")
@Composable
private fun HistoryEmptyPreview() {
    ScrollPauseTheme(darkTheme = true) {
        HistoryContent(uiState = HistoryUiState(isLoading = false, days = emptyList()))
    }
}
