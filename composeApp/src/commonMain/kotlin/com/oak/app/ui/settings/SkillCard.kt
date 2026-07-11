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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oak.app.ui.handCursor
import com.oak.app.ui.oakAdaptiveCardBorder
import com.oak.app.ui.oakAdaptiveCardColors
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_skills_built_in
import oak.composeapp.generated.resources.settings_skills_collapse
import oak.composeapp.generated.resources.settings_skills_edit
import oak.composeapp.generated.resources.settings_skills_expand
import oak.composeapp.generated.resources.settings_skills_remove
import oak.composeapp.generated.resources.settings_skills_required_tools
import oak.composeapp.generated.resources.settings_skills_reset
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SkillCard(
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
internal fun SkillBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}
