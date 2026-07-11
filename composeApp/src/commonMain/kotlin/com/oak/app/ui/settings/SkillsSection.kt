package com.oak.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import com.oak.app.ui.handCursor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_skills_description
import oak.composeapp.generated.resources.settings_skills_import
import oak.composeapp.generated.resources.settings_skills_import_from_file
import oak.composeapp.generated.resources.settings_skills_title
import org.jetbrains.compose.resources.stringResource

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

