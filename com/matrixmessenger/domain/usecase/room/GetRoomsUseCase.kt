package com.matrixmessenger.domain.usecase.room

import com.matrixmessenger.domain.model.MatrixRoom
import com.matrixmessenger.domain.repository.RoomRepository
import kotlinx.coroutines.flow.Flow

class GetRoomsUseCase(private val roomRepository: RoomRepository) {
    operator fun invoke(): Flow<List<MatrixRoom>> {
        return roomRepository.getRooms()
    }
}
