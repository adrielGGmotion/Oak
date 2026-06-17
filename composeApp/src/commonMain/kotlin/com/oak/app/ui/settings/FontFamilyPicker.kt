package com.oak.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oak.app.data.OakFontFamily
import com.oak.app.ui.resolve
import com.oak.app.ui.toTypography
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_font_family
import oak.composeapp.generated.resources.settings_font_family_description
import oak.composeapp.generated.resources.settings_font_preview
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontFamilyPicker(
    selectedFontFamily: OakFontFamily,
    onChangeFontFamily: (OakFontFamily) -> Unit,
    title: String = stringResource(Res.string.settings_font_family),
    description: String = stringResource(Res.string.settings_font_family_description),
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = stringResource(selectedFontFamily.displayNameRes),
                onValueChange = {},
                readOnly = true,
                textStyle = TextStyle(
                    fontFamily = selectedFontFamily.resolve(),
                    fontSize = 16.sp,
                ),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                OakFontFamily.entries.forEach { fontFamily ->
                    val isSelected = fontFamily == selectedFontFamily
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(fontFamily.displayNameRes),
                                style = TextStyle(
                                    fontFamily = fontFamily.resolve(),
                                    fontSize = 16.sp,
                                ),
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                        onClick = {
                            onChangeFontFamily(fontFamily)
                            expanded = false
                        },
                    )
                }
            }
        }
        FontFamilyPreview(
            fontFamily = selectedFontFamily,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun FontFamilyPreview(
    fontFamily: OakFontFamily,
    modifier: Modifier = Modifier,
) {
    val previewText = stringResource(Res.string.settings_font_preview)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        AnimatedContent(
            targetState = fontFamily,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 4 })
                    .togetherWith(
                        fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 4 }
                    )
            },
            label = "fontPreview",
        ) { targetFont ->
            val typography = targetFont.toTypography()
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(targetFont.displayNameRes),
                    style = typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = previewText,
                    style = typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
