package com.oak.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
actual fun oakColorScheme(useDynamicColors: Boolean, darkTheme: Boolean): ColorScheme {
    return if (darkTheme) greenDarkColorScheme() else greenLightColorScheme()
}
