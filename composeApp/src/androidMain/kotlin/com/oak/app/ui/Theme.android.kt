package com.oak.app.ui

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun oakColorScheme(useDynamicColors: Boolean, darkTheme: Boolean): ColorScheme {
    if (!useDynamicColors) {
        return if (darkTheme) greenDarkColorScheme() else greenLightColorScheme()
    }
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= 31) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) greenDarkColorScheme() else greenLightColorScheme()
    }
}
