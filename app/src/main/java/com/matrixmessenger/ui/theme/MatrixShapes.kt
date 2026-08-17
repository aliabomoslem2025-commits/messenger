package com.matrixmessenger.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Matrix Messenger Shape System
 * Centralized corner radii and shape definitions
 * 
 * Reference: Telegram messenger - restrained corner radii, clean geometry
 */
object MatrixShapes {
    
    // ========== Corner Radius Tokens ==========
    val CornerNone = 0.dp
    val CornerExtraSmall = 4.dp
    val CornerSmall = 8.dp
    val CornerMedium = 12.dp
    val CornerLarge = 16.dp
    val CornerExtraLarge = 20.dp
    val CornerHuge = 24.dp
    val CornerFull = 9999.dp  // Fully rounded / pill
    
    // ========== Avatar Shapes ==========
    val Avatar = RoundedCornerShape(CornerFull)  // Circular avatars
    
    // ========== Message Bubble Shapes ==========
    // Outgoing message (right side)
    val MessageBubbleOutgoingTop = RoundedCornerShape(
        topStart = CornerLarge,
        topEnd = CornerLarge,
        bottomStart = CornerLarge,
        bottomEnd = CornerNone
    )
    
    val MessageBubbleOutgoingBottom = RoundedCornerShape(
        topStart = CornerLarge,
        topEnd = CornerLarge,
        bottomStart = CornerNone,
        bottomEnd = CornerLarge
    )
    
    val MessageBubbleOutgoingMiddle = RoundedCornerShape(
        topStart = CornerMedium,
        topEnd = CornerMedium,
        bottomStart = CornerMedium,
        bottomEnd = CornerNone
    )
    
    val MessageBubbleOutgoingSingle = RoundedCornerShape(
        topStart = CornerLarge,
        topEnd = CornerLarge,
        bottomStart = CornerLarge,
        bottomEnd = CornerMedium
    )
    
    // Incoming message (left side)
    val MessageBubbleIncomingTop = RoundedCornerShape(
        topStart = CornerLarge,
        topEnd = CornerLarge,
        bottomStart = CornerNone,
        bottomEnd = CornerLarge
    )
    
    val MessageBubbleIncomingBottom = RoundedCornerShape(
        topStart = CornerNone,
        topEnd = CornerLarge,
        bottomStart = CornerLarge,
        bottomEnd = CornerLarge
    )
    
    val MessageBubbleIncomingMiddle = RoundedCornerShape(
        topStart = CornerMedium,
        topEnd = CornerMedium,
        bottomStart = CornerNone,
        bottomEnd = CornerMedium
    )
    
    val MessageBubbleIncomingSingle = RoundedCornerShape(
        topStart = CornerMedium,
        topEnd = CornerLarge,
        bottomStart = CornerLarge,
        bottomEnd = CornerLarge
    )
    
    // ========== Card & Surface Shapes ==========
    val CardSmall = RoundedCornerShape(CornerSmall)
    val CardMedium = RoundedCornerShape(CornerMedium)
    val CardLarge = RoundedCornerShape(CornerLarge)
    
    // Chat list item
    val ChatListItem = RoundedCornerShape(CornerNone)  // Full width, no corners
    
    // ========== Button Shapes ==========
    val Button = RoundedCornerShape(CornerMedium)
    val ButtonSmall = RoundedCornerShape(CornerSmall)
    val ButtonPill = RoundedCornerShape(CornerFull)
    
    // ========== Input Shapes ==========
    val MessageInput = RoundedCornerShape(CornerLarge)
    val SearchBar = RoundedCornerShape(CornerFull)
    val TextField = RoundedCornerShape(CornerMedium)
    
    // ========== Badge Shapes ==========
    val Badge = RoundedCornerShape(CornerFull)
    val BadgeSmall = RoundedCornerShape(CornerFull)
    
    // ========== Dialog & Bottom Sheet Shapes ==========
    val Dialog = RoundedCornerShape(CornerLarge)
    val BottomSheet = RoundedCornerShape(topStart = CornerLarge, topEnd = CornerLarge)
    val ContextMenu = RoundedCornerShape(CornerLarge)
    
    // ========== Media Shapes ==========
    val MediaThumbnail = RoundedCornerShape(CornerSmall)
    val MediaGridItem = RoundedCornerShape(CornerExtraSmall)
    
    // ========== Chip Shapes ==========
    val Chip = RoundedCornerShape(CornerFull)
    val ChipSmall = RoundedCornerShape(CornerFull)
    
    // ========== FAB Shapes ==========
    val FloatingActionButton = RoundedCornerShape(CornerFull)
    val FloatingActionButtonMini = RoundedCornerShape(CornerFull)
    
    /**
     * Get message bubble shape based on position and direction
     * 
     * @param isOutgoing true for outgoing (right), false for incoming (left)
     * @param position Single, First, Middle, Last in a group
     */
    fun getMessageBubbleShape(
        isOutgoing: Boolean,
        position: MessageGroupPosition
    ): RoundedCornerShape {
        return when {
            isOutgoing -> when (position) {
                MessageGroupPosition.Single -> MessageBubbleOutgoingSingle
                MessageGroupPosition.First -> MessageBubbleOutgoingTop
                MessageGroupPosition.Middle -> MessageBubbleOutgoingMiddle
                MessageGroupPosition.Last -> MessageBubbleOutgoingBottom
            }
            else -> when (position) {
                MessageGroupPosition.Single -> MessageBubbleIncomingSingle
                MessageGroupPosition.First -> MessageBubbleIncomingTop
                MessageGroupPosition.Middle -> MessageBubbleIncomingMiddle
                MessageGroupPosition.Last -> MessageBubbleIncomingBottom
            }
        }
    }
}

/**
 * Message grouping position for bubble shape calculation
 */
enum class MessageGroupPosition {
    Single,   // Only message in group
    First,    // First message in group (top)
    Middle,   // Middle message in group
    Last      // Last message in group (bottom)
}
