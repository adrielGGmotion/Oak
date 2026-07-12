package com.oak.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oak.app.data.EmailAccount
import com.oak.app.data.EmailSyncState
import com.oak.app.ui.components.OakSlider
import com.oak.app.ui.components.SettingsListItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_email
import oak.composeapp.generated.resources.settings_email_description
import oak.composeapp.generated.resources.settings_email_empty
import oak.composeapp.generated.resources.settings_email_last_poll
import oak.composeapp.generated.resources.settings_email_poll_failed
import oak.composeapp.generated.resources.settings_email_poll_interval
import oak.composeapp.generated.resources.settings_email_poll_never
import oak.composeapp.generated.resources.settings_email_queued
import oak.composeapp.generated.resources.settings_email_refresh
import oak.composeapp.generated.resources.settings_email_remove
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Clock

@Composable
internal fun EmailSection(
    isEmailEnabled: Boolean,
    emailAccounts: ImmutableList<EmailAccount>,
    pollIntervalMinutes: Int,
    pendingCount: Int,
    syncStates: ImmutableMap<String, EmailSyncState>,
    refreshingAccountIds: ImmutableSet<String>,
    onToggleEmail: (Boolean) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onChangePollInterval: (Int) -> Unit,
    onRefreshAccount: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_email),
            description = stringResource(Res.string.settings_email_description),
            checked = isEmailEnabled,
            onCheckedChange = onToggleEmail,
        )

        if (isEmailEnabled) {
            Spacer(Modifier.height(12.dp))

            if (emailAccounts.isEmpty()) {
                Text(
                    text = stringResource(Res.string.settings_email_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (pendingCount > 0) {
                    Text(
                        text = stringResource(Res.string.settings_email_queued, pendingCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                val emailPresets = listOf(0, 5, 15, 30, 60)
                val neverLabel = stringResource(Res.string.settings_email_poll_never)
                val initialEmailPos = emailPresets.indexOf(pollIntervalMinutes)
                    .takeIf { it >= 0 }?.toFloat() ?: 0f
                var emailSliderValue by remember(pollIntervalMinutes) {
                    mutableStateOf(initialEmailPos)
                }
                val currentEmailMinutes = emailPresets[emailSliderValue.roundToInt()]
                val emailDisplay = if (currentEmailMinutes == 0) neverLabel else "${currentEmailMinutes}m"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_email_poll_interval, currentEmailMinutes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = emailDisplay,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                OakSlider(
                    value = emailSliderValue,
                    onValueChange = { emailSliderValue = it },
                    onValueChangeFinished = {
                        onChangePollInterval(emailPresets[emailSliderValue.roundToInt()])
                    },
                    valueRange = 0f..(emailPresets.size - 1).toFloat(),
                    steps = emailPresets.size - 2,
                )

                Spacer(Modifier.height(12.dp))

                val nowMs = remember(syncStates) { Clock.System.now().toEpochMilliseconds() }
                for (account in emailAccounts) {
                    SettingsListItem(
                        title = account.email,
                        subtitle = "${account.imapHost}:${account.imapPort}",
                        onDelete = { onRemoveAccount(account.id) },
                        deleteContentDescription = stringResource(Res.string.settings_email_remove),
                        onRefresh = { onRefreshAccount(account.id) },
                        refreshContentDescription = stringResource(Res.string.settings_email_refresh),
                        isRefreshing = account.id in refreshingAccountIds,
                    )
                    val sync = syncStates[account.id]
                    if (sync != null) {
                        val failed = sync.lastError != null && sync.lastAttemptEpochMs > 0
                        val timestampMs = if (failed) sync.lastAttemptEpochMs else sync.lastSyncEpochMs
                        if (timestampMs > 0) {
                            val relative = formatPollRelative(nowMs - timestampMs)
                            val text = if (failed) {
                                stringResource(Res.string.settings_email_poll_failed, relative)
                            } else {
                                stringResource(Res.string.settings_email_last_poll, relative)
                            }
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
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
