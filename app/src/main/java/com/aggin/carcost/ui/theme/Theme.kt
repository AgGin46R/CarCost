package com.aggin.carcost.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ---------------------------------------------------------------------------
//  Accent scheme definitions
// ---------------------------------------------------------------------------

/** Supported accent colours. Key string stored in DataStore / SettingsManager. */
enum class AccentScheme(
    val key: String,
    val displayName: String,
    val previewColor: Color
) {
    BLUE("Blue", "Синий", Color(0xFF1976D2)),
    GREEN("Green", "Зелёный", Color(0xFF2E7D32)),
    PURPLE("Purple", "Фиолетовый", Color(0xFF6A1B9A)),
    ORANGE("Orange", "Оранжевый", Color(0xFFE65100)),
    TEAL("Teal", "Бирюзовый", Color(0xFF00695C));

    companion object {
        fun fromKey(key: String): AccentScheme =
            entries.firstOrNull { it.key == key } ?: BLUE
    }
}

// ---------------------------------------------------------------------------
//  Per-accent light color schemes
// ---------------------------------------------------------------------------

private fun lightScheme(accent: AccentScheme): ColorScheme = when (accent) {
    AccentScheme.BLUE -> lightColorScheme(
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
    AccentScheme.GREEN -> lightColorScheme(
        primary = Color(0xFF2E7D32),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC8E6C9),
        onPrimaryContainer = Color(0xFF1B5E20),
        secondary = Color(0xFF558B2F),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFDCEDC8),
        onSecondaryContainer = Color(0xFF33691E),
        tertiary = Color(0xFFF57F17),
        onTertiary = Color.White,
        background = Color(0xFFF1F8E9),
        onBackground = Color(0xFF1C1B1F),
        surface = Color.White,
        onSurface = Color(0xFF1C1B1F),
        error = Color(0xFFD32F2F),
        onError = Color.White
    )
    AccentScheme.PURPLE -> lightColorScheme(
        primary = Color(0xFF6A1B9A),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE1BEE7),
        onPrimaryContainer = Color(0xFF4A148C),
        secondary = Color(0xFF8E24AA),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3E5F5),
        onSecondaryContainer = Color(0xFF6A1B9A),
        tertiary = Color(0xFFFF6F00),
        onTertiary = Color.White,
        background = Color(0xFFF8F5FC),
        onBackground = Color(0xFF1C1B1F),
        surface = Color.White,
        onSurface = Color(0xFF1C1B1F),
        error = Color(0xFFD32F2F),
        onError = Color.White
    )
    AccentScheme.ORANGE -> lightColorScheme(
        primary = Color(0xFFE65100),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE0B2),
        onPrimaryContainer = Color(0xFFBF360C),
        secondary = Color(0xFFFF8F00),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFF8E1),
        onSecondaryContainer = Color(0xFFE65100),
        tertiary = Color(0xFF1976D2),
        onTertiary = Color.White,
        background = Color(0xFFFFF8F5),
        onBackground = Color(0xFF1C1B1F),
        surface = Color.White,
        onSurface = Color(0xFF1C1B1F),
        error = Color(0xFFD32F2F),
        onError = Color.White
    )
    AccentScheme.TEAL -> lightColorScheme(
        primary = Color(0xFF00695C),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB2DFDB),
        onPrimaryContainer = Color(0xFF004D40),
        secondary = Color(0xFF0097A7),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0F7FA),
        onSecondaryContainer = Color(0xFF006064),
        tertiary = Color(0xFF558B2F),
        onTertiary = Color.White,
        background = Color(0xFFF0FAFA),
        onBackground = Color(0xFF1C1B1F),
        surface = Color.White,
        onSurface = Color(0xFF1C1B1F),
        error = Color(0xFFD32F2F),
        onError = Color.White
    )
}

// ---------------------------------------------------------------------------
//  Per-accent dark color schemes
// ---------------------------------------------------------------------------

