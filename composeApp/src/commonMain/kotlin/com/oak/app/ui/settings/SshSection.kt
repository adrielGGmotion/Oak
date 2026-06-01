package com.oak.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oak.app.ssh.SshAuthType
import com.oak.app.ssh.SshConnectionStatus
import com.oak.app.ui.OakOutlinedTextField
import com.oak.app.ui.handCursor
import com.oak.app.ui.oakAdaptiveCardBorder
import com.oak.app.ui.oakAdaptiveCardColors
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.common_cancel
import oak.composeapp.generated.resources.settings_ssh_add
import oak.composeapp.generated.resources.settings_ssh_add_server
import oak.composeapp.generated.resources.settings_ssh_auth_type
import oak.composeapp.generated.resources.settings_ssh_auth_type_label
import oak.composeapp.generated.resources.settings_ssh_connect
import oak.composeapp.generated.resources.settings_ssh_description
import oak.composeapp.generated.resources.settings_ssh_host
import oak.composeapp.generated.resources.settings_ssh_name
import oak.composeapp.generated.resources.settings_ssh_no_servers
import oak.composeapp.generated.resources.settings_ssh_passphrase
import oak.composeapp.generated.resources.settings_ssh_password
import oak.composeapp.generated.resources.settings_ssh_port
import oak.composeapp.generated.resources.settings_ssh_private_key
import oak.composeapp.generated.resources.settings_ssh_remove
import oak.composeapp.generated.resources.settings_ssh_servers
import oak.composeapp.generated.resources.settings_ssh_status_connected
import oak.composeapp.generated.resources.settings_ssh_status_connecting
import oak.composeapp.generated.resources.settings_ssh_status_disconnected
import oak.composeapp.generated.resources.settings_ssh_status_error
import oak.composeapp.generated.resources.settings_ssh_username
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SshServersSection(
    sshServers: ImmutableList<SshServerUiState>,
    onAddSshServer: (String, String, Int, String, String, String, String, SshAuthType) -> Unit,
    onRemoveSshServer: (String) -> Unit,
    onToggleSshServer: (String, Boolean) -> Unit,
    onConnectSshServer: (String) -> Unit,
    showAddDialog: Boolean,
    onShowAddDialog: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_ssh_servers),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_ssh_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        for (server in sshServers) {
            SshServerCard(
                server = server,
                onToggle = { enabled -> onToggleSshServer(server.id, enabled) },
                onRemove = { onRemoveSshServer(server.id) },
                onConnect = { onConnectSshServer(server.id) },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (sshServers.isEmpty()) {
            Text(
                text = stringResource(Res.string.settings_ssh_no_servers),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        OutlinedButton(
            onClick = { onShowAddDialog(true) },
            modifier = Modifier.align(Alignment.CenterHorizontally).handCursor(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(Res.string.settings_ssh_add_server))
        }
    }

    if (showAddDialog) {
        AddSshServerDialog(
            onDismiss = { onShowAddDialog(false) },
            onAdd = onAddSshServer,
        )
    }
}

@Composable
private fun SshServerCard(
    server: SshServerUiState,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onConnect: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().handCursor(),
        colors = oakAdaptiveCardColors(),
        border = oakAdaptiveCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusColor = when (server.connectionStatus) {
                    SshConnectionStatus.Connected -> StatusColorConnected
                    SshConnectionStatus.Connecting -> StatusColorChecking
                    SshConnectionStatus.Error -> StatusColorError
                    SshConnectionStatus.Disconnected -> StatusColorUnknown
                    SshConnectionStatus.Unknown -> StatusColorUnknown
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(12.dp))

                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${server.username}@${server.host}:${server.port}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Switch(
                    checked = server.isEnabled,
                    onCheckedChange = onToggle,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(12.dp))

                val statusText = when (server.connectionStatus) {
                    SshConnectionStatus.Connected -> stringResource(Res.string.settings_ssh_status_connected)
                    SshConnectionStatus.Connecting -> stringResource(Res.string.settings_ssh_status_connecting)
                    SshConnectionStatus.Error -> server.errorMessage ?: stringResource(Res.string.settings_ssh_status_error)
                    SshConnectionStatus.Disconnected -> stringResource(Res.string.settings_ssh_status_disconnected)
                    SshConnectionStatus.Unknown -> ""
                }
                if (statusText.isNotEmpty()) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = when (server.connectionStatus) {
                            SshConnectionStatus.Error -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Text(
                    text = stringResource(Res.string.settings_ssh_auth_type, server.authType.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onConnect,
                        enabled = server.connectionStatus != SshConnectionStatus.Connected,
                        modifier = Modifier.handCursor(),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.settings_ssh_connect))
                    }
                    TextButton(onClick = onRemove, modifier = Modifier.handCursor()) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(Res.string.settings_ssh_remove),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSshServerDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int, String, String, String, String, SshAuthType) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("22") }
    var username by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf(SshAuthType.Password) }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }

    val fieldsValid = (name.isNotBlank() && host.isNotBlank() && username.isNotBlank()) &&
        ((authType == SshAuthType.Password && password.isNotBlank()) ||
            (authType == SshAuthType.Key && privateKey.isNotBlank()))

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.settings_ssh_add_server),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            OakOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.settings_ssh_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OakOutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text(stringResource(Res.string.settings_ssh_host)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Row {
                OakOutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(Res.string.settings_ssh_port)) },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
                Spacer(Modifier.width(8.dp))
                OakOutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.settings_ssh_username)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))

            var authTypeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = authTypeExpanded,
                onExpandedChange = { authTypeExpanded = it },
            ) {
                OutlinedTextField(
                    value = authType.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.settings_ssh_auth_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = authTypeExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                )
                ExposedDropdownMenu(
                    expanded = authTypeExpanded,
                    onDismissRequest = { authTypeExpanded = false },
                ) {
                    SshAuthType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.name) },
                            onClick = {
                                authType = type
                                authTypeExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (authType == SshAuthType.Password) {
                OakOutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.settings_ssh_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OakOutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = { Text(stringResource(Res.string.settings_ssh_private_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OakOutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.settings_ssh_passphrase)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val portInt = (port.toIntOrNull() ?: 22).coerceIn(1, 65535)
                        onAdd(name, host, portInt, username, password, privateKey, passphrase, authType)
                    },
                    enabled = fieldsValid,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_ssh_add))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
