package com.matrixmessenger.domain.usecase.message

import com.matrixmessenger.domain.repository.MessageRepository

class DeleteMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(roomId: String, eventId: String): Result<Unit> {
        return messageRepository.deleteMessage(roomId, eventId)
    }
}
