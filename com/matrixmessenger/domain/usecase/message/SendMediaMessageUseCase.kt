package com.matrixmessenger.domain.usecase.message

import com.matrixmessenger.domain.repository.MessageRepository

class SendMediaMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(roomId: String, mediaUri: String, mimeType: String, caption: String? = null): Result<String> {
        return messageRepository.sendMediaMessage(roomId, mediaUri, mimeType, caption)
    }
}
