package com.matrixmessenger.domain.usecase.message

import com.matrixmessenger.domain.repository.MessageRepository

class EditMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(roomId: String, eventId: String, newText: String): Result<Unit> {
        return messageRepository.editMessage(roomId, eventId, newText)
    }
}
