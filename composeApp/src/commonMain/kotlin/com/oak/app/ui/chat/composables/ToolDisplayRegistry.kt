package com.oak.app.ui.chat.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Provides display metadata for tool calls: human-readable names, icons, and visibility flags.
 */
internal object ToolDisplayRegistry {

    data class ToolDisplayInfo(
        val displayName: String,
        val icon: ImageVector,
        val isHidden: Boolean = false,
        val isSearchTool: Boolean = false,
        val isFileCreationTool: Boolean = false,
    )

    private val registry = mapOf(
        // Web / search
        "web_search" to ToolDisplayInfo(
            displayName = "Searched the web",
            icon = Icons.Default.Search,
            isSearchTool = true,
        ),
        "fetch_url" to ToolDisplayInfo(
            displayName = "Fetched URL",
            icon = Icons.Default.OpenInBrowser,
            isSearchTool = true,
        ),
        "open_url" to ToolDisplayInfo(
            displayName = "Opened URL",
            icon = Icons.Default.OpenInBrowser,
        ),

        // File operations
        "read_file" to ToolDisplayInfo(
            displayName = "Read file",
            icon = Icons.Default.Description,
        ),
        "edit_file" to ToolDisplayInfo(
            displayName = "Edited file",
            icon = Icons.Default.Edit,
            isFileCreationTool = true,
        ),
        "open_file" to ToolDisplayInfo(
            displayName = "Opened file",
            icon = Icons.Default.FolderOpen,
        ),

        // Shell
        "execute_shell_command" to ToolDisplayInfo(
            displayName = "Ran command",
            icon = Icons.Default.Code,
        ),
        "manage_process" to ToolDisplayInfo(
            displayName = "Managed process",
            icon = Icons.Default.Code,
        ),

        // Memory
        "memory_store" to ToolDisplayInfo(
            displayName = "Saved memory",
            icon = Icons.Default.Memory,
        ),
        "memory_forget" to ToolDisplayInfo(
            displayName = "Forgot memory",
            icon = Icons.Default.Memory,
        ),
        "memory_learn" to ToolDisplayInfo(
            displayName = "Stored learning",
            icon = Icons.Default.Memory,
        ),
        "memory_reinforce" to ToolDisplayInfo(
            displayName = "Reinforced memory",
            icon = Icons.Default.Memory,
        ),
        "promote_learning" to ToolDisplayInfo(
            displayName = "Promoted learning",
            icon = Icons.Default.Memory,
        ),

        // Scheduling
        "schedule_task" to ToolDisplayInfo(
            displayName = "Scheduled task",
            icon = Icons.Default.Schedule,
        ),
        "cancel_task" to ToolDisplayInfo(
            displayName = "Cancelled task",
            icon = Icons.Default.ErrorOutline,
        ),
        "list_tasks" to ToolDisplayInfo(
            displayName = "Listed tasks",
            icon = Icons.AutoMirrored.Filled.List,
        ),

        // Email
        "check_email" to ToolDisplayInfo(
            displayName = "Checked email",
            icon = Icons.Default.Email,
        ),
        "read_email" to ToolDisplayInfo(
            displayName = "Read email",
            icon = Icons.Default.Email,
        ),
        "search_email" to ToolDisplayInfo(
            displayName = "Searched email",
            icon = Icons.Default.Search,
        ),
        "compose_email" to ToolDisplayInfo(
            displayName = "Composed email",
            icon = Icons.Default.Email,
            isFileCreationTool = true,
        ),
        "reply_email" to ToolDisplayInfo(
            displayName = "Replied to email",
            icon = Icons.Default.Email,
        ),
        "setup_email" to ToolDisplayInfo(
            displayName = "Set up email",
            icon = Icons.Default.Email,
        ),

        // SMS
        "check_sms" to ToolDisplayInfo(
            displayName = "Checked SMS",
            icon = Icons.Default.Sms,
        ),
        "read_sms" to ToolDisplayInfo(
            displayName = "Read SMS",
            icon = Icons.Default.Sms,
        ),
        "search_sms" to ToolDisplayInfo(
            displayName = "Searched SMS",
            icon = Icons.Default.Search,
        ),
        "send_sms" to ToolDisplayInfo(
            displayName = "Drafted SMS",
            icon = Icons.Default.Sms,
        ),
        "reply_sms" to ToolDisplayInfo(
            displayName = "Drafted SMS reply",
            icon = Icons.Default.Sms,
        ),

        // Calendar / alarms
        "create_calendar_event" to ToolDisplayInfo(
            displayName = "Created calendar event",
            icon = Icons.Default.CalendarMonth,
        ),
        "set_alarm" to ToolDisplayInfo(
            displayName = "Set alarm",
            icon = Icons.Default.Alarm,
        ),

        // Notifications
        "check_notifications" to ToolDisplayInfo(
            displayName = "Checked notifications",
            icon = Icons.Default.Notifications,
        ),
        "read_notification" to ToolDisplayInfo(
            displayName = "Read notification",
            icon = Icons.Default.Notifications,
        ),
        "search_notifications" to ToolDisplayInfo(
            displayName = "Searched notifications",
            icon = Icons.Default.Search,
        ),
        "send_notification" to ToolDisplayInfo(
            displayName = "Sent notification",
            icon = Icons.Default.Notifications,
        ),

        // SSH
        "ssh_connect" to ToolDisplayInfo(
            displayName = "Connected to server",
            icon = Icons.Default.Cloud,
        ),
        "ssh_disconnect" to ToolDisplayInfo(
            displayName = "Disconnected from server",
            icon = Icons.Default.Cloud,
        ),
        "ssh_execute_command" to ToolDisplayInfo(
            displayName = "Ran remote command",
            icon = Icons.Default.Code,
        ),
        "ssh_status" to ToolDisplayInfo(
            displayName = "Checked connection",
            icon = Icons.Default.Cloud,
        ),
        "ssh_transfer_file" to ToolDisplayInfo(
            displayName = "Transferred file",
            icon = Icons.Default.SwapHoriz,
        ),
        "ssh_read_file" to ToolDisplayInfo(
            displayName = "Read remote file",
            icon = Icons.Default.Description,
        ),
        "ssh_write_file" to ToolDisplayInfo(
            displayName = "Wrote remote file",
            icon = Icons.Default.Edit,
        ),
        "ssh_edit_file" to ToolDisplayInfo(
            displayName = "Edited remote file",
            icon = Icons.Default.Edit,
        ),
        "ssh_list_directory" to ToolDisplayInfo(
            displayName = "Listed directory",
            icon = Icons.Default.FolderOpen,
        ),
        "ssh_delete_path" to ToolDisplayInfo(
            displayName = "Deleted remote path",
            icon = Icons.Default.Delete,
        ),
        "ssh_make_directory" to ToolDisplayInfo(
            displayName = "Created directory",
            icon = Icons.Default.FolderOpen,
        ),
        "ssh_file_info" to ToolDisplayInfo(
            displayName = "Checked file info",
            icon = Icons.Default.Info,
        ),
        "ssh_search_files" to ToolDisplayInfo(
            displayName = "Searched files",
            icon = Icons.Default.Search,
        ),
        "ssh_grep" to ToolDisplayInfo(
            displayName = "Searched file contents",
            icon = Icons.Default.Search,
        ),

        // Hidden tools (internal / noise)
        "get_local_time" to ToolDisplayInfo(
            displayName = "",
            icon = Icons.Default.Timer,
            isHidden = true,
        ),
        "get_location_from_ip" to ToolDisplayInfo(
            displayName = "",
            icon = Icons.Default.Build,
            isHidden = true,
        ),
        "wait" to ToolDisplayInfo(
            displayName = "",
            icon = Icons.Default.Timer,
            isHidden = true,
        ),
        "compress_context" to ToolDisplayInfo(
            displayName = "",
            icon = Icons.Default.Build,
            isHidden = true,
        ),
        "ask_questions" to ToolDisplayInfo(
            displayName = "",
            icon = Icons.Default.Build,
            isHidden = true,
        ),
    )

