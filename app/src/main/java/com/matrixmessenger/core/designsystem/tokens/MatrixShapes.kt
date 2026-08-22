package com.matrixmessenger.core.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Telegram-style shapes with dynamic grouping support.
 */
object MatrixShapes {
    val CornerNone = 0.dp
    val CornerSmall = 4.dp
    val CornerMedium = 12.dp
    val CornerLarge = 20.dp
    val CornerFull = 9999.dp
    
    // Components
    val Card = RoundedCornerShape(CornerMedium)
    val CardSmall = RoundedCornerShape(CornerSmall)
    val CardMedium = Card
    val CardLarge = RoundedCornerShape(CornerLarge)
    val Button = RoundedCornerShape(CornerLarge)
    val Avatar = RoundedCornerShape(CornerFull)
    val Badge = RoundedCornerShape(CornerFull)
    
    // Message Bubbles
    val BubbleRadiusLarge = 18.dp
    val BubbleRadiusSmall = 6.dp
    
    /**
     * Get bubble shape based on grouping.
     */
    fun getBubbleShape(
        isOutgoing: Boolean,
        isFirst: Boolean,
        isLast: Boolean
    ): RoundedCornerShape {
        val topStart = if (!isOutgoing && !isFirst) BubbleRadiusSmall else BubbleRadiusLarge
        val topEnd = if (isOutgoing && !isFirst) BubbleRadiusSmall else BubbleRadiusLarge
        val bottomStart = if (!isOutgoing && !isLast) BubbleRadiusSmall else BubbleRadiusLarge
        val bottomEnd = if (isOutgoing && !isLast) BubbleRadiusSmall else BubbleRadiusLarge
        
        return RoundedCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomStart = bottomStart,
            bottomEnd = bottomEnd
        )
    }
}
