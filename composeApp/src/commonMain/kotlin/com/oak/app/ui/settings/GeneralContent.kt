@file:OptIn(ExperimentalMaterial3Api::class)

package com.oak.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oak.app.BackIcon
import com.oak.app.TerminalLine
import com.oak.app.Version
import com.oak.app.data.EmailAccount
import com.oak.app.data.HeartbeatLogEntry
import com.oak.app.data.ImportSection
import com.oak.app.data.MemoryEntry
import com.oak.app.data.ScheduledTask
import com.oak.app.data.Service
import com.oak.app.data.SharedJson
import com.oak.app.data.TaskTrigger
import com.oak.app.data.ThemeMode
import com.oak.app.data.detectImportSections
import com.oak.app.formatFileSize
import com.oak.app.inference.DevicePerformance
import com.oak.app.inference.DownloadError
import com.oak.app.inference.LocalModel
import com.oak.app.inference.calculateDevicePerformance
import com.oak.app.inference.estimateGpuMemoryMb
import com.oak.app.mcp.PopularMcpServer
import com.oak.app.network.tools.ToolInfo
import com.oak.app.saveFileToDevice
import com.oak.app.tools.SetupStoragePermissionHandler
import com.oak.app.ui.OakClearableTextField
import com.oak.app.ui.OakOutlinedTextField
import com.oak.app.ui.components.OakSlider
import com.oak.app.ui.components.SettingsListItem
import com.oak.app.ui.components.VerticalScrollbarForScroll
import com.oak.app.ui.handCursor
import com.oak.app.ui.oakAdaptiveCardBorder
import com.oak.app.ui.oakAdaptiveCardColors
import com.oak.app.ui.oakAdaptiveCardSurface
import com.oak.app.ui.sandbox.SandboxProgressRow
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonObject
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.default_soul
import oak.composeapp.generated.resources.device_storage_description_disabled
import oak.composeapp.generated.resources.device_storage_description_enabled_denied
import oak.composeapp.generated.resources.device_storage_description_enabled_granted
import oak.composeapp.generated.resources.device_storage_title
import oak.composeapp.generated.resources.github_mark
import oak.composeapp.generated.resources.litert_cancel
import oak.composeapp.generated.resources.litert_context_size
import oak.composeapp.generated.resources.litert_download
import oak.composeapp.generated.resources.litert_error_download_incomplete
import oak.composeapp.generated.resources.litert_error_network
import oak.composeapp.generated.resources.litert_error_not_enough_disk_space
import oak.composeapp.generated.resources.litert_free_space
import oak.composeapp.generated.resources.litert_on_device_description
import oak.composeapp.generated.resources.litert_performance_good
import oak.composeapp.generated.resources.litert_performance_ok
import oak.composeapp.generated.resources.litert_performance_poor
import oak.composeapp.generated.resources.litert_recommended
import oak.composeapp.generated.resources.litert_tool_support
import oak.composeapp.generated.resources.settings_add_service
import oak.composeapp.generated.resources.settings_ai_font_family
import oak.composeapp.generated.resources.settings_ai_font_family_description
import oak.composeapp.generated.resources.settings_ai_mistakes_warning
import oak.composeapp.generated.resources.settings_api_key_label
import oak.composeapp.generated.resources.settings_api_key_optional_label
import oak.composeapp.generated.resources.settings_base_url_label
import oak.composeapp.generated.resources.settings_daemon_mode
import oak.composeapp.generated.resources.settings_daemon_mode_description
import oak.composeapp.generated.resources.settings_documentation
import oak.composeapp.generated.resources.settings_dynamic_ui
import oak.composeapp.generated.resources.settings_dynamic_ui_description
import oak.composeapp.generated.resources.settings_export
import oak.composeapp.generated.resources.settings_export_import_description
import oak.composeapp.generated.resources.settings_export_import_title
import oak.composeapp.generated.resources.settings_export_preview_title
import oak.composeapp.generated.resources.settings_free_ai_access
import oak.composeapp.generated.resources.settings_free_ai_access_url
import oak.composeapp.generated.resources.settings_heartbeat_recent
import oak.composeapp.generated.resources.settings_import
import oak.composeapp.generated.resources.settings_import_error
import oak.composeapp.generated.resources.settings_import_partial
import oak.composeapp.generated.resources.settings_import_preview_title
import oak.composeapp.generated.resources.settings_import_replace_all
import oak.composeapp.generated.resources.settings_import_replace_all_description
import oak.composeapp.generated.resources.settings_import_section_conversations
import oak.composeapp.generated.resources.settings_import_section_email
import oak.composeapp.generated.resources.settings_import_section_heartbeat
import oak.composeapp.generated.resources.settings_import_section_mcp
import oak.composeapp.generated.resources.settings_import_section_memory
import oak.composeapp.generated.resources.settings_import_section_scheduling
import oak.composeapp.generated.resources.settings_import_section_services
import oak.composeapp.generated.resources.settings_import_section_soul
import oak.composeapp.generated.resources.settings_import_section_ssh
import oak.composeapp.generated.resources.settings_import_section_tools
import oak.composeapp.generated.resources.settings_import_success
import oak.composeapp.generated.resources.settings_mcp_cancel
import oak.composeapp.generated.resources.settings_memories
import oak.composeapp.generated.resources.settings_memories_all_title
import oak.composeapp.generated.resources.settings_memories_delete
import oak.composeapp.generated.resources.settings_memories_description
import oak.composeapp.generated.resources.settings_memories_edit_cancel
import oak.composeapp.generated.resources.settings_memories_edit_save
import oak.composeapp.generated.resources.settings_memories_edit_title
import oak.composeapp.generated.resources.settings_memories_show_all
import oak.composeapp.generated.resources.settings_open_github_issue
import oak.composeapp.generated.resources.settings_openai_compatible_or_other_service
import oak.composeapp.generated.resources.settings_openai_compatible_providers
import oak.composeapp.generated.resources.settings_openai_compatible_setup_ollama
import oak.composeapp.generated.resources.settings_remove_service
import oak.composeapp.generated.resources.settings_reorder_content_description
import oak.composeapp.generated.resources.settings_request_integration_description
import oak.composeapp.generated.resources.settings_request_integration_title
import oak.composeapp.generated.resources.settings_sandbox_cancel
import oak.composeapp.generated.resources.settings_sandbox_description
import oak.composeapp.generated.resources.settings_sandbox_disk_usage
import oak.composeapp.generated.resources.settings_sandbox_install
import oak.composeapp.generated.resources.settings_sandbox_install_packages
import oak.composeapp.generated.resources.settings_sandbox_subtab_files
import oak.composeapp.generated.resources.settings_sandbox_subtab_packages
import oak.composeapp.generated.resources.settings_sandbox_subtab_terminal
import oak.composeapp.generated.resources.settings_sandbox_uninstall
import oak.composeapp.generated.resources.settings_sandbox_uninstall_confirm
import oak.composeapp.generated.resources.settings_scheduled_tasks
import oak.composeapp.generated.resources.settings_scheduled_tasks_cancel
import oak.composeapp.generated.resources.settings_scheduled_tasks_description
import oak.composeapp.generated.resources.settings_sign_in_copy_api_key_from
import oak.composeapp.generated.resources.settings_sms
import oak.composeapp.generated.resources.settings_soul
import oak.composeapp.generated.resources.settings_soul_description
import oak.composeapp.generated.resources.settings_soul_reset
import oak.composeapp.generated.resources.settings_soul_reset_cancel
import oak.composeapp.generated.resources.settings_soul_reset_confirm
import oak.composeapp.generated.resources.settings_soul_save
import oak.composeapp.generated.resources.settings_status_checking
import oak.composeapp.generated.resources.settings_status_connected
import oak.composeapp.generated.resources.settings_status_error
import oak.composeapp.generated.resources.settings_status_error_connection_failed
import oak.composeapp.generated.resources.settings_status_error_invalid_key
import oak.composeapp.generated.resources.settings_status_error_quota_exhausted
import oak.composeapp.generated.resources.settings_status_error_rate_limited
import oak.composeapp.generated.resources.settings_streaming_description
import oak.composeapp.generated.resources.settings_streaming_enabled
import oak.composeapp.generated.resources.settings_tab_agent
import oak.composeapp.generated.resources.settings_tab_general
import oak.composeapp.generated.resources.settings_tab_integrations
import oak.composeapp.generated.resources.settings_tab_sandbox
import oak.composeapp.generated.resources.settings_tab_services
import oak.composeapp.generated.resources.settings_tab_tools
import oak.composeapp.generated.resources.settings_task_details_consecutive_failures
import oak.composeapp.generated.resources.settings_task_details_created
import oak.composeapp.generated.resources.settings_task_details_last_result
import oak.composeapp.generated.resources.settings_task_details_next_run
import oak.composeapp.generated.resources.settings_task_details_no_heartbeat_runs
import oak.composeapp.generated.resources.settings_task_details_no_runs
import oak.composeapp.generated.resources.settings_task_details_on_every_heartbeat
import oak.composeapp.generated.resources.settings_task_details_schedule
import oak.composeapp.generated.resources.settings_task_details_scheduled_for
import oak.composeapp.generated.resources.settings_task_details_status
import oak.composeapp.generated.resources.settings_task_details_trigger
import oak.composeapp.generated.resources.settings_theme
import oak.composeapp.generated.resources.settings_theme_dark
import oak.composeapp.generated.resources.settings_theme_description
import oak.composeapp.generated.resources.settings_theme_light
import oak.composeapp.generated.resources.settings_theme_oled
import oak.composeapp.generated.resources.settings_theme_system
import oak.composeapp.generated.resources.settings_tools_description
import oak.composeapp.generated.resources.settings_tools_none_available
import oak.composeapp.generated.resources.settings_ui_scale
import oak.composeapp.generated.resources.settings_unlimited_tool_calls
import oak.composeapp.generated.resources.settings_unlimited_tool_calls_description
import oak.composeapp.generated.resources.settings_version
import oak.composeapp.generated.resources.snackbar_email_removed
import oak.composeapp.generated.resources.snackbar_mcp_server_removed
import oak.composeapp.generated.resources.snackbar_memory_deleted
import oak.composeapp.generated.resources.snackbar_service_removed
import oak.composeapp.generated.resources.snackbar_ssh_server_removed
import oak.composeapp.generated.resources.snackbar_task_cancelled
import oak.composeapp.generated.resources.snackbar_undo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableColumn
import kotlin.math.roundToInt
import kotlin.time.Instant

