package com.matrixmessenger.feature.message.presentation.renderer

import androidx.compose.runtime.Composable
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.domain.model.MessageType
import com.matrixmessenger.core.designsystem.tokens.MessageGroupPosition
import com.matrixmessenger.feature.message.presentation.components.MessageBubble
import com.matrixmessenger.feature.message.presentation.components.MessageContent
import javax.inject.Inject
import javax.inject.Provider

class MessageRendererImpl @Inject constructor(
    private val videoNoteRenderer: Provider<VideoNoteRenderer>
) : MessageRenderer {

    @Composable
    override fun Render(
        message: Message,
        isOutgoing: Boolean,
        groupPosition: MessageGroupPosition,
        onStatusClick: (Message) -> Unit
    ) {
        when (message.messageType) {
            MessageType.VIDEO_NOTE -> {
                videoNoteRenderer.get().Render(
                    message = message,
                    isOutgoing = isOutgoing,
                    groupPosition = groupPosition,
                    onStatusClick = onStatusClick
                )
            }
            else -> {
                MessageBubble(
                    message = message,
                    isOutgoing = isOutgoing,
                    groupPosition = groupPosition,
                    onStatusClick = onStatusClick
                ) {
                    MessageContent(message = message)
                }
            }
        }
    }
}
