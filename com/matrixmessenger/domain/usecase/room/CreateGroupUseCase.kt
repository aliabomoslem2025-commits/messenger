package com.matrixmessenger.domain.usecase.room

import com.matrixmessenger.domain.repository.RoomRepository

class CreateGroupUseCase(private val roomRepository: RoomRepository) {
    suspend operator fun invoke(name: String, topic: String?, userIds: List<String>): Result<String> {
        return roomRepository.createGroup(name, topic, userIds)
    }
}
