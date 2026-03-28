package com.example.planreminder.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color.White,
    secondary = Color(0xFF2F4858),
    tertiary = Color(0xFFFF7A59),
    background = Color(0xFFF7F4EE),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFE4F0EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF76D8B2),
    onPrimary = Color(0xFF003826),
    secondary = Color(0xFF9BCBDE),
    tertiary = Color(0xFFFFB59F),
    background = Color(0xFF101513),
    surface = Color(0xFF141A17),
    surfaceVariant = Color(0xFF24312B),
)

@Composable
fun PlanReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
