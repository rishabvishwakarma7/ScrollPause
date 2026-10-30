package com.scrollpause.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scrollpause.app.domain.model.DailyStats
import com.scrollpause.app.ui.theme.AmberWarning
import com.scrollpause.app.ui.theme.MintPrimary
import com.scrollpause.app.ui.theme.NavySurfaceVariant
import com.scrollpause.app.ui.theme.OutlineColor
import com.scrollpause.app.ui.theme.ScrollPauseTheme
import com.scrollpause.app.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/**
 * Native Canvas bar chart for the 7-day Reel history.
 *
 * No third-party charting library — drawn with Compose Canvas APIs.
 * This keeps the APK lean and gives full control over accessibility.
 */
@Composable
fun SevenDayChart(
    days: List<DailyStats>,
    dailyTarget: Int,
    modifier: Modifier = Modifier,
) {
    val barColor = MintPrimary
    val exceededColor = AmberWarning
    val emptyColor = NavySurfaceVariant
    val targetLineColor = OutlineColor
    val labelColor = TextSecondary
    val textMeasurer = rememberTextMeasurer()

    val labelStyle = TextStyle(
        color = labelColor,
        fontSize = 10.sp,
        fontWeight = FontWeight.Normal,
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
    ) {
        if (days.isEmpty()) return@Canvas

        val labelAreaHeightPx = 16.dp.toPx()
        val chartHeight = size.height - labelAreaHeightPx
        val barCount = minOf(days.size, 7)
        val totalBarWidth = size.width
        val barWidth = (totalBarWidth / barCount) * 0.55f
        val gapWidth = (totalBarWidth / barCount) * 0.45f

        val maxCount = maxOf(days.maxOfOrNull { it.reelCount } ?: 0, dailyTarget, 1)

        // Draw target reference line
        val targetLineY = chartHeight * (1f - dailyTarget.toFloat() / maxCount)
        if (dailyTarget > 0 && targetLineY >= 0f) {
            drawLine(
                color = targetLineColor,
                start = Offset(0f, targetLineY),
                end = Offset(size.width, targetLineY),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Draw bars (newest on the right, oldest on the left — days list is newest-first)
        val sortedDays = days.take(barCount).reversed()  // oldest → newest = left → right
        val dayFormatter = DateTimeFormatter.ofPattern("EEE")

        sortedDays.forEachIndexed { index, stat ->
            val x = index * (barWidth + gapWidth)
            val heightRatio = stat.reelCount.toFloat() / maxCount
            val barHeight = (chartHeight * heightRatio).coerceAtLeast(2.dp.toPx())
            val barTop = chartHeight - barHeight

            // Bar background (empty state visual)
            drawRoundRect(
                color = emptyColor,
                topLeft = Offset(x, 0f),
                size = Size(barWidth, chartHeight),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )

            // Filled bar
            if (stat.reelCount > 0) {
                drawRoundRect(
                    color = if (stat.targetExceeded) exceededColor else barColor,
                    topLeft = Offset(x, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }

            // Day label below
            try {
                val date = LocalDate.parse(stat.date, DateTimeFormatter.ISO_LOCAL_DATE)
                val label = date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault())
                    .take(1)  // Single letter: M T W T F S S
                val textResult = textMeasurer.measure(label, labelStyle)
                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(
                        x = x + barWidth / 2 - textResult.size.width / 2,
                        y = chartHeight + 2.dp.toPx(),
                    ),
                )
            } catch (e: Exception) {
                // Malformed date — skip label
            }
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SevenDayChartPreview() {
    ScrollPauseTheme(darkTheme = true) {
        val today = LocalDate.now()
        val sampleDays = (0..6).map { i ->
            DailyStats(
                date = today.minusDays(i.toLong())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE),
                reelCount = listOf(42, 67, 15, 31, 55, 8, 72)[i],
                sessionMs = 0L,
                targetExceeded = listOf(false, true, false, true, false, false, true)[i],
            )
        }
        SevenDayChart(
            days = sampleDays,
            dailyTarget = 30,
        )
    }
}
