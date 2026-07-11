package com.oak.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oak.app.data.Skill
import com.oak.app.ui.OakOutlinedTextField
import com.oak.app.ui.handCursor
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.common_cancel
import oak.composeapp.generated.resources.settings_skills_discard
import oak.composeapp.generated.resources.settings_skills_discard_confirm
import oak.composeapp.generated.resources.settings_skills_discard_title
import oak.composeapp.generated.resources.settings_skills_edit
import oak.composeapp.generated.resources.settings_skills_edit_description
import oak.composeapp.generated.resources.settings_skills_import_content_label
import oak.composeapp.generated.resources.settings_skills_import_description_label
import oak.composeapp.generated.resources.settings_skills_import_name_label
import oak.composeapp.generated.resources.settings_skills_import_tools_label
import oak.composeapp.generated.resources.settings_skills_reset
import oak.composeapp.generated.resources.settings_skills_reset_cancel
import oak.composeapp.generated.resources.settings_skills_reset_confirm
import oak.composeapp.generated.resources.settings_skills_save
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSkillDialog(
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

    // Use rememberUpdatedState so the confirmValueChange lambda (captured once
    // by rememberBottomSheetState) always reads the latest hasChanges value.
    // showDiscardDialog is a var backed by mutableStateOf so the MutableState
    // reference stays valid even inside the captured lambda.
    val currentHasChanges by rememberUpdatedState(hasChanges)

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        confirmValueChange = { newValue ->
            if (newValue == SheetValue.Hidden && currentHasChanges) {
                showDiscardDialog = true
                false // prevent the sheet from hiding
            } else {
                true
            }
        },
    )

    val handleDismiss = {
        if (hasChanges) {
            showDiscardDialog = true
        } else {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = sheetState,
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