@Composable
internal fun GeneralContent(uiState: SettingsUiState, actions: SettingsActions) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useStaggered = maxWidth >= 600.dp
        if (useStaggered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.showDaemonToggle) {
                        SettingsCard {
                            DaemonModeToggle(
                                isDaemonEnabled = uiState.isDaemonEnabled,
                                onToggleDaemon = actions.onToggleDaemon,
                            )
                        }
                    }
                    SettingsCard {
                        DynamicUiToggle(
                            isDynamicUiEnabled = uiState.isDynamicUiEnabled,
                            onToggleDynamicUi = actions.onToggleDynamicUi,
                        )
                    }
                    SettingsCard {
                        ThemeModePicker(
                            themeMode = uiState.themeMode,
                            onChangeThemeMode = actions.onChangeThemeMode,
                        )
                        if (uiState.showDynamicColorsToggle) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            DynamicColorsToggle(
                                useDynamicColors = uiState.useDynamicColors,
                                onToggleDynamicColors = actions.onToggleDynamicColors,
                            )
                        }
                    }
                    SettingsCard {
                        FontFamilyPicker(
                            selectedFontFamily = uiState.fontFamily,
                            onChangeFontFamily = actions.onChangeFontFamily,
                        )
                    }
                    SettingsCard {
                        FontFamilyPicker(
                            selectedFontFamily = uiState.aiFontFamily,
                            onChangeFontFamily = actions.onChangeAiFontFamily,
                            title = stringResource(Res.string.settings_ai_font_family),
                            description = stringResource(Res.string.settings_ai_font_family_description),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (uiState.showUiScale) {
                        SettingsCard {
                            UiScaleSection(
                                uiScale = uiState.uiScale,
                                onChangeUiScale = actions.onChangeUiScale,
                            )
                        }
                    }
                    SettingsCard {
                        ExportImportSection(
                            onExportSettings = actions.onExportSettings,
                            onPrepareExport = actions.onPrepareExport,
                            onImportSettings = actions.onImportSettings,
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (uiState.showDaemonToggle) {
                    SettingsCard {
                        DaemonModeToggle(
                            isDaemonEnabled = uiState.isDaemonEnabled,
                            onToggleDaemon = actions.onToggleDaemon,
                        )
                    }
                }
                SettingsCard {
                    DynamicUiToggle(
                        isDynamicUiEnabled = uiState.isDynamicUiEnabled,
                        onToggleDynamicUi = actions.onToggleDynamicUi,
                    )
                }
                SettingsCard {
                    ThemeModePicker(
                        themeMode = uiState.themeMode,
                        onChangeThemeMode = actions.onChangeThemeMode,
                    )
                    if (uiState.showDynamicColorsToggle) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        DynamicColorsToggle(
                            useDynamicColors = uiState.useDynamicColors,
                            onToggleDynamicColors = actions.onToggleDynamicColors,
                        )
                    }
                }
                SettingsCard {
                    FontFamilyPicker(
                        selectedFontFamily = uiState.fontFamily,
                        onChangeFontFamily = actions.onChangeFontFamily,
                    )
                }
                SettingsCard {
                    FontFamilyPicker(
                        selectedFontFamily = uiState.aiFontFamily,
                        onChangeFontFamily = actions.onChangeAiFontFamily,
                        title = stringResource(Res.string.settings_ai_font_family),
                        description = stringResource(Res.string.settings_ai_font_family_description),
                    )
                }
                if (uiState.showUiScale) {
                    SettingsCard {
                        UiScaleSection(
                            uiScale = uiState.uiScale,
                            onChangeUiScale = actions.onChangeUiScale,
                        )
                    }
                }
                SettingsCard {
                    ExportImportSection(
                        onExportSettings = actions.onExportSettings,
                        onPrepareExport = actions.onPrepareExport,
                        onImportSettings = actions.onImportSettings,
                    )
                }
            }
        }
    }
}

