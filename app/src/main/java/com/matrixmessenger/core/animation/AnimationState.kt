package com.matrixmessenger.core.animation

import androidx.compose.runtime.Immutable

/**
 * States for swipe-to-reply Telegram-style interaction.
 */
sealed interface SwipeReplyState {
    object Idle : SwipeReplyState
    data class Dragging(val offset: Float) : SwipeReplyState
    object Triggered : SwipeReplyState
}

/**
 * Message effect types for premium animations.
 */
enum class MessageEffectType {
    NONE,
    LOVE,
    FIRE,
    CONFETTI,
    LAUGH,
    STAR
}

/**
 * State for message action animations (e.g. selection, long press).
 */
@Immutable
data class MessageActionState(
    val isSelected: Boolean = false,
    val isLongPressed: Boolean = false,
    val progress: Float = 0f
)
