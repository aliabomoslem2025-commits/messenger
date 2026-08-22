package com.matrixmessenger.domain.usecase.room

import com.matrixmessenger.domain.repository.RoomRepository

class CreateDirectChatUseCase(private val roomRepository: RoomRepository) {
    suspend operator fun invoke(userId: String): Result<String> {
        return roomRepository.createDirectChat(userId)
    }
}
