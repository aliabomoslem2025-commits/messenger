package com.matrixmessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Light Theme Colors - Telegram-inspired
private val LightColorScheme = lightColorScheme(
    primary = MatrixColors.AccentPrimary,
    onPrimary = MatrixColors.TextOnAccent,
    primaryContainer = MatrixColors.AccentContainer,
    onPrimaryContainer = MatrixColors.TextPrimary,
    secondary = MatrixColors.AccentSecondary,
    onSecondary = MatrixColors.TextOnAccent,
    secondaryContainer = MatrixColors.SurfaceSecondary,
    onSecondaryContainer = MatrixColors.TextPrimary,
    tertiary = MatrixColors.TextLink,
    onTertiary = MatrixColors.TextOnAccent,
    background = MatrixColors.BackgroundPrimary,
    onBackground = MatrixColors.TextPrimary,
    surface = MatrixColors.SurfacePrimary,
    onSurface = MatrixColors.TextPrimary,
    surfaceVariant = MatrixColors.BackgroundSecondary,
    onSurfaceVariant = MatrixColors.TextSecondary,
    error = MatrixColors.Error,
    onError = MatrixColors.TextPrimary,
    outline = MatrixColors.Divider,
    outlineVariant = MatrixColors.DividerLight
)

// Dark Theme Colors - Telegram dark mode inspired
private val DarkColorScheme = darkColorScheme(
    primary = MatrixColors.AccentPrimary,
    onPrimary = MatrixColors.TextOnAccent,
    primaryContainer = MatrixColors.AccentContainer,
    onPrimaryContainer = MatrixColors.TextPrimary,
    secondary = MatrixColors.AccentSecondary,
    onSecondary = MatrixColors.TextOnAccent,
    secondaryContainer = MatrixColors.SurfaceSecondary,
    onSecondaryContainer = MatrixColors.TextPrimary,
    tertiary = MatrixColors.TextLink,
    onTertiary = MatrixColors.TextOnAccent,
    background = MatrixColors.BackgroundPrimary,
    onBackground = MatrixColors.TextPrimary,
    surface = MatrixColors.SurfacePrimary,
    onSurface = MatrixColors.TextPrimary,
    surfaceVariant = MatrixColors.BackgroundSecondary,
    onSurfaceVariant = MatrixColors.TextSecondary,
    error = MatrixColors.Error,
    onError = MatrixColors.TextPrimary,
    outline = MatrixColors.Divider,
    outlineVariant = MatrixColors.DividerLight
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
        typography = MatrixTypography(),
        content = content
    )
}
