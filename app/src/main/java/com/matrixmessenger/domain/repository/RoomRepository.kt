package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixRoom
import kotlinx.coroutines.flow.Flow

interface RoomRepository {
    fun getRooms(): Flow<List<MatrixRoom>>
    suspend fun createDirectChat(userId: String): Result<String>
    suspend fun createGroup(name: String, topic: String, userIds: List<String>): Result<String>
    suspend fun joinRoom(roomIdOrAlias: String): Result<String>
    suspend fun leaveRoom(roomId: String): Result<Unit>
}
