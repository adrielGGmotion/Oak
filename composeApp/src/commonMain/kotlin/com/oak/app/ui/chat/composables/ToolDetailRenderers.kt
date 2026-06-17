package com.oak.app.ui.chat.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// Type & registry
// ---------------------------------------------------------------------------

typealias ToolDetailRenderer = @Composable (action: ToolAction, modifier: Modifier) -> Unit

fun toolDetailRendererFor(toolName: String): ToolDetailRenderer? = when (toolName) {
    "web_search" -> ::WebSearchDetail
    "fetch_url" -> ::FetchUrlDetail
    "open_url" -> ::OpenUrlDetail
    "open_file" -> ::OpenFileDetail
    "set_alarm" -> ::SetAlarmDetail
    "create_calendar_event" -> ::CalendarEventDetail
    "send_notification" -> ::NotificationDetail
    "check_notifications", "search_notifications" -> ::NotificationListDetail
    "read_notification" -> ::NotificationReadDetail
    "check_email", "search_email" -> ::EmailListDetail
    "read_email" -> ::EmailReadDetail
    "compose_email", "reply_email" -> ::EmailSentDetail
    "check_sms", "search_sms" -> ::SmsListDetail
    "read_sms" -> ::SmsReadDetail
    "send_sms", "reply_sms" -> ::SmsDraftDetail
    "execute_shell_command" -> ::ShellCommandDetail
    "manage_process" -> ::ManageProcessDetail
    "read_file" -> ::FileReadDetail
    "edit_file" -> ::FileEditDetail
    "schedule_task" -> ::ScheduleTaskDetail
    "cancel_task", "list_tasks" -> ::TaskManagementDetail
    "memory_store", "memory_forget", "memory_learn", "memory_reinforce", "promote_learning" -> ::MemoryDetail
    "setup_email" -> ::EmailSetupDetail
    "ssh_connect" -> ::SshConnectDetail
    "ssh_disconnect" -> ::SshDisconnectDetail
    "ssh_execute_command" -> ::SshCommandDetail
    "ssh_status" -> ::SshStatusDetail
    "ssh_transfer_file" -> ::SshTransferDetail
    "ssh_read_file" -> ::SshReadFileDetail
    "ssh_write_file", "ssh_edit_file" -> ::SshWriteFileDetail
    "ssh_list_directory" -> ::SshListDirDetail
    "ssh_delete_path" -> ::SshDeleteDetail
    "ssh_make_directory" -> ::SshMkdirDetail
    "ssh_file_info" -> ::SshFileInfoDetail
    "ssh_search_files", "ssh_grep" -> ::SshSearchDetail
    else -> null
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
private fun FaviconImage(url: String, fallbackLetter: String, modifier: Modifier = Modifier) {
    if (url.isNotEmpty()) {
        coil3.compose.SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp)),
            error = {
                FaviconFallback(fallbackLetter, modifier)
            },
        )
    } else {
        FaviconFallback(fallbackLetter, modifier)
    }
}

