package com.matrixmessenger.domain.usecase.room

import com.matrixmessenger.domain.repository.RoomRepository

class JoinRoomUseCase(private val roomRepository: RoomRepository) {
    suspend operator fun invoke(roomIdOrAlias: String): Result<String> {
        return roomRepository.joinRoom(roomIdOrAlias)
    }
}
