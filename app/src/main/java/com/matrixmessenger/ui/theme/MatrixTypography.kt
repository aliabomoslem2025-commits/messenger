package com.matrixmessenger.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Matrix Messenger Typography System
 * Extended typography based on Material 3, customized for messenger UI
 * 
 * Reference: Telegram messenger - compact, high information density
 */

// Font family - using system default for now (can be extended with custom fonts)
val MatrixFontFamily = FontFamily.Default

// ========== Display Styles ==========
val MatrixTypographyDisplayLarge = androidx.compose.material3.DisplayLarge.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 57.sp,
    lineHeight = 64.sp,
    letterSpacing = (-0.25).sp
)

val MatrixTypographyDisplayMedium = androidx.compose.material3.DisplayMedium.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 45.sp,
    lineHeight = 52.sp,
    letterSpacing = 0.sp
)

val MatrixTypographyDisplaySmall = androidx.compose.material3.DisplaySmall.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = 44.sp,
    letterSpacing = 0.sp
)

// ========== Headline Styles ==========
val MatrixTypographyHeadlineLarge = androidx.compose.material3.HeadlineLarge.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 32.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.sp
)

val MatrixTypographyHeadlineMedium = androidx.compose.material3.HeadlineMedium.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    letterSpacing = 0.sp
)

val MatrixTypographyHeadlineSmall = androidx.compose.material3.HeadlineSmall.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp
)

// ========== Title Styles ==========
val MatrixTypographyTitleLarge = androidx.compose.material3.TitleLarge.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = 0.sp
)

val MatrixTypographyTitleMedium = androidx.compose.material3.TitleMedium.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.15.sp
)

val MatrixTypographyTitleSmall = androidx.compose.material3.TitleSmall.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
)

// ========== Body Styles ==========
val MatrixTypographyBodyLarge = androidx.compose.material3.BodyLarge.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.5.sp
)

val MatrixTypographyBodyMedium = androidx.compose.material3.BodyMedium.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.25.sp
)

val MatrixTypographyBodySmall = androidx.compose.material3.BodySmall.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.4.sp
)

// ========== Label Styles ==========
val MatrixTypographyLabelLarge = androidx.compose.material3.LabelLarge.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.1.sp
)

val MatrixTypographyLabelMedium = androidx.compose.material3.LabelMedium.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

val MatrixTypographyLabelSmall = androidx.compose.material3.LabelSmall.copy(
    fontFamily = MatrixFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
)

// ========== Messenger-Specific Typography ==========

// Chat list item
val MatrixTypographyChatName = MatrixTypographyTitleMedium.copy(
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp
)

val MatrixTypographyChatPreview = MatrixTypographyBodyMedium.copy(
    color = MatrixColors.TextSecondary,
    fontSize = 14.sp
)

val MatrixTypographyChatTimestamp = MatrixTypographyLabelSmall.copy(
    color = MatrixColors.TextSecondary,
    fontSize = 12.sp
)

// Message bubble
val MatrixTypographyMessageBody = MatrixTypographyBodyLarge.copy(
    fontSize = 15.sp,
    lineHeight = 22.sp
)

val MatrixTypographyMessageSender = MatrixTypographyLabelMedium.copy(
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp
)

val MatrixTypographyMessageTimestamp = MatrixTypographyLabelSmall.copy(
    color = MatrixColors.TextSecondary,
    fontSize = 11.sp
)

val MatrixTypographyMessageEdited = MatrixTypographyLabelSmall.copy(
    color = MatrixColors.TextTertiary,
    fontSize = 10.sp
)

// Input field
val MatrixTypographyInput = MatrixTypographyBodyLarge.copy(
    fontSize = 16.sp,
    lineHeight = 24.sp
)

val MatrixTypographyInputPlaceholder = MatrixTypographyBodyMedium.copy(
    color = MatrixColors.TextTertiary,
    fontSize = 14.sp
)

// Search
val MatrixTypographySearchQuery = MatrixTypographyBodyLarge.copy(
    fontSize = 16.sp
)

val MatrixTypographySearchResult = MatrixTypographyBodyMedium.copy(
    color = MatrixColors.TextSecondary
)

// Profile
val MatrixTypographyProfileName = MatrixTypographyHeadlineSmall.copy(
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp
)

val MatrixTypographyProfileStatus = MatrixTypographyBodyMedium.copy(
    color = MatrixColors.TextSecondary
)

// Settings
val MatrixTypographySettingsCategory = MatrixTypographyLabelMedium.copy(
    color = MatrixColors.TextSecondary,
    textTransform = androidx.compose.ui.text.TextTransform.Uppercase,
    letterSpacing = 0.5.sp
)

val MatrixTypographySettingsItem = MatrixTypographyBodyLarge.copy(
    fontSize = 15.sp
)

/**
 * Get the complete Matrix typography theme
 */
@Composable
fun MatrixTypography(): androidx.compose.material3.Typography {
    return androidx.compose.material3.Typography(
        displayLarge = MatrixTypographyDisplayLarge,
        displayMedium = MatrixTypographyDisplayMedium,
        displaySmall = MatrixTypographyDisplaySmall,
        headlineLarge = MatrixTypographyHeadlineLarge,
        headlineMedium = MatrixTypographyHeadlineMedium,
        headlineSmall = MatrixTypographyHeadlineSmall,
        titleLarge = MatrixTypographyTitleLarge,
        titleMedium = MatrixTypographyTitleMedium,
        titleSmall = MatrixTypographyTitleSmall,
        bodyLarge = MatrixTypographyBodyLarge,
        bodyMedium = MatrixTypographyBodyMedium,
        bodySmall = MatrixTypographyBodySmall,
        labelLarge = MatrixTypographyLabelLarge,
        labelMedium = MatrixTypographyLabelMedium,
        labelSmall = MatrixTypographyLabelSmall
    )
}