@Composable
private fun FaviconFallback(letter: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(70.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToolResultCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun SuccessBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Success",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ErrorBadge(error: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun JsonObject?.str(key: String): String = this?.get(key)?.jsonPrimitive?.content ?: ""
private fun JsonObject?.int(key: String): Int = this?.get(key)?.jsonPrimitive?.content?.toIntOrNull() ?: 0

// ---------------------------------------------------------------------------
// Web Search — Level 1 renders scrollable list, no Level 2
// ---------------------------------------------------------------------------

@Composable
private fun WebSearchDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier) {
        action.sources.forEach { source ->
            SearchResultRow(
                source = source,
                onClick = { if (source.url.isNotEmpty()) uriHandler.openUri(source.url) },
            )
        }
        if (action.sources.isEmpty()) {
            Text(
                text = action.result ?: "No results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    source: SearchSource,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FaviconImage(
            url = source.faviconUrl,
            fallbackLetter = source.faviconLetter,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (source.url.isNotEmpty()) {
                Text(
                    text = runCatching {
                        java.net.URI(source.url).host ?: source.url
                    }.getOrElse { source.url },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (source.snippet.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = source.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fetch URL
// ---------------------------------------------------------------------------

@Composable
private fun FetchUrlDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val parsed = action.parsedResult

    Column(modifier = modifier) {
        val url = parsed.str("final_url").ifEmpty { action.parsedArguments.str("url") }
        val status = parsed.int("status")
        val contentType = parsed.str("content_type")
        val success = parsed.str("success") == "true"
        val error = parsed.str("error")
        val body = parsed.str("body")

        if (url.isNotEmpty()) {
            ToolResultCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaviconImage(
                        url = runCatching {
                            "https://www.google.com/s2/favicons?domain=${java.net.URI(url).host}&sz=32"
                        }.getOrElse { "" },
                        fallbackLetter = url.firstOrNull()?.uppercase() ?: "?",
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = runCatching { java.net.URI(url).host ?: url }.getOrElse { url },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (status > 0) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (success) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        ) {
                            Text(
                                text = "$status",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (success) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (contentType.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = contentType.substringBefore(";"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (error.isNotEmpty()) {
                ErrorBadge(error = error)
            }

            if (body.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = body.take(1000) + if (body.length > 1000) "\n\n\u2026" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    label = "Copy",
                    icon = Icons.Default.ContentCopy,
                    onClick = { clipboard.setText(AnnotatedString(url)) },
                )
                ActionButton(
                    label = "Open",
                    icon = Icons.Default.OpenInBrowser,
                    onClick = { uriHandler.openUri(url) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Open URL
// ---------------------------------------------------------------------------

@Composable
private fun OpenUrlDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val url = action.parsedArguments.str("url")
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FaviconImage(
                    url = runCatching {
                        "https://www.google.com/s2/favicons?domain=${java.net.URI(url).host}&sz=32"
                    }.getOrElse { "" },
                    fallbackLetter = url.firstOrNull()?.uppercase() ?: "?",
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = runCatching { java.net.URI(url).host ?: url }.getOrElse { url },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (success) {
            SuccessBadge()
        } else {
            ErrorBadge(error = action.parsedResult?.str("error") ?: "Failed to open URL")
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                label = "Copy link",
                icon = Icons.Default.ContentCopy,
                onClick = { clipboard.setText(AnnotatedString(url)) },
            )
            ActionButton(
                label = "Open link",
                icon = Icons.Default.OpenInBrowser,
                onClick = { uriHandler.openUri(url) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Set Alarm
// ---------------------------------------------------------------------------

@Composable
private fun SetAlarmDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedArguments
    val hour = parsed.int("hour")
    val minutes = parsed.int("minutes")
    val label = parsed.str("label")
    val durationSeconds = parsed.int("duration_seconds")
    val isTimer = durationSeconds > 0

    Column(modifier = modifier) {
        ToolResultCard {
            if (isTimer) {
                val hrs = durationSeconds / 3600
                val mins = (durationSeconds % 3600) / 60
                val secs = durationSeconds % 60
                val timeText = buildString {
                    if (hrs > 0) append("${hrs}h ")
                    if (mins > 0 || hrs > 0) append("${mins}m ")
                    append("${secs}s")
                }
                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Timer",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                val displayHour = when {
                    hour == 0 -> 12
                    hour > 12 -> hour - 12
                    else -> hour
                }
                val amPm = if (hour < 12) "AM" else "PM"
                val timeText = "$displayHour:${minutes.toString().padStart(2, '0')}"
                val periodText = amPm

                Icon(
                    Icons.Default.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Alarm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = periodText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            if (label.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val resultSuccess = action.parsedResult?.str("success") == "true"
        if (resultSuccess) {
            SuccessBadge()
        }
    }
}

// ---------------------------------------------------------------------------
// Calendar Event
// ---------------------------------------------------------------------------

@Composable
private fun CalendarEventDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedArguments
    val title = parsed.str("title")
    val startTime = parsed.str("start_time")
    val endTime = parsed.str("end_time")
    val location = parsed.str("location")
    val description = parsed.str("description")
    val allDay = parsed?.get("all_day")?.jsonPrimitive?.content == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title.ifEmpty { "Calendar Event" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(8.dp))

            if (startTime.isNotEmpty()) {
                InfoRow(label = "When", value = formatDateTime(startTime, endTime, allDay), icon = Icons.Default.Schedule)
            }
            if (location.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Where", value = location, icon = Icons.Default.LocationOn)
            }
            if (description.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Notes", value = description)
            }
        }

        Spacer(Modifier.height(8.dp))
        val success = action.parsedResult?.str("success") == "true"
        if (success) {
            SuccessBadge()
        }
    }
}

private fun formatDateTime(start: String, end: String, allDay: Boolean): String {
    val startDate = start.substringBefore("T").substringAfterLast("-").let {
        val parts = start.substringBefore("T").split("-")
        if (parts.size == 3) "${parts[0]}-${parts[1]}-${parts[2]}" else start.substringBefore("T")
    }
    val startTimePart = if (start.contains("T")) {
        val time = start.substringAfter("T").substringBefore("+").substringBefore("Z")
        val hour = time.substringBefore(":").toIntOrNull() ?: 0
        val min = time.substringAfter(":").substringBefore(":")
        val amPm = if (hour < 12) "AM" else "PM"
        val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
        " at $h12:$min $amPm"
    } else ""

    return if (end.isNotEmpty() && end != start) {
        val endTimePart = if (end.contains("T")) {
            val time = end.substringAfter("T").substringBefore("+").substringBefore("Z")
            val hour = time.substringBefore(":").toIntOrNull() ?: 0
            val min = time.substringAfter(":").substringBefore(":")
            val amPm = if (hour < 12) "AM" else "PM"
            val h12 = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            " \u2013 $h12:$min $amPm"
        } else ""
        "$startDate$startTimePart$endTimePart"
    } else {
        if (allDay) "$startDate (all day)" else "$startDate$startTimePart"
    }
}

// ---------------------------------------------------------------------------
// Send Notification
// ---------------------------------------------------------------------------

@Composable
private fun NotificationDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val title = action.parsedArguments.str("title")
    val message = action.parsedArguments.str("message")

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = title.ifEmpty { "Notification" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (action.parsedResult?.str("success") == "true") {
            SuccessBadge()
        } else {
            ErrorBadge(error = action.parsedResult?.str("error") ?: "Failed")
        }
    }
}

// ---------------------------------------------------------------------------
// Email: Check / Search (list)
// ---------------------------------------------------------------------------

@Composable
private fun EmailListDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val emails = parsed?.get("emails")?.jsonArray

    Column(modifier = modifier) {
        val count = emails?.size ?: 0
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        ) {
            Text(
                text = "$count ${if (count == 1) "email" else "emails"} found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        emails?.take(10)?.forEach { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val from = obj["from"]?.jsonPrimitive?.content ?: ""
            val subject = obj["subject"]?.jsonPrimitive?.content ?: ""
            val date = obj["date"]?.jsonPrimitive?.content ?: ""
            val preview = obj["preview"]?.jsonPrimitive?.content ?: ""

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = from.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = from.substringBefore("<").trim().ifEmpty { from },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (date.isNotEmpty()) {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (subject.isNotEmpty()) {
                            Text(
                                text = subject,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (preview.isNotEmpty()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Email: Read
// ---------------------------------------------------------------------------

@Composable
private fun EmailReadDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val from = parsed.str("from")
    val to = parsed.str("to")
    val subject = parsed.str("subject")
    val date = parsed.str("date")
    val body = parsed.str("body")

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = subject.ifEmpty { "(no subject)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (from.isNotEmpty()) InfoRow(label = "From", value = from)
            if (to.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "To", value = to)
            }
            if (date.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Date", value = date, icon = Icons.Default.Schedule)
            }
        }

        if (body.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = body.take(2000) + if (body.length > 2000) "\n\n\u2026" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Email: Compose / Reply (sent)
// ---------------------------------------------------------------------------

@Composable
private fun EmailSentDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedArguments
    val to = parsed.str("to")
    val subject = parsed.str("subject")
    val body = parsed.str("body")

    Column(modifier = modifier) {
        ToolResultCard {
            SuccessBadge()
            Spacer(Modifier.height(8.dp))
            if (to.isNotEmpty()) InfoRow(label = "To", value = to)
            if (subject.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Subject", value = subject)
            }
        }
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = body.take(1000) + if (body.length > 1000) "\n\n\u2026" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SMS: Check / Search (list)
// ---------------------------------------------------------------------------

@Composable
private fun SmsListDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val messages = parsed?.get("messages")?.jsonArray

    Column(modifier = modifier) {
        val count = messages?.size ?: 0
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        ) {
            Text(
                text = "$count ${if (count == 1) "message" else "messages"} found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        messages?.take(10)?.forEach { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val from = obj["from"]?.jsonPrimitive?.content ?: ""
            val date = obj["date"]?.jsonPrimitive?.content ?: ""
            val preview = obj["preview"]?.jsonPrimitive?.content ?: ""

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = from,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            if (date.isNotEmpty()) {
                                Text(
                                    text = date,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (preview.isNotEmpty()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// SMS: Read
// ---------------------------------------------------------------------------

@Composable
private fun SmsReadDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val from = parsed.str("from")
    val date = parsed.str("date")
    val body = parsed.str("body")

    Column(modifier = modifier) {
        ToolResultCard {
            if (from.isNotEmpty()) InfoRow(label = "From", value = from)
            if (date.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Date", value = date, icon = Icons.Default.Schedule)
            }
        }
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SMS: Send / Reply (draft)
// ---------------------------------------------------------------------------

@Composable
private fun SmsDraftDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedArguments
    val to = parsed.str("to")
    val body = parsed.str("body")

    Column(modifier = modifier) {
        ToolResultCard {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
            ) {
                Text(
                    text = "Draft — awaiting review",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (to.isNotEmpty()) InfoRow(label = "To", value = to)
            if (body.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shell Command
// ---------------------------------------------------------------------------

@Composable
private fun ShellCommandDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val command = action.parsedArguments.str("command").ifEmpty {
        action.parsedArguments.str("cmd")
    }
    val parsed = action.parsedResult
    val output = parsed.str("output").ifEmpty { parsed.str("stdout") }
    val error = parsed.str("error").ifEmpty { parsed.str("stderr") }
    val success = parsed.str("success") == "true"
    val exitCode = parsed.int("exit_code")

    Column(modifier = modifier) {
        if (command.isNotEmpty()) {
            ToolResultCard {
                Text(
                    text = "Command",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (output.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ToolResultCard {
                Text(
                    text = "Output",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = output.take(2000) + if (output.length > 2000) "\n\n\u2026" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (success) {
            SuccessBadge()
        } else {
            ErrorBadge(error = error.ifEmpty { "Exit code: $exitCode" })
        }
    }
}

// ---------------------------------------------------------------------------
// Read File
// ---------------------------------------------------------------------------

@Composable
private fun FileReadDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path").ifEmpty { action.parsedArguments.str("file_path") }
    val filename = path.substringAfterLast("/")
    val parsed = action.parsedResult
    val content = parsed.str("content")
    val lineCount = parsed.int("line_count")

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = filename.ifEmpty { "File" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (path.isNotEmpty() && path != filename) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (lineCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$lineCount lines",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (content.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = content.take(2000) + if (content.length > 2000) "\n\n\u2026" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Edit File
// ---------------------------------------------------------------------------

@Composable
private fun FileEditDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path").ifEmpty { action.parsedArguments.str("file_path") }
    val filename = path.substringAfterLast("/")
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = filename.ifEmpty { "File" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (path.isNotEmpty() && path != filename) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (success) {
            SuccessBadge()
        } else {
            ErrorBadge(error = action.parsedResult?.str("error") ?: "Edit failed")
        }
    }
}

// ---------------------------------------------------------------------------
// Schedule Task
// ---------------------------------------------------------------------------

@Composable
private fun ScheduleTaskDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedArguments
    val description = parsed.str("description")
    val executeAt = parsed.str("execute_at")
    val cron = parsed.str("cron")
    val onHeartbeat = parsed?.get("on_heartbeat")?.jsonPrimitive?.content == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            val triggerType = when {
                onHeartbeat -> "Heartbeat"
                cron.isNotEmpty() -> "Recurring"
                else -> "One-off"
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
            ) {
                Text(
                    text = triggerType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description.ifEmpty { "Scheduled task" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (executeAt.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "When", value = executeAt, icon = Icons.Default.Schedule)
            }
            if (cron.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Cron", value = cron)
            }
        }

        Spacer(Modifier.height(8.dp))
        if (action.parsedResult?.str("success") == "true") {
            SuccessBadge()
        }
    }
}

// ---------------------------------------------------------------------------
// Cancel / List Tasks
// ---------------------------------------------------------------------------

@Composable
private fun TaskManagementDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val success = parsed.str("success") == "true"
    val tasks = parsed?.get("tasks")?.jsonArray
    val count = parsed?.get("count")?.jsonPrimitive?.content?.toIntOrNull() ?: tasks?.size ?: 0

    Column(modifier = modifier) {
        if (tasks != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            ) {
                Text(
                    text = "$count ${if (count == 1) "task" else "tasks"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            tasks.take(10).forEach { element ->
                val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val id = obj["id"]?.jsonPrimitive?.content ?: ""
                val desc = obj["description"]?.jsonPrimitive?.content ?: ""
                val trigger = obj["trigger"]?.jsonPrimitive?.content ?: ""
                val status = obj["status"]?.jsonPrimitive?.content ?: ""

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = desc.ifEmpty { id },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (trigger.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    Text(
                                        text = trigger,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (status.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (status == "PENDING")
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                ) {
                                    Text(
                                        text = status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (status == "PENDING")
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        } else {
            val taskId = parsed?.get("task_id")?.jsonPrimitive?.content ?: ""
            val removed = parsed?.get("status")?.jsonPrimitive?.content == "REMOVED"
            ToolResultCard {
                if (taskId.isNotEmpty()) {
                    InfoRow(label = "Task ID", value = taskId)
                }
                if (removed) {
                    Spacer(Modifier.height(4.dp))
                    SuccessBadge()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Memory / Learning
// ---------------------------------------------------------------------------

@Composable
private fun MemoryDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedArguments
    val key = parsed.str("key")
    val content = parsed.str("content")
    val category = parsed.str("category")

    Column(modifier = modifier) {
        ToolResultCard {
            if (key.isNotEmpty()) {
                Text(
                    text = key,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (category.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (content.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        if (action.parsedResult?.str("success") == "true") {
            SuccessBadge()
        }
    }
}

// ---------------------------------------------------------------------------
// Setup Email
// ---------------------------------------------------------------------------

@Composable
private fun EmailSetupDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val email = parsed.str("email")
    val imapHost = parsed.str("imap_host")
    val smtpHost = parsed.str("smtp_host")
    val success = parsed.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            if (email.isNotEmpty()) InfoRow(label = "Email", value = email)
            if (imapHost.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "IMAP", value = imapHost)
            }
            if (smtpHost.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "SMTP", value = smtpHost)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (success) {
            SuccessBadge()
        } else {
            ErrorBadge(error = parsed.str("error"))
        }
    }
}

// ---------------------------------------------------------------------------
// Open File
// ---------------------------------------------------------------------------

@Composable
private fun OpenFileDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path").ifEmpty { action.parsedArguments.str("file_path") }
    val filename = path.substringAfterLast("/")
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = filename.ifEmpty { "File" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (path.isNotEmpty() && path != filename) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = action.parsedResult?.str("error") ?: "Failed")
    }
}

// ---------------------------------------------------------------------------
// Manage Process
// ---------------------------------------------------------------------------

@Composable
private fun ManageProcessDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val actionType = action.parsedArguments.str("action")
    val parsed = action.parsedResult

    Column(modifier = modifier) {
        ToolResultCard {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Text(
                    text = actionType.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            if (actionType == "list") {
                val processes = parsed?.get("processes")?.jsonArray
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${processes?.size ?: 0} process(es)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                processes?.take(5)?.forEach { el ->
                    val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val pid = obj["pid"]?.jsonPrimitive?.content ?: ""
                    val cmd = obj["command"]?.jsonPrimitive?.content ?: ""
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "PID $pid: ${cmd.take(60)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (actionType == "log") {
                val output = parsed.str("output")
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        text = output.take(1000) + if (output.length > 1000) "\n\n\u2026" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (parsed.str("success") == "true") SuccessBadge()
        else ErrorBadge(error = parsed.str("error"))
    }
}

// ---------------------------------------------------------------------------
// Notification: Check / Search (list)
// ---------------------------------------------------------------------------

@Composable
private fun NotificationListDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val notifications = parsed?.get("notifications")?.jsonArray

    Column(modifier = modifier) {
        val count = notifications?.size ?: 0
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        ) {
            Text(
                text = "$count ${if (count == 1) "notification" else "notifications"} found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        notifications?.take(10)?.forEach { element ->
            val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val appLabel = obj["app_label"]?.jsonPrimitive?.content ?: ""
            val title = obj["title"]?.jsonPrimitive?.content ?: ""
            val preview = obj["preview"]?.jsonPrimitive?.content ?: ""

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        if (appLabel.isNotEmpty()) {
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (title.isNotEmpty()) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (preview.isNotEmpty()) {
                            Text(
                                text = preview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Notification: Read
// ---------------------------------------------------------------------------

@Composable
private fun NotificationReadDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val appLabel = parsed.str("app_label")
    val title = parsed.str("title")
    val text = parsed.str("text")
    val subtext = parsed.str("subtext")

    Column(modifier = modifier) {
        ToolResultCard {
            if (appLabel.isNotEmpty()) {
                Text(
                    text = appLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = title.ifEmpty { "Notification" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (subtext.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SSH: Connect
// ---------------------------------------------------------------------------

@Composable
private fun SshConnectDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedArguments
    val host = parsed.str("host")
    val username = parsed.str("username")
    val port = parsed.int("port").let { if (it > 0) it else 22 }
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            if (host.isNotEmpty()) InfoRow(label = "Host", value = host)
            if (username.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "User", value = username)
            }
            Spacer(Modifier.height(4.dp))
            InfoRow(label = "Port", value = "$port")
        }
        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = action.parsedResult?.str("error") ?: "Connection failed")
    }
}

// ---------------------------------------------------------------------------
// SSH: Disconnect
// ---------------------------------------------------------------------------

@Composable
private fun SshDisconnectDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val success = action.parsedResult?.str("success") == "true"
    Column(modifier = modifier) {
        ToolResultCard {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Disconnected",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = action.parsedResult?.str("error") ?: "Failed")
    }
}

// ---------------------------------------------------------------------------
// SSH: Execute Command
// ---------------------------------------------------------------------------

@Composable
private fun SshCommandDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val command = action.parsedArguments.str("command")
    val parsed = action.parsedResult
    val stdout = parsed.str("stdout")
    val stderr = parsed.str("stderr")
    val exitCode = parsed.int("exit_code")
    val success = parsed.str("success") == "true"

    Column(modifier = modifier) {
        if (command.isNotEmpty()) {
            ToolResultCard {
                Text(
                    text = "Command",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = command,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (stdout.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ToolResultCard {
                Text(
                    text = "Output",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = stdout.take(2000) + if (stdout.length > 2000) "\n\n\u2026" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = stderr.ifEmpty { "Exit code: $exitCode" })
    }
}

// ---------------------------------------------------------------------------
// SSH: Status
// ---------------------------------------------------------------------------

@Composable
private fun SshStatusDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val active = parsed?.get("active_connections")?.jsonArray
    val saved = parsed?.get("saved_servers")?.jsonArray

    Column(modifier = modifier) {
        if (active != null) {
            Text(
                text = "Active connections (${active.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            active.forEach { el ->
                val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val host = obj["host"]?.jsonPrimitive?.content ?: ""
                val user = obj["username"]?.jsonPrimitive?.content ?: ""

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name.ifEmpty { host },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "$user@$host",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        if (saved != null && saved.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Saved servers (${saved.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            saved.forEach { el ->
                val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val host = obj["host"]?.jsonPrimitive?.content ?: ""
                val connected = obj["connected"]?.jsonPrimitive?.content == "true"

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    CircleShape,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "$name ($host)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SSH: Transfer File
// ---------------------------------------------------------------------------

@Composable
private fun SshTransferDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val direction = action.parsedArguments.str("direction")
    val localPath = action.parsedArguments.str("local_path")
    val remotePath = action.parsedArguments.str("remote_path")
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
            ) {
                Text(
                    text = direction.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            InfoRow(label = "Local", value = localPath)
            Spacer(Modifier.height(4.dp))
            InfoRow(label = "Remote", value = remotePath)
        }
        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = action.parsedResult?.str("error") ?: "Transfer failed")
    }
}

// ---------------------------------------------------------------------------
// SSH: Read File
// ---------------------------------------------------------------------------

@Composable
private fun SshReadFileDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path")
    val filename = path.substringAfterLast("/")
    val parsed = action.parsedResult
    val content = parsed.str("content")
    val totalLines = parsed.int("total_lines")
    val offset = parsed.int("offset")

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = filename.ifEmpty { "Remote file" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (path.isNotEmpty() && path != filename) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (totalLines > 0) {
                Spacer(Modifier.height(4.dp))
                val rangeText = if (offset > 1) "lines $offset\u2013${(offset + content.lines().size - 1)} of $totalLines" else "$totalLines lines"
                Text(
                    text = rangeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (content.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    text = content.take(2000) + if (content.length > 2000) "\n\n\u2026" else "",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SSH: Write / Edit File
// ---------------------------------------------------------------------------

@Composable
private fun SshWriteFileDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path")
    val filename = path.substringAfterLast("/")
    val isEdit = action.name == "ssh_edit_file"
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = filename.ifEmpty { "Remote file" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (path.isNotEmpty() && path != filename) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isEdit) {
                Spacer(Modifier.height(4.dp))
                val oldStr = action.parsedArguments.str("old_string")
                val newStr = action.parsedArguments.str("new_string")
                if (oldStr.isNotEmpty()) {
                    Text(
                        text = "Replaced:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(
                            text = oldStr.take(80) + if (oldStr.length > 80) "\u2026" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        text = "With:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(
                            text = newStr.take(80) + if (newStr.length > 80) "\u2026" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = action.parsedResult?.str("error") ?: "Failed")
    }
}

// ---------------------------------------------------------------------------
// SSH: List Directory
// ---------------------------------------------------------------------------

@Composable
private fun SshListDirDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path")
    val parsed = action.parsedResult
    val entries = parsed?.get("entries")?.jsonArray

    Column(modifier = modifier) {
        ToolResultCard {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            if (path.isNotEmpty()) InfoRow(label = "Path", value = path)
        }

        if (entries != null && entries.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            entries.take(20).forEach { el ->
                val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
                val name = obj["name"]?.jsonPrimitive?.content ?: ""
                val type = obj["type"]?.jsonPrimitive?.content ?: ""
                val size = obj["size"]?.jsonPrimitive?.content ?: ""

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (type == "directory") Icons.Default.FolderOpen else Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (type == "directory") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (size.isNotEmpty() && size != "0") {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SSH: Delete Path
// ---------------------------------------------------------------------------

@Composable
private fun SshDeleteDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path")
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            if (path.isNotEmpty()) InfoRow(label = "Deleted", value = path)
        }
        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = action.parsedResult?.str("error") ?: "Failed")
    }
}

// ---------------------------------------------------------------------------
// SSH: Make Directory
// ---------------------------------------------------------------------------

@Composable
private fun SshMkdirDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path")
    val success = action.parsedResult?.str("success") == "true"

    Column(modifier = modifier) {
        ToolResultCard {
            Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            if (path.isNotEmpty()) InfoRow(label = "Created", value = path)
        }
        Spacer(Modifier.height(8.dp))
        if (success) SuccessBadge()
        else ErrorBadge(error = action.parsedResult?.str("error") ?: "Failed")
    }
}

// ---------------------------------------------------------------------------
// SSH: File Info
// ---------------------------------------------------------------------------

@Composable
private fun SshFileInfoDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val path = action.parsedArguments.str("path")
    val parsed = action.parsedResult
    val type = parsed.str("type")
    val size = parsed.str("size")
    val permissions = parsed.str("permissions")
    val owner = parsed.str("owner")

    Column(modifier = modifier) {
        ToolResultCard {
            Text(
                text = path.substringAfterLast("/").ifEmpty { path },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (type.isNotEmpty()) InfoRow(label = "Type", value = type)
            if (size.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Size", value = size)
            }
            if (permissions.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Perms", value = permissions)
            }
            if (owner.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                InfoRow(label = "Owner", value = owner)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// SSH: Search Files / Grep
// ---------------------------------------------------------------------------

@Composable
private fun SshSearchDetail(action: ToolAction, modifier: Modifier = Modifier) {
    val parsed = action.parsedResult
    val matches = parsed?.get("matches")?.jsonArray
    val count = parsed?.get("count")?.jsonPrimitive?.content?.toIntOrNull() ?: matches?.size ?: 0

    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        ) {
            Text(
                text = "$count ${if (count == 1) "match" else "matches"} found",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        matches?.take(15)?.forEach { el ->
            val obj = el as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val path = obj["path"]?.jsonPrimitive?.content ?: ""
            val line = obj["line"]?.jsonPrimitive?.content ?: ""
            val content = obj["content"]?.jsonPrimitive?.content ?: obj["match"]?.jsonPrimitive?.content ?: ""

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (path.isNotEmpty()) {
                        Text(
                            text = path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (content.isNotEmpty()) {
                        SelectionContainer {
                            Text(
                                text = content.take(200),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