    /** Default info for unknown / MCP tools. */
    private val defaultInfo = ToolDisplayInfo(
        displayName = "",
        icon = Icons.Default.Build,
    )

    fun lookup(toolName: String): ToolDisplayInfo {
        if (isMcpTool(toolName)) {
            return ToolDisplayInfo(
                displayName = "Used ${mcpServerName(toolName)}",
                icon = Icons.Default.Extension,
            )
        }
        return registry[toolName] ?: defaultInfo.copy(
            displayName = humanizeToolName(toolName),
        )
    }

    fun displayName(toolName: String): String {
        val info = lookup(toolName)
        return info.displayName.ifEmpty { humanizeToolName(toolName) }
    }

    fun displayIcon(toolName: String): ImageVector = lookup(toolName).icon

    fun isHiddenTool(toolName: String): Boolean = lookup(toolName).isHidden

    fun isSearchTool(toolName: String): Boolean = lookup(toolName).isSearchTool

    fun isFileCreationTool(toolName: String): Boolean = lookup(toolName).isFileCreationTool

    fun isMcpTool(toolName: String): Boolean = toolName.startsWith("mcp_")

    fun mcpServerName(toolId: String): String {
        val withoutPrefix = toolId.removePrefix("mcp_")
        val parts = withoutPrefix.split("_", limit = 2)
        return parts.firstOrNull()
            ?.replace("_", " ")
            ?.replaceFirstChar { it.uppercase() }
            ?: "MCP"
    }

    fun humanizeToolName(toolName: String): String =
        toolName.replace("_", " ").replaceFirstChar { it.uppercase() }
}
