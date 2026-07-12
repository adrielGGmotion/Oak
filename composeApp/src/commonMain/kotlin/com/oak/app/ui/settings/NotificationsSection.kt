package com.oak.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_notifications_access_button
import oak.composeapp.generated.resources.settings_notifications_access_required
import oak.composeapp.generated.resources.settings_notifications_clear_queue
import oak.composeapp.generated.resources.settings_notifications_description
import oak.composeapp.generated.resources.settings_notifications_label
import oak.composeapp.generated.resources.settings_notifications_listener_bound
import oak.composeapp.generated.resources.settings_notifications_listener_disconnected
import oak.composeapp.generated.resources.settings_notifications_manage_apps
import oak.composeapp.generated.resources.settings_notifications_queued
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NotificationsSection(
    isEnabled: Boolean,
    accessGranted: Boolean,
    listenerBound: Boolean,
    pendingCount: Int,
    onToggle: (Boolean) -> Unit,
    onOpenAccessSettings: () -> Unit,
    onClearPending: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ToggleableHeadline(
            title = stringResource(Res.string.settings_notifications_label),
            description = stringResource(Res.string.settings_notifications_description),
            checked = isEnabled,
            onCheckedChange = onToggle,
        )

        if (isEnabled) {
            Spacer(Modifier.height(12.dp))

            if (!accessGranted) {
                PermissionRequiredRow(
                    message = stringResource(Res.string.settings_notifications_access_required),
                    buttonLabel = stringResource(Res.string.settings_notifications_access_button),
                    onGrant = onOpenAccessSettings,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (listenerBound) {
                                Res.string.settings_notifications_listener_bound
                            } else {
                                Res.string.settings_notifications_listener_disconnected
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (listenerBound) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    OutlinedButton(onClick = onOpenAccessSettings) {
                        Text(stringResource(Res.string.settings_notifications_manage_apps))
                    }
                }

                if (pendingCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.settings_notifications_queued, pendingCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = onClearPending) {
                            Text(stringResource(Res.string.settings_notifications_clear_queue))
                        }
                    }
                }
            }
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
