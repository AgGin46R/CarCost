package com.aggin.carcost.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Ваша светлая цветовая схема (без изменений)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1976D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFF26A69A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00695C),
    tertiary = Color(0xFFFF6F00),
    onTertiary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

// Тёмная тема в стиле мокапа — зелёный акцент
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color(0xFF003909),
    primaryContainer = Color(0xFF1B3E1D),
    onPrimaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF0D1F2D),
    onSecondaryContainer = Color(0xFFBBDEFB),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF2D1F00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2128),
    onSurfaceVariant = Color(0xFF8B949E),
    error = Color(0xFFEF5350),
    onError = Color(0xFF690005)
)

@Composable
fun CarCostTheme(
    themeSetting: String = "System", // "Light", "Dark", "System"
    content: @Composable () -> Unit
) {
    val darkTheme: Boolean = when (themeSetting) {
        "Light" -> false
        "Dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primaryContainer.toArgb() // Изменено для лучшего вида
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}