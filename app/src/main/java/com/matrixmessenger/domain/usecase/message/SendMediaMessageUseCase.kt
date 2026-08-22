package com.matrixmessenger.domain.usecase.message

import android.net.Uri
import com.matrixmessenger.domain.repository.MessageRepository

class SendMediaMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(roomId: String, mediaUri: Uri, mimeType: String, caption: String? = null): Result<Unit> {
        return messageRepository.sendMediaMessage(roomId, mediaUri, mimeType, caption)
    }
}
