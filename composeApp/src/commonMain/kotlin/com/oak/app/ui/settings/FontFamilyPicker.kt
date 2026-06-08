package com.oak.app.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
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
    title: String = stringResource(Res.string.settings_font_family),
    description: String = stringResource(Res.string.settings_font_family_description),
) {
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
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 200),
    )

    Card(
        modifier = Modifier
            .width(72.dp)
            .height(48.dp)
            .handCursor()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
        border = BorderStroke(
            borderWidth,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = fontFamily.displayName,
                    style = TextStyle(
                        fontFamily = fontFamily.resolveForPreview(),
                        fontSize = 11.sp,
                    ),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                AnimatedVisibility(
                    visible = isSelected,
                    enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 2 },
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(10.dp),
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
                    text = targetFont.displayName,
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
