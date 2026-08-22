package com.matrixmessenger.domain.usecase.message

import com.matrixmessenger.domain.repository.MessageRepository

class SendReactionUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(roomId: String, eventId: String, emoji: String): Result<Unit> {
        return messageRepository.sendReaction(roomId, eventId, emoji)
    }
}
