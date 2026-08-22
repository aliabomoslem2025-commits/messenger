package com.matrixmessenger.feature.message.presentation.components

import androidx.compose.runtime.Composable
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.domain.model.MessageType

@Composable
fun MessageContent(
    message: Message
) {
    when (message.messageType) {
        MessageType.TEXT -> {
            TextMessage(text = message.body)
        }
        MessageType.IMAGE -> {
            val attachment = message.attachments.firstOrNull()
            ImageMessage(
                url = attachment?.url ?: "",
                caption = if (message.body != attachment?.fileName) message.body else null
            )
        }
        MessageType.AUDIO -> {
            val attachment = message.attachments.firstOrNull()
            VoiceMessage(
                duration = attachment?.duration?.toString() ?: "0:00"
            )
        }
        MessageType.VIDEO_NOTE -> {
            VideoNoteBubble(message = message)
        }
        else -> {
            TextMessage(text = message.body) // Fallback
        }
    }
}
