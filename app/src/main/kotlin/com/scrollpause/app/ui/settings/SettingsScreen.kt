package com.scrollpause.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.scrollpause.app.R
import com.scrollpause.app.domain.model.UserPreferences
import com.scrollpause.app.ui.theme.ErrorRed
import com.scrollpause.app.ui.theme.MintPrimary
import com.scrollpause.app.ui.theme.NavyBackground
import com.scrollpause.app.ui.theme.NavySurface
import com.scrollpause.app.ui.theme.OutlineColor
import com.scrollpause.app.ui.theme.ScrollPauseTheme

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val prefs by viewModel.preferences.collectAsState()
    SettingsContent(
        prefs = prefs,
        onSetDailyTarget = viewModel::setDailyTarget,
        onSetShowCounter = viewModel::setShowFloatingCounter,
        onSetTrackingPaused = viewModel::setTrackingPaused,
        onSetRemindersEnabled = viewModel::setRemindersEnabled,
        onSetReminderInterval = viewModel::setReminderInterval,
        onDeleteHistory = viewModel::deleteAllHistory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    prefs: UserPreferences,
    onSetDailyTarget: (Int) -> Unit,
    onSetShowCounter: (Boolean) -> Unit,
    onSetTrackingPaused: (Boolean) -> Unit,
    onSetRemindersEnabled: (Boolean) -> Unit,
    onSetReminderInterval: (Int) -> Unit,
    onDeleteHistory: () -> Unit,
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var intervalMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NavyBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp),
        )

        // ── Tracking ──────────────────────────────────────────────────────────
        SettingsSection(title = stringResource(R.string.settings_tracking_section)) {
            SettingsRow(
                label = if (prefs.trackingPaused) stringResource(R.string.home_resume_tracking)
                else stringResource(R.string.home_pause_tracking),
            ) {
                Switch(
                    checked = !prefs.trackingPaused,
                    onCheckedChange = { onSetTrackingPaused(!it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = NavyBackground, checkedTrackColor = MintPrimary),
                )
            }
        }

        // ── Daily Target ──────────────────────────────────────────────────────
        SettingsSection(title = stringResource(R.string.settings_daily_target)) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${prefs.dailyTarget} ${stringResource(R.string.settings_daily_target_suffix)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MintPrimary,
                    )
                }
                Slider(
                    value = prefs.dailyTarget.toFloat(),
                    onValueChange = { onSetDailyTarget(it.toInt()) },
                    valueRange = 1f..200f,
                    steps = 0,
                    colors = SliderDefaults.colors(
                        thumbColor = MintPrimary,
                        activeTrackColor = MintPrimary,
                    ),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("200", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Display ───────────────────────────────────────────────────────────
        SettingsSection(title = "Display") {
            SettingsRow(label = stringResource(R.string.settings_floating_counter)) {
                Switch(
                    checked = prefs.showFloatingCounter,
                    onCheckedChange = onSetShowCounter,
                    colors = SwitchDefaults.colors(checkedThumbColor = NavyBackground, checkedTrackColor = MintPrimary),
                )
            }
        }

        // ── Reminders ─────────────────────────────────────────────────────────
        SettingsSection(title = stringResource(R.string.settings_reminders)) {
            SettingsRow(label = stringResource(R.string.settings_reminders)) {
                Switch(
                    checked = prefs.remindersEnabled,
                    onCheckedChange = onSetRemindersEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = NavyBackground, checkedTrackColor = MintPrimary),
                )
            }
            if (prefs.remindersEnabled) {
                HorizontalDivider(color = OutlineColor, thickness = 0.5.dp)
                SettingsRow(label = stringResource(R.string.settings_reminder_interval)) {
                    ExposedDropdownMenuBox(
                        expanded = intervalMenuExpanded,
                        onExpandedChange = { intervalMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = "${prefs.reminderIntervalMinutes} min",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(0.5f),
                        )
                        ExposedDropdownMenu(
                            expanded = intervalMenuExpanded,
                            onDismissRequest = { intervalMenuExpanded = false },
                        ) {
                            UserPreferences.REMINDER_INTERVAL_OPTIONS.forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("$mins min") },
                                    onClick = {
                                        onSetReminderInterval(mins)
                                        intervalMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Permissions ───────────────────────────────────────────────────────
        SettingsSection(title = stringResource(R.string.settings_permissions_section)) {
            SettingsRow(label = stringResource(R.string.settings_accessibility)) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) {
                    Text(stringResource(R.string.settings_open_settings), color = MintPrimary)
                }
            }
            HorizontalDivider(color = OutlineColor, thickness = 0.5.dp)
            SettingsRow(label = stringResource(R.string.settings_overlay)) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }) {
                    Text(stringResource(R.string.settings_open_settings), color = MintPrimary)
                }
            }
        }

        // ── Data ──────────────────────────────────────────────────────────────
        SettingsSection(title = stringResource(R.string.settings_data_section)) {
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
            ) {
                Text(
                    text = stringResource(R.string.settings_delete_history),
                    color = ErrorRed,
                )
            }
        }

        // ── Privacy ───────────────────────────────────────────────────────────
        SettingsSection(title = stringResource(R.string.settings_privacy_section)) {
            Text(
                text = stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.settings_delete_confirm_title)) },
            text = { Text(stringResource(R.string.settings_delete_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteHistory()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                ) {
                    Text(stringResource(R.string.settings_delete_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MintPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        content()
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    ScrollPauseTheme(darkTheme = true) {
        SettingsContent(
            prefs = UserPreferences(
                dailyTarget = 30,
                remindersEnabled = true,
                reminderIntervalMinutes = 15,
            ),
            onSetDailyTarget = {},
            onSetShowCounter = {},
            onSetTrackingPaused = {},
            onSetRemindersEnabled = {},
            onSetReminderInterval = {},
            onDeleteHistory = {},
        )
    }
}
