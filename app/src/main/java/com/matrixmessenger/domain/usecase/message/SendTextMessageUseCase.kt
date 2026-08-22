package com.matrixmessenger.domain.usecase.message

import com.matrixmessenger.domain.repository.MessageRepository

class SendTextMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(roomId: String, text: String, replyToEventId: String? = null): Result<Unit> {
        return messageRepository.sendTextMessage(roomId, text, replyToEventId)
    }
}
