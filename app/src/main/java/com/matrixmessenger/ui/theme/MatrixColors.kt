package com.matrixmessenger.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Matrix Messenger Color Palette
 * Based on Telegram dark mode aesthetic - deep navy surfaces with blue accents
 * 
 * Reference: https://www.behance.net/gallery/77706627/Telegram-messenger-UI-UX-Redesign
 */
object MatrixColors {
    
    // ========== Background Colors ==========
    val BackgroundPrimary = Color(0xFF0E1621)      // Main app background
    val BackgroundSecondary = Color(0xFF17212B)    // Card/chat list item background
    val BackgroundTertiary = Color(0xFF0F1A25)     // Secondary surfaces
    
    // ========== Surface Colors ==========
    val SurfacePrimary = Color(0xFF17212B)         // Primary surface (cards, dialogs)
    val SurfaceSecondary = Color(0xFF202B36)       // Secondary surface (hover states)
    val SurfaceTertiary = Color(0xFF2A3540)        // Tertiary surface (pressed states)
    
    // ========== Message Bubble Colors ==========
    val IncomingBubble = Color(0xFF17212B)         // Incoming message bubble
    val OutgoingBubble = Color(0xFF2B5278)         // Outgoing message bubble (blue)
    val OutgoingBubblePressed = Color(0xFF3A6288)  // Outgoing bubble pressed state
    
    // ========== Text Colors ==========
    val TextPrimary = Color(0xFFFFFFFF)            // Primary text
    val TextSecondary = Color(0xFF7F91A4)          // Secondary text (timestamps, hints)
    val TextTertiary = Color(0xFF5C6B7A)           // Tertiary text (disabled)
    val TextOnAccent = Color(0xFF0D2F4E)           // Text on accent colors
    val TextLink = Color(0xFF5AA0F0)               // Link text
    
    // ========== Accent Colors ==========
    val AccentPrimary = Color(0xFF5AA0F0)          // Primary accent (buttons, links)
    val AccentSecondary = Color(0xFF4A8FE0)        // Secondary accent (pressed)
    val AccentContainer = Color(0xFF0D2F4E)        // Accent container
    
    // ========== Status Colors ==========
    val Online = Color(0xFF4CD964)                 // Online indicator (green)
    val Away = Color(0xFFFF9500)                   // Away indicator (orange)
    val Offline = Color(0xFF5C6B7A)                // Offline indicator (gray)
    val Error = Color(0xFFCF6679)                  // Error state
    val Warning = Color(0xFFFFAB40)                // Warning state
    
    // ========== Badge Colors ==========
    val UnreadBadge = Color(0xFF5AA0F0)            // Unread count badge
    val MentionBadge = Color(0xFF4CD964)           // Mention badge (green)
    val UnreadBadgeMuted = Color(0xFF5C6B7A)       // Muted unread badge
    
    // ========== Divider & Separator Colors ==========
    val Divider = Color(0xFF202B36)                // Standard divider
    val DividerLight = Color(0xFF2A3540)           // Light divider
    
    // ========== Icon Colors ==========
    val IconPrimary = Color(0xFFFFFFFF)            // Primary icons
    val IconSecondary = Color(0xFF7F91A4)          // Secondary icons
    val IconOnAccent = Color(0xFF0D2F4E)           // Icons on accent backgrounds
    
    // ========== Overlay Colors ==========
    val OverlayScrim = Color(0x99000000)           // Dark overlay for modals
    val OverlayLight = Color(0x40FFFFFF)           // Light overlay
    
    // ========== Selection Colors ==========
    val SelectionOverlay = Color(0x405AA0F0)       // Message selection overlay
    
    // ========== Skeleton Loading Colors ==========
    val SkeletonBase = Color(0xFF17212B)           // Skeleton base color
    val SkeletonHighlight = Color(0xFF2A3540)      // Skeleton highlight
    
    /**
     * Get color scheme for Material 3 integration
     */
    fun toColorScheme(): androidx.compose.material3.ColorScheme {
        return androidx.compose.material3.darkColorScheme(
            primary = AccentPrimary,
            onPrimary = TextOnAccent,
            primaryContainer = AccentContainer,
            onPrimaryContainer = TextPrimary,
            
            secondary = AccentSecondary,
            onSecondary = TextOnAccent,
            secondaryContainer = SurfaceSecondary,
            onSecondaryContainer = TextPrimary,
            
            tertiary = TextLink,
            onTertiary = TextOnAccent,
            
            background = BackgroundPrimary,
            onBackground = TextPrimary,
            
            surface = SurfacePrimary,
            onSurface = TextPrimary,
            surfaceVariant = BackgroundSecondary,
            onSurfaceVariant = TextSecondary,
            
            error = Error,
            onError = TextPrimary,
            
            outline = Divider,
            outlineVariant = DividerLight
        )
    }
}
