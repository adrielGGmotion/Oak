package com.oak.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oak.app.data.Skill
import com.oak.app.ui.OakOutlinedTextField
import com.oak.app.ui.handCursor
import com.oak.app.ui.oakAdaptiveCardBorder
import com.oak.app.ui.oakAdaptiveCardColors
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.common_cancel
import oak.composeapp.generated.resources.settings_skills_built_in
import oak.composeapp.generated.resources.settings_skills_description
import oak.composeapp.generated.resources.settings_skills_discard
import oak.composeapp.generated.resources.settings_skills_discard_confirm
import oak.composeapp.generated.resources.settings_skills_discard_title
import oak.composeapp.generated.resources.settings_skills_edit
import oak.composeapp.generated.resources.settings_skills_edit_description
import oak.composeapp.generated.resources.settings_skills_import
import oak.composeapp.generated.resources.settings_skills_import_description
import oak.composeapp.generated.resources.settings_skills_import_from_file
import oak.composeapp.generated.resources.settings_skills_import_name_label
import oak.composeapp.generated.resources.settings_skills_import_content_label
import oak.composeapp.generated.resources.settings_skills_import_description_label
import oak.composeapp.generated.resources.settings_skills_import_tools_label
import oak.composeapp.generated.resources.settings_skills_remove
import oak.composeapp.generated.resources.settings_skills_required_tools
import oak.composeapp.generated.resources.settings_skills_reset
import oak.composeapp.generated.resources.settings_skills_reset_cancel
import oak.composeapp.generated.resources.settings_skills_reset_confirm
import oak.composeapp.generated.resources.settings_skills_save
import oak.composeapp.generated.resources.settings_skills_collapse
import oak.composeapp.generated.resources.settings_skills_expand
import oak.composeapp.generated.resources.settings_skills_title
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillsSection(
    skills: ImmutableList<SkillUiState>,
    onToggleSkill: (String, Boolean) -> Unit,
    onRemoveSkill: (String) -> Unit,
    onImportSkill: (String, String, String, List<String>) -> Unit,
    showImportDialog: Boolean,
    onShowImportDialog: (Boolean) -> Unit,
    importSkillPrefill: ImportSkillPrefill?,
    onImportSkillFromFile: () -> Unit,
    onSkillFilePicked: (ByteArray, String) -> Unit,
    onEditSkill: (String, String, String, String, List<String>) -> Unit,
    showEditDialog: Boolean,
    editingSkillId: String?,
    onShowEditSkillDialog: (String?) -> Unit,
    onResetSkill: (String) -> Unit,
) {
    val isPreview = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = if (!isPreview) {
        rememberFilePickerLauncher(
            type = FileKitType.File(extensions = listOf("md")),
        ) { file: PlatformFile? ->
            if (file != null) {
                scope.launch {
                    val bytes = file.readBytes()
                    onSkillFilePicked(bytes, file.name)
                }
            }
        }
    } else {
        null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_skills_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_skills_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        for (skill in skills) {
            key(skill.id) {
                SkillCard(
                    skill = skill,
                    onToggle = { enabled -> onToggleSkill(skill.id, enabled) },
                    onRemove = { onRemoveSkill(skill.id) },
                    onEdit = { onShowEditSkillDialog(skill.id) },
                    onReset = { onResetSkill(skill.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            OutlinedButton(
                onClick = { onShowImportDialog(true) },
                modifier = Modifier.weight(1f).handCursor(),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.settings_skills_import))
            }
            OutlinedButton(
                onClick = { filePickerLauncher?.launch() },
                modifier = Modifier.weight(1f).handCursor(),
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.settings_skills_import_from_file))
            }
        }
    }

    if (showImportDialog) {
        ImportSkillDialog(
            prefill = importSkillPrefill,
            onDismiss = { onShowImportDialog(false) },
            onImport = onImportSkill,
        )
    }

    if (showEditDialog && editingSkillId != null) {
        val editingSkill = skills.find { it.id == editingSkillId }
        if (editingSkill != null) {
            EditSkillDialog(
                skill = editingSkill,
                onDismiss = { onShowEditSkillDialog(null) },
                onSave = onEditSkill,
                onReset = onResetSkill,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillCard(
    skill: SkillUiState,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { if (!skill.isBuiltIn) expanded = !expanded },
        modifier = Modifier.fillMaxWidth().handCursor(),
        colors = oakAdaptiveCardColors(),
        border = oakAdaptiveCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (skill.isBuiltIn) Icons.Default.Star else Icons.Default.Extension,
                    contentDescription = null,
                    tint = if (skill.isBuiltIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = skill.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (skill.description.isNotEmpty()) {
                        Text(
                            text = skill.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (skill.isBuiltIn) {
                            SkillBadge(text = stringResource(Res.string.settings_skills_built_in))
                        }
                        if (skill.requiredTools.isNotEmpty()) {
                            SkillBadge(
                                text = "${skill.requiredTools.size} ${
                                    stringResource(Res.string.settings_skills_required_tools)
                                }",
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                Switch(
                    checked = skill.isEnabled,
                    onCheckedChange = onToggle,
                )

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.settings_skills_edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                if (skill.isBuiltIn && skill.isModified) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.handCursor(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = stringResource(Res.string.settings_skills_reset),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (!skill.isBuiltIn) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = if (expanded) stringResource(Res.string.settings_skills_collapse) else stringResource(Res.string.settings_skills_expand),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expanded && !skill.isBuiltIn) {
                Spacer(Modifier.height(12.dp))

                if (skill.requiredTools.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.settings_skills_required_tools),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    for (toolId in skill.requiredTools) {
                        key(toolId) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = toolId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.handCursor(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(Res.string.settings_skills_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSkillDialog(
    prefill: ImportSkillPrefill?,
    onDismiss: () -> Unit,
    onImport: (String, String, String, List<String>) -> Unit,
) {
    var name by remember(prefill?.requestId) { mutableStateOf(prefill?.name ?: "") }
    var description by remember(prefill?.requestId) { mutableStateOf(prefill?.description ?: "") }
    var content by remember(prefill?.requestId) { mutableStateOf(prefill?.content ?: "") }
    var requiredTools by remember(prefill?.requestId) {
        mutableStateOf(prefill?.requiredTools?.joinToString(", ") ?: "")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_skills_import),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.settings_skills_import_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OakOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.settings_skills_import_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OakOutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(Res.string.settings_skills_import_description_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OakOutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(Res.string.settings_skills_import_content_label)) },
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            Spacer(Modifier.height(8.dp))

            OakOutlinedTextField(
                value = requiredTools,
                onValueChange = { requiredTools = it },
                label = { Text(stringResource(Res.string.settings_skills_import_tools_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
                        val tools = requiredTools
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onImport(name, description, content, tools)
                    },
                    enabled = name.isNotBlank() && content.isNotBlank(),
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_skills_import))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSkillDialog(
    skill: SkillUiState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, List<String>) -> Unit,
    onReset: (String) -> Unit,
) {
    var name by remember(skill.id, skill.content) { mutableStateOf(skill.name) }
    var description by remember(skill.id, skill.content) { mutableStateOf(skill.description) }
    var content by remember(skill.id, skill.content) { mutableStateOf(skill.content) }
    var requiredTools by remember(skill.id, skill.content) {
        mutableStateOf(skill.requiredTools.joinToString(", "))
    }

    val hasChanges = name != skill.name ||
        description != skill.description ||
        content != skill.content ||
        requiredTools != skill.requiredTools.joinToString(", ")

    var showDiscardDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val handleDismiss = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_skills_edit),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.settings_skills_edit_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            OakOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.settings_skills_import_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OakOutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(Res.string.settings_skills_import_description_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OakOutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(Res.string.settings_skills_import_content_label)) },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                enabled = !(skill.isBuiltIn && skill.id == Skill.EMAIL_SKILL_ID && skill.content.isEmpty()),
            )
            Spacer(Modifier.height(8.dp))

            OakOutlinedTextField(
                value = requiredTools,
                onValueChange = { requiredTools = it },
                label = { Text(stringResource(Res.string.settings_skills_import_tools_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (skill.isBuiltIn && skill.isModified) {
                    TextButton(
                        onClick = {
                            if (hasChanges) {
                                showResetDialog = true
                            } else {
                                onReset(skill.id)
                            }
                        },
                        modifier = Modifier.handCursor(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(Res.string.settings_skills_reset))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(
                    onClick = handleDismiss,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = {
                        val tools = requiredTools
                            .split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onSave(skill.id, name, description, content, tools)
                    },
                    enabled = name.isNotBlank() && (skill.id == Skill.EMAIL_SKILL_ID || content.isNotBlank()) && hasChanges,
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_skills_save))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(Res.string.settings_skills_discard_title)) },
            text = { Text(stringResource(Res.string.settings_skills_discard_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDismiss()
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_skills_discard))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.common_cancel))
                }
            },
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(Res.string.settings_skills_reset)) },
            text = { Text(stringResource(Res.string.settings_skills_reset_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        onReset(skill.id)
                    },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_skills_reset))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false },
                    modifier = Modifier.handCursor(),
                ) {
                    Text(stringResource(Res.string.settings_skills_reset_cancel))
                }
            },
        )
    }
}
