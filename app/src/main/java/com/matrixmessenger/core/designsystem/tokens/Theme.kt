package com.matrixmessenger.core.designsystem.tokens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes

private val MatrixTypographyM3 = Typography(
    headlineLarge = MatrixTypography.HeadlineLarge,
    headlineMedium = MatrixTypography.HeadlineMedium,
    headlineSmall = MatrixTypography.HeadlineSmall,
    bodyLarge = MatrixTypography.BodyLarge,
    bodyMedium = MatrixTypography.BodyMedium,
    labelSmall = MatrixTypography.LabelSmall
)

private val MatrixShapesM3 = Shapes(
    small = MatrixShapes.CardSmall,
    medium = MatrixShapes.CardMedium,
    large = MatrixShapes.CardLarge
)

@Composable
fun MatrixMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = MatrixColors.DarkPrimary,
            onPrimary = MatrixColors.White,
            primaryContainer = MatrixColors.DarkBubbleOutgoing,
            onPrimaryContainer = MatrixColors.White,
            secondary = MatrixColors.DarkSurface,
            onSecondary = MatrixColors.DarkTextSecondary,
            background = MatrixColors.DarkBackground,
            onBackground = MatrixColors.DarkTextPrimary,
            surface = MatrixColors.DarkSurface,
            onSurface = MatrixColors.DarkTextPrimary,
            error = MatrixColors.Red,
            outline = MatrixColors.DarkDivider
        )
    } else {
        lightColorScheme(
            primary = MatrixColors.LightPrimary,
            onPrimary = MatrixColors.White,
            primaryContainer = MatrixColors.LightBubbleOutgoing,
            onPrimaryContainer = MatrixColors.Black,
            secondary = MatrixColors.LightSurface,
            onSecondary = MatrixColors.LightTextSecondary,
            background = MatrixColors.LightBackground,
            onBackground = MatrixColors.LightTextPrimary,
            surface = MatrixColors.LightSurface,
            onSurface = MatrixColors.LightTextPrimary,
            error = MatrixColors.Red,
            outline = MatrixColors.LightDivider
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MatrixTypographyM3,
        shapes = MatrixShapesM3,
        content = content
    )
}
