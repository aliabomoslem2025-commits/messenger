package com.matrixmessenger.core.designsystem.tokens

import androidx.compose.ui.graphics.Color

/**
 * Pixel-accurate design tokens for Matrix Messenger,
 * based on the professional Telegram UI/UX specification.
 */
object MatrixColors {
    // Brand Palette
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    val Blue = Color(0xFF5288C1)
    val LightBlue = Color(0xFF62A9EA)
    val Green = Color(0xFF4CD964)
    val Red = Color(0xFFE53935)
    
    // Theme Palette - Dark (Primary Spec)
    val DarkBackground = Color(0xFF0E1621)
    val DarkSurface = Color(0xFF17212B)
    val DarkBubbleIncoming = Color(0xFF18252F)
    val DarkBubbleOutgoing = Color(0xFF2B5278)
    val DarkTextPrimary = Color(0xFFFFFFFF)
    val DarkTextSecondary = Color(0xFF7F91A4)
    val DarkDivider = Color(0xFF0B1118)
    val DarkPrimary = Blue
    
    // Theme Palette - Light (Adaptive)
    val LightBackground = Color(0xFFDEE6ED)
    val LightSurface = Color(0xFFFFFFFF)
    val LightBubbleIncoming = Color(0xFFFFFFFF)
    val LightBubbleOutgoing = Color(0xFFEEFFDE)
    val LightTextPrimary = Color(0xFF000000)
    val LightTextSecondary = Color(0xFF8A8A8E)
    val LightDivider = Color(0xFFD1D1D6)
    val LightPrimary = Color(0xFF0088CC)

    // Functional Tokens
    val Accent = Blue
    val AccentBlue = Blue
    val Online = Green
    val Error = Red
    val Link = LightBlue
    val Verified = Blue

    // Call Screen & Legacy Aliases
    val BackgroundPrimary = DarkBackground
    val AccentPrimary = Blue
    val SurfaceSecondary = DarkSurface
    val TextPrimary = DarkTextPrimary
    val TextSecondary = DarkTextSecondary
    val TextTertiary = DarkTextSecondary.copy(alpha = 0.7f)
    val BubbleIncoming = DarkBubbleIncoming
    val BubbleOutgoing = DarkBubbleOutgoing
    val SurfacePrimary = DarkSurface
}
