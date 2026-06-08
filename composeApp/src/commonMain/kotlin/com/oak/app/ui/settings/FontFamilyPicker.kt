package com.oak.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oak.app.ui.OakFontFamily
import com.oak.app.ui.handCursor
import com.oak.app.ui.resolveForPreview
import com.oak.app.ui.toTypography
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.settings_font_family
import oak.composeapp.generated.resources.settings_font_family_description
import oak.composeapp.generated.resources.settings_font_preview
import org.jetbrains.compose.resources.stringResource

@Composable
fun FontFamilyPicker(
    selectedFontFamily: OakFontFamily,
    onChangeFontFamily: (OakFontFamily) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.settings_font_family),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.settings_font_family_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OakFontFamily.entries.forEach { fontFamily ->
                FontFamilyCard(
                    fontFamily = fontFamily,
                    isSelected = fontFamily == selectedFontFamily,
                    onClick = { onChangeFontFamily(fontFamily) },
                )
            }
        }
        FontFamilyPreview(
            fontFamily = selectedFontFamily,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun FontFamilyCard(
    fontFamily: OakFontFamily,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val previewStyle = when (fontFamily) {
        OakFontFamily.Prata -> TextStyle(fontSize = 18.sp)
        else -> TextStyle(fontSize = 14.sp)
    }
    val resolvedStyle = previewStyle.copy(
        fontFamily = fontFamily.resolveForPreview(),
    )

    Card(
        modifier = Modifier
            .width(110.dp)
            .height(80.dp)
            .handCursor()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = fontFamily.displayName,
                    style = resolvedStyle,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FontFamilyPreview(
    fontFamily: OakFontFamily,
    modifier: Modifier = Modifier,
) {
    val previewText = stringResource(Res.string.settings_font_preview)
    val typography = fontFamily.toTypography()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = fontFamily.displayName,
                style = typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = previewText,
                style = typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
