package com.matrixmessenger.core.designsystem.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * High-density typography system for a professional messenger.
 */
object MatrixTypography {
    val HeaderTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    )
    
    val HeaderSubtitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp
    )
    
    val ChatItemTitle = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
    
    val ChatItemSnippet = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    )
    
    val ChatItemMeta = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
    
    val MessageBody = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp
    )
    
    val MessageMeta = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
    
    val SectionHeader = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp
    )

    // Material 3 Mapping Aliases
    val HeadlineLarge = HeaderTitle
    val HeadlineMedium = HeaderTitle.copy(fontSize = 24.sp, lineHeight = 32.sp)
    val HeadlineSmall = HeaderTitle.copy(fontSize = 20.sp, lineHeight = 28.sp)
    val BodyLarge = MessageBody
    val BodyMedium = MessageBody.copy(fontSize = 14.sp, lineHeight = 20.sp)
    val BodySmall = MessageBody.copy(fontSize = 12.sp, lineHeight = 16.sp)
    val LabelSmall = MessageMeta
    val LabelMedium = MessageMeta.copy(fontSize = 12.sp, lineHeight = 16.sp)
}
