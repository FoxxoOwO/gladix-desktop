package dev.gladix.desktop.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF7C4DFF)
private val AccentDark = Color(0xFFB69CFF)
private val AccentSoft = Color(0xFF3A2A63)

private val DarkColors = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF1A1030),
    primaryContainer = AccentSoft,
    onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFF9F8FD0),
    background = Color(0xFF0F0D14),
    onBackground = Color(0xFFECE9F2),
    surface = Color(0xFF16131D),
    onSurface = Color(0xFFECE9F2),
    surfaceVariant = Color(0xFF221D2C),
    onSurfaceVariant = Color(0xFFBDB5CC),
    outline = Color(0xFF463D57),
    error = Color(0xFFFF8A80)
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF),
    onPrimaryContainer = Color(0xFF2A1B52),
    background = Color(0xFFFDFBFF),
    onBackground = Color(0xFF1B1721),
    surface = Color(0xFFF7F3FC),
    onSurface = Color(0xFF1B1721),
    surfaceVariant = Color(0xFFECE5F5),
    onSurfaceVariant = Color(0xFF4B4357),
    outline = Color(0xFFB6ACC4)
)

@Composable
fun GladixTheme(useDark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}