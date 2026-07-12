@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.oak.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oak.app.data.SmsSyncState
import com.oak.app.ui.components.OakSlider
import com.oak.app.ui.components.RefreshIconButton
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_email_poll_never
import oak.composeapp.generated.resources.settings_sms_description
import oak.composeapp.generated.resources.settings_sms_last_poll
import oak.composeapp.generated.resources.settings_sms_permission_button
import oak.composeapp.generated.resources.settings_sms_permission_required
import oak.composeapp.generated.resources.settings_sms_poll_failed
import oak.composeapp.generated.resources.settings_sms_poll_interval
import oak.composeapp.generated.resources.settings_sms_queued
import oak.composeapp.generated.resources.settings_sms_read_label
import oak.composeapp.generated.resources.settings_sms_refresh
import oak.composeapp.generated.resources.settings_sms_send_description
import oak.composeapp.generated.resources.settings_sms_send_label
import oak.composeapp.generated.resources.settings_sms_send_permission_required
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
internal fun SmsSection(
    isSmsEnabled: Boolean,
    permissionGranted: Boolean,
    pollIntervalMinutes: Int,
    pendingCount: Int,
    syncState: SmsSyncState,
    isRefreshing: Boolean,
    isSmsSendEnabled: Boolean,
    sendPermissionGranted: Boolean,
    onToggleSms: (Boolean) -> Unit,
    onChangePollInterval: (Int) -> Unit,
    onRefresh: () -> Unit,
    onToggleSmsSend: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_sms_read_label),
            description = stringResource(Res.string.settings_sms_description),
            checked = isSmsEnabled,
            onCheckedChange = onToggleSms,
        )

        if (isSmsEnabled) {
            Spacer(Modifier.height(12.dp))

            if (!permissionGranted) {
                PermissionRequiredRow(
                    message = stringResource(Res.string.settings_sms_permission_required),
                    buttonLabel = stringResource(Res.string.settings_sms_permission_button),
                    onGrant = { onToggleSms(true) },
                )
            } else {
                if (pendingCount > 0) {
                    Text(
                        text = stringResource(Res.string.settings_sms_queued, pendingCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                val smsPresets = listOf(0, 5, 15, 30, 60)
                val neverLabel = stringResource(Res.string.settings_email_poll_never)
                val initialSmsPos = smsPresets.indexOf(pollIntervalMinutes)
                    .takeIf { it >= 0 }?.toFloat() ?: 0f
                var smsSliderValue by remember(pollIntervalMinutes) {
                    mutableStateOf(initialSmsPos)
                }
                val currentSmsMinutes = smsPresets[smsSliderValue.roundToInt()]
                val smsDisplay = if (currentSmsMinutes == 0) neverLabel else "${currentSmsMinutes}m"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_sms_poll_interval, currentSmsMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = smsDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                OakSlider(
                    value = smsSliderValue,
                    onValueChange = { smsSliderValue = it },
                    onValueChangeFinished = {
                        onChangePollInterval(smsPresets[smsSliderValue.roundToInt()])
                    },
                    valueRange = 0f..(smsPresets.size - 1).toFloat(),
                    steps = smsPresets.size - 2,
                )

                Spacer(Modifier.height(8.dp))

                val nowMs = remember(syncState) { Clock.System.now().toEpochMilliseconds() }
                val failed = syncState.lastError != null && syncState.lastAttemptEpochMs > 0
                val timestampMs = if (failed) syncState.lastAttemptEpochMs else syncState.lastSyncEpochMs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (timestampMs > 0) {
                        val relative = formatPollRelative(nowMs - timestampMs)
                        val text = if (failed) {
                            stringResource(Res.string.settings_sms_poll_failed, relative)
                        } else {
                            stringResource(Res.string.settings_sms_last_poll, relative)
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    RefreshIconButton(
                        onClick = onRefresh,
                        isRefreshing = isRefreshing,
                        contentDescription = stringResource(Res.string.settings_sms_refresh),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        ToggleableHeadline(
            title = stringResource(Res.string.settings_sms_send_label),
            description = stringResource(Res.string.settings_sms_send_description),
            checked = isSmsSendEnabled,
            onCheckedChange = onToggleSmsSend,
        )

        if (isSmsSendEnabled && !sendPermissionGranted) {
            Spacer(Modifier.height(8.dp))
            PermissionRequiredRow(
                message = stringResource(Res.string.settings_sms_send_permission_required),
                buttonLabel = stringResource(Res.string.settings_sms_permission_button),
                onGrant = { onToggleSmsSend(true) },
            )
        }
    }
}

@Composable
private fun PermissionRequiredRow(
    message: String,
    buttonLabel: String,
    onGrant: () -> Unit,
) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onGrant) {
        Text(buttonLabel)
    }
}

private fun formatPollRelative(diffMs: Long): String {
    val clamped = diffMs.coerceAtLeast(0L)
    val minutes = clamped / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
