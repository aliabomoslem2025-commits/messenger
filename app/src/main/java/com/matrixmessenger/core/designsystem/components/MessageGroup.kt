package com.matrixmessenger.core.designsystem.components

import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.core.designsystem.tokens.MessageGroupPosition
import kotlin.time.Duration.Companion.minutes

/**
 * Logic for calculating message group position
 */
fun getMessageGroupPosition(
    index: Int,
    messages: List<Message>
): MessageGroupPosition {
    if (messages.isEmpty()) return MessageGroupPosition.Single
    
    val current = messages[index]
    val prev = if (index > 0) messages[index - 1] else null
    val next = if (index < messages.size - 1) messages[index + 1] else null

    val isFirst = prev == null || prev.senderId != current.senderId || 
                 (current.timestamp.time - prev.timestamp.time) > 5.minutes.inWholeMilliseconds
    
    val isLast = next == null || next.senderId != current.senderId || 
                (next.timestamp.time - current.timestamp.time) > 5.minutes.inWholeMilliseconds

    return when {
        isFirst && isLast -> MessageGroupPosition.Single
        isFirst -> MessageGroupPosition.First
        isLast -> MessageGroupPosition.Last
        else -> MessageGroupPosition.Middle
    }
}
