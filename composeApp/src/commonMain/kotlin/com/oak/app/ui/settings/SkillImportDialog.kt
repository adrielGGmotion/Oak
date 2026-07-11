package com.oak.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oak.app.ui.OakOutlinedTextField
import com.oak.app.ui.handCursor
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.common_cancel
import oak.composeapp.generated.resources.settings_skills_import
import oak.composeapp.generated.resources.settings_skills_import_content_label
import oak.composeapp.generated.resources.settings_skills_import_description
import oak.composeapp.generated.resources.settings_skills_import_description_label
import oak.composeapp.generated.resources.settings_skills_import_name_label
import oak.composeapp.generated.resources.settings_skills_import_tools_label
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImportSkillDialog(
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
