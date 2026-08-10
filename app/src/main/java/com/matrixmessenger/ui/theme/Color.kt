package com.matrixmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Theme Colors - Telegram-inspired
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3390EC),        // Telegram blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEEFF),
    onPrimaryContainer = Color(0xFF0D2F4E),
    secondary = Color(0xFF5A7B8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EBF0),
    onSecondaryContainer = Color(0xFF162630),
    tertiary = Color(0xFF7B5A8C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E0F5),
    onTertiaryContainer = Color(0xFF2B1630),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF0F2F5),  // Chat list background
    onSurfaceVariant = Color(0xFF000000),
    error = Color(0xFFE53935),
    onError = Color.White,
    outline = Color(0xFFCED0D5),
    outlineVariant = Color(0xFFE0E0E0)
)

// Dark Theme Colors - Telegram dark mode inspired
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5AA0F0),        // Lighter blue for dark mode
    onPrimary = Color(0xFF0D2F4E),
    primaryContainer = Color(0xFF0D2F4E),
    onPrimaryContainer = Color(0xFFDDEEFF),
    secondary = Color(0xFF8FA8B8),
    onSecondary = Color(0xFF162630),
    secondaryContainer = Color(0xFF162630),
    onSecondaryContainer = Color(0xFFE0EBF0),
    tertiary = Color(0xFFB89AC4),
    onTertiary = Color(0xFF2B1630),
    tertiaryContainer = Color(0xFF2B1630),
    onTertiaryContainer = Color(0xFFF0E0F5),
    background = Color(0xFF0E1621),     // Telegram dark background
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF17212B),        // Card/Chat background
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF0E1621), // Chat list background dark
    onSurfaceVariant = Color(0xFFFFFFFF),
    error = Color(0xFFCF6679),
    onError = Color(0xFF000000),
    outline = Color(0xFF4A5560),
    outlineVariant = Color(0xFF2C3640)
)

@Composable
fun MatrixMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
