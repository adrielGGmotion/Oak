package com.oak.app.ui

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.oak.app.data.OakFontFamily
import org.jetbrains.compose.resources.Font
import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.Inter_Variable
import oak.composeapp.generated.resources.JetBrainsMono_Variable
import oak.composeapp.generated.resources.JosefinSans_Variable
import oak.composeapp.generated.resources.LexendDeca_Regular
import oak.composeapp.generated.resources.Lora_Regular
import oak.composeapp.generated.resources.Merriweather_Variable
import oak.composeapp.generated.resources.NotoSans_Variable
import oak.composeapp.generated.resources.PlusJakartaSans_Variable
import oak.composeapp.generated.resources.Prata_Regular

@Composable
fun OakFontFamily.resolve(): FontFamily = when (this) {
    OakFontFamily.System -> FontFamily.Default
    OakFontFamily.Inter -> {
        val font = Font(resource = Res.font.Inter_Variable, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.JosefinSans -> {
        val font = Font(resource = Res.font.JosefinSans_Variable, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.LexendDeca -> {
        val font = Font(resource = Res.font.LexendDeca_Regular, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.NotoSans -> {
        val font = Font(resource = Res.font.NotoSans_Variable, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.PlusJakartaSans -> {
        val font = Font(resource = Res.font.PlusJakartaSans_Variable, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.Lora -> {
        val font = Font(resource = Res.font.Lora_Regular, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.Merriweather -> {
        val font = Font(resource = Res.font.Merriweather_Variable, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.Prata -> {
        val font = Font(resource = Res.font.Prata_Regular, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
    OakFontFamily.JetBrainsMono -> {
        val font = Font(resource = Res.font.JetBrainsMono_Variable, weight = FontWeight.Normal)
        remember(font) { FontFamily(font) }
    }
}

@Composable
fun OakFontFamily.resolveForPreview(): FontFamily = resolve()

@Composable
fun OakFontFamily.toTypography(): Typography {
    val fontFamily = resolve()
    return remember(fontFamily) {
        Typography(
            displayLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
            displayMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
            displaySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
            headlineLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
            headlineMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
            headlineSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
            titleLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
            titleMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
            titleSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            bodyLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
            bodyMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
            bodySmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
            labelLarge = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
            labelMedium = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
            labelSmall = TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        )
    }
}
