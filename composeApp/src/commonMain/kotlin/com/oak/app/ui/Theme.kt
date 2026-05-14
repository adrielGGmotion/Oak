@file:Suppress("DEPRECATION")

package com.oak.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

val OakSeed = Color(0xFF5B8C5B)

private fun tonal(seed: Color, tone: Float): Color {
    return when {
        tone <= 50f -> lerp(Color.Black, seed, tone / 50f)
        else -> lerp(seed, Color.White, (tone - 50f) / 50f)
    }
}

fun Modifier.handCursor() = pointerHoverIcon(PointerIcon.Hand, overrideDescendants = true)

fun greenLightColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = tonal(OakSeed, 40f),
        onPrimary = tonal(OakSeed, 100f),
        primaryContainer = tonal(OakSeed, 90f),
        onPrimaryContainer = tonal(OakSeed, 10f),
        secondary = tonal(OakSeed, 40f),
        onSecondary = tonal(OakSeed, 100f),
        secondaryContainer = tonal(OakSeed, 90f),
        onSecondaryContainer = tonal(OakSeed, 10f),
        tertiary = tonal(OakSeed, 40f),
        onTertiary = tonal(OakSeed, 100f),
        tertiaryContainer = tonal(OakSeed, 90f),
        onTertiaryContainer = tonal(OakSeed, 10f),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF8FAF3),
        onBackground = Color(0xFF1A1C19),
        surface = Color(0xFFF8FAF3),
        onSurface = Color(0xFF1A1C19),
        surfaceVariant = Color(0xFFDDE5D9),
        onSurfaceVariant = Color(0xFF424940),
        outline = Color(0xFF72796F),
        outlineVariant = Color(0xFFC1C9BD),
        inverseSurface = Color(0xFF2F312D),
        inverseOnSurface = Color(0xFFF0F1EB),
        inversePrimary = tonal(OakSeed, 80f),
        scrim = Color(0xFF000000),
    )
}

fun greenDarkColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = tonal(OakSeed, 80f),
        onPrimary = tonal(OakSeed, 20f),
        primaryContainer = tonal(OakSeed, 30f),
        onPrimaryContainer = tonal(OakSeed, 90f),
        secondary = tonal(OakSeed, 80f),
        onSecondary = tonal(OakSeed, 20f),
        secondaryContainer = tonal(OakSeed, 30f),
        onSecondaryContainer = tonal(OakSeed, 90f),
        tertiary = tonal(OakSeed, 80f),
        onTertiary = tonal(OakSeed, 20f),
        tertiaryContainer = tonal(OakSeed, 30f),
        onTertiaryContainer = tonal(OakSeed, 90f),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF1A1C19),
        onBackground = Color(0xFFE2E3DC),
        surface = Color(0xFF1A1C19),
        onSurface = Color(0xFFE2E3DC),
        surfaceVariant = Color(0xFF424940),
        onSurfaceVariant = Color(0xFFC1C9BD),
        outline = Color(0xFF8B9388),
        outlineVariant = Color(0xFF424940),
        inverseSurface = Color(0xFFE2E3DC),
        inverseOnSurface = Color(0xFF2F312D),
        inversePrimary = tonal(OakSeed, 40f),
        scrim = Color(0xFF000000),
    )
}

val DarkColorScheme = greenDarkColorScheme()
val LightColorScheme = greenLightColorScheme()

fun ColorScheme.withBlackBackground(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
)

val ColorScheme.isOledFlavor: Boolean get() = background == Color.Black

@Composable
fun oakAdaptiveCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = if (MaterialTheme.colorScheme.isOledFlavor) {
        Color.Transparent
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    },
)

@Composable
fun oakAdaptiveCardBorder(): BorderStroke? = if (MaterialTheme.colorScheme.isOledFlavor) {
    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
} else {
    null
}

@Composable
fun Modifier.oakAdaptiveCardSurface(shape: Shape = CardDefaults.shape): Modifier = this
    .clip(shape)
    .background(
        if (MaterialTheme.colorScheme.isOledFlavor) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        },
    )
    .then(
        if (MaterialTheme.colorScheme.isOledFlavor) {
            Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
        } else {
            Modifier
        },
    )

@Composable
expect fun oakColorScheme(useDynamicColors: Boolean, darkTheme: Boolean): ColorScheme

@Composable
fun OakTheme(
    useDynamicColors: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    isOledBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = oakColorScheme(useDynamicColors, darkTheme)
    val effectiveScheme = if (isOledBlack) colorScheme.withBlackBackground() else colorScheme
    MaterialTheme(colorScheme = effectiveScheme) {
        content()
    }
}

@Composable
fun outlineTextFieldColors() = OutlinedTextFieldDefaults.colors()

@Composable
fun OakOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(12.dp),
        colors = outlineTextFieldColors(),
    )
}

@Composable
fun OakClearableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    OakOutlinedTextField(
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        value = value,
        onValueChange = onValueChange,
        label = label,
        singleLine = singleLine,
        trailingIcon = {
            IconButton(
                onClick = { onValueChange("") },
                modifier = Modifier.handCursor()
                    .alpha(if (focused && value.isNotEmpty()) 1f else 0f),
                enabled = value.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
@Preview
fun Theme(
    colorScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme,
    ) {
        content()
    }
}
