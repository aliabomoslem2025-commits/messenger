package com.matrixmessenger.feature.message.presentation.renderer

import androidx.compose.runtime.Composable
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.core.designsystem.tokens.MessageGroupPosition

interface MessageRenderer {
    @Composable
    fun Render(
        message: Message,
        isOutgoing: Boolean,
        groupPosition: MessageGroupPosition,
        onStatusClick: (Message) -> Unit
    )
}
