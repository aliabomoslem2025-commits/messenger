package com.matrixmessenger.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Matrix Messenger Dimension Tokens
 * Centralized spacing, sizing, and layout dimensions
 * 
 * Reference: Telegram messenger UI/UX redesign
 */
object MatrixDimens {
    
    // ========== Avatar Sizes ==========
    val AvatarExtraSmall = 24.dp
    val AvatarSmall = 32.dp
    val AvatarMedium = 40.dp
    val AvatarLarge = 48.dp
    val AvatarExtraLarge = 64.dp
    val AvatarHuge = 96.dp
    
    // Avatar online indicator size
    val OnlineIndicatorSize = 12.dp
    val OnlineIndicatorBorderWidth = 2.dp
    
    // ========== Chat List Dimensions ==========
    val ChatListItemHeight = 72.dp
    val ChatListHorizontalPadding = 0.dp
    val ChatListItemVerticalPadding = 8.dp
    val ChatListItemHorizontalPadding = 12.dp
    val ChatListAvatarSpacing = 12.dp
    
    // ========== Header Dimensions ==========
    val HeaderHeight = 56.dp
    val HeaderContentPadding = 16.dp
    val HeaderIconSize = 24.dp
    val HeaderTitleFontSize = 20.sp
    
    // ========== Message Bubble Dimensions ==========
    val MessageMaxWidth = 0.75f  // 75% of screen width
    val MessageBubblePadding = 10.dp
    val MessageContentPadding = 8.dp
    val MessageSpacing = 2.dp       // Between consecutive messages
    val GroupMessageSpacing = 6.dp  // Between message groups
    val MessageAvatarSpacing = 8.dp
    val MessageCornerRadius = 12.dp
    val MessageOutgoingCornerRadius = 16.dp
    val MessageIncomingCornerRadius = 16.dp
    
    // Message bubble corner radii based on grouping
    val MessageCornerSingle = 16.dp
    val MessageCornerFirst = 16.dp
    val MessageCornerMiddle = 4.dp
    val MessageCornerLast = 16.dp
    
    // ========== Message Input Dimensions ==========
    val InputBarHeight = 56.dp
    val InputBarPadding = 8.dp
    val InputTextFieldMinHeight = 40.dp
    val InputTextFieldMaxHeight = 120.dp
    val InputButtonSize = 40.dp
    val InputIconSize = 24.dp
    val InputAttachButtonSize = 40.dp
    val InputSendButtonSize = 48.dp
    val InputReplyBarHeight = 48.dp
    val InputReplyBarCornerRadius = 8.dp
    
    // ========== Badge Dimensions ==========
    val BadgeSize = 20.dp
    val BadgeMinSize = 20.dp
    val BadgePadding = 6.dp
    val BadgeFontSize = 12.sp
    val BadgeCornerRadius = 10.dp
    
    // Mention badge (pill shape)
    val MentionBadgeHeight = 16.dp
    val MentionBadgeCornerRadius = 8.dp
    
    // ========== Spacing Tokens ==========
    val SpacingNone = 0.dp
    val SpacingExtraSmall = 4.dp
    val SpacingSmall = 8.dp
    val SpacingMedium = 12.dp
    val SpacingLarge = 16.dp
    val SpacingExtraLarge = 20.dp
    val SpacingHuge = 24.dp
    val SpacingMassive = 32.dp
    
    // ========== Icon Sizes ==========
    val IconSmall = 16.dp
    val IconMedium = 20.dp
    val IconLarge = 24.dp
    val IconExtraLarge = 28.dp
    val IconHuge = 32.dp
    
    // ========== Button Sizes ==========
    val ButtonSmallHeight = 32.dp
    val ButtonMediumHeight = 40.dp
    val ButtonLargeHeight = 48.dp
    val ButtonCornerRadius = 8.dp
    val IconButtonSize = 40.dp
    val FloatingActionButtonSize = 56.dp
    val FloatingActionButtonMiniSize = 40.dp
    
    // ========== Card & Surface Dimensions ==========
    val CardCornerRadius = 12.dp
    val CardElevation = 0.dp  // Flat design
    val CardStrokeWidth = 0.dp
    
    // ========== Divider Dimensions ==========
    val DividerThickness = 1.dp
    val DividerIndent = 16.dp
    
    // ========== Scroll & Pagination ==========
    val ScrollToBottomButtonSize = 48.dp
    val ScrollToBottomButtonOffset = 16.dp
    val UnreadSeparatorHeight = 32.dp
    
    // ========== Loading States ==========
    val SkeletonCornerRadius = 8.dp
    val ShimmerBaseAlpha = 0.3f
    val ShimmerHighlightAlpha = 0.6f
    
    // ========== Typing Indicator ==========
    val TypingDotSize = 6.dp
    val TypingDotSpacing = 4.dp
    val TypingContainerPadding = 8.dp
    
    // ========== Reaction Dimensions ==========
    val ReactionChipSize = 28.dp
    val ReactionChipCornerRadius = 14.dp
    val ReactionChipElevation = 2.dp
    val ReactionPickerItemSize = 40.dp
    
    // ========== Media Viewer Dimensions ==========
    val MediaThumbnailSize = 80.dp
    val MediaGridSpacing = 2.dp
    val MediaViewerToolbarHeight = 48.dp
    
    // ========== Profile Dimensions ==========
    val ProfileHeaderHeight = 200.dp
    val ProfileAvatarSize = 96.dp
    val ProfileInfoPadding = 24.dp
    
    // ========== Search Dimensions ==========
    val SearchBarHeight = 48.dp
    val SearchBarCornerRadius = 24.dp
    val SearchBarPadding = 12.dp
    
    // ========== Context Menu Dimensions ==========
    val ContextMenuItemHeight = 48.dp
    val ContextMenuCornerRadius = 16.dp
    val ContextMenuElevation = 8.dp
    
    // ========== Tab Dimensions ==========
    val TabHeight = 48.dp
    val TabIndicatorHeight = 2.dp
    val TabPadding = 16.dp
    
    // ========== Navigation Bar Dimensions ==========
    val NavigationBarHeight = 56.dp
    val NavigationBarItemPadding = 4.dp
    
    // ========== Touch Target Minimums (Accessibility) ==========
    val TouchTargetMin = 48.dp
}