private fun darkScheme(accent: AccentScheme): ColorScheme = when (accent) {
    AccentScheme.BLUE -> darkColorScheme(
        primary = Color(0xFF64B5F6),
        onPrimary = Color(0xFF003258),
        primaryContainer = Color(0xFF1B3E6A),
        onPrimaryContainer = Color(0xFFBBDEFB),
        secondary = Color(0xFF4DB6AC),
        onSecondary = Color(0xFF003C38),
        secondaryContainer = Color(0xFF0D2E2B),
        onSecondaryContainer = Color(0xFFB2DFDB),
        tertiary = Color(0xFFFFB74D),
        onTertiary = Color(0xFF4A2800),
        background = Color(0xFF0D1117),
        onBackground = Color(0xFFE6EDF3),
        surface = Color(0xFF161B22),
        onSurface = Color(0xFFE6EDF3),
        surfaceVariant = Color(0xFF1C2128),
        onSurfaceVariant = Color(0xFF8B949E),
        error = Color(0xFFEF5350),
        onError = Color(0xFF690005)
    )
    AccentScheme.GREEN -> darkColorScheme(
        primary = Color(0xFF4CAF50),
        onPrimary = Color(0xFF003909),
        primaryContainer = Color(0xFF1B3E1D),
        onPrimaryContainer = Color(0xFFA5D6A7),
        secondary = Color(0xFF8BC34A),
        onSecondary = Color(0xFF1E3200),
        secondaryContainer = Color(0xFF1C2B0D),
        onSecondaryContainer = Color(0xFFDCEDC8),
        tertiary = Color(0xFFFFB74D),
        onTertiary = Color(0xFF4A2800),
        background = Color(0xFF0A0F0A),
        onBackground = Color(0xFFE6EDF3),
        surface = Color(0xFF0F1A0F),
        onSurface = Color(0xFFE6EDF3),
        surfaceVariant = Color(0xFF1A221A),
        onSurfaceVariant = Color(0xFF8B949E),
        error = Color(0xFFEF5350),
        onError = Color(0xFF690005)
    )
    AccentScheme.PURPLE -> darkColorScheme(
        primary = Color(0xFFCE93D8),
        onPrimary = Color(0xFF3A0050),
        primaryContainer = Color(0xFF4A1570),
        onPrimaryContainer = Color(0xFFE1BEE7),
        secondary = Color(0xFFBA68C8),
        onSecondary = Color(0xFF2D0042),
        secondaryContainer = Color(0xFF3D1157),
        onSecondaryContainer = Color(0xFFF3E5F5),
        tertiary = Color(0xFFFFB74D),
        onTertiary = Color(0xFF4A2800),
        background = Color(0xFF0E0A13),
        onBackground = Color(0xFFEDE7F6),
        surface = Color(0xFF150E1E),
        onSurface = Color(0xFFEDE7F6),
        surfaceVariant = Color(0xFF1E1528),
        onSurfaceVariant = Color(0xFF8B949E),
        error = Color(0xFFEF5350),
        onError = Color(0xFF690005)
    )
    AccentScheme.ORANGE -> darkColorScheme(
        primary = Color(0xFFFFB74D),
        onPrimary = Color(0xFF4A2800),
        primaryContainer = Color(0xFF6B3A00),
        onPrimaryContainer = Color(0xFFFFE0B2),
        secondary = Color(0xFFFFCC80),
        onSecondary = Color(0xFF4A3000),
        secondaryContainer = Color(0xFF5A3A00),
        onSecondaryContainer = Color(0xFFFFF8E1),
        tertiary = Color(0xFF64B5F6),
        onTertiary = Color(0xFF003258),
        background = Color(0xFF130D08),
        onBackground = Color(0xFFF5E6D8),
        surface = Color(0xFF1E1208),
        onSurface = Color(0xFFF5E6D8),
        surfaceVariant = Color(0xFF2A1D10),
        onSurfaceVariant = Color(0xFF8B949E),
        error = Color(0xFFEF5350),
        onError = Color(0xFF690005)
    )
    AccentScheme.TEAL -> darkColorScheme(
        primary = Color(0xFF4DB6AC),
        onPrimary = Color(0xFF003B38),
        primaryContainer = Color(0xFF0D2E2B),
        onPrimaryContainer = Color(0xFFB2DFDB),
        secondary = Color(0xFF4DD0E1),
        onSecondary = Color(0xFF003543),
        secondaryContainer = Color(0xFF0D2530),
        onSecondaryContainer = Color(0xFFE0F7FA),
        tertiary = Color(0xFF8BC34A),
        onTertiary = Color(0xFF1E3200),
        background = Color(0xFF08100F),
        onBackground = Color(0xFFE0F2F1),
        surface = Color(0xFF0E1817),
        onSurface = Color(0xFFE0F2F1),
        surfaceVariant = Color(0xFF172322),
        onSurfaceVariant = Color(0xFF8B949E),
        error = Color(0xFFEF5350),
        onError = Color(0xFF690005)
    )
}

// ---------------------------------------------------------------------------
//  Public theme composable
// ---------------------------------------------------------------------------

@Composable
fun CarCostTheme(
    themeSetting: String = "System",  // "Light", "Dark", "System"
    accentSetting: String = "Blue",   // key from AccentScheme
    content: @Composable () -> Unit
) {
    val darkTheme: Boolean = when (themeSetting) {
        "Light" -> false
        "Dark"  -> true
        else    -> isSystemInDarkTheme()
    }

    val accent = AccentScheme.fromKey(accentSetting)
    val colorScheme = if (darkTheme) darkScheme(accent) else lightScheme(accent)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primaryContainer.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
