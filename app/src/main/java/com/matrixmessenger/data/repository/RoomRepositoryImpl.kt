package com.matrixmessenger.data.repository

import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.data.matrix.mapper.RoomMapper
import com.matrixmessenger.domain.model.MatrixRoom
import com.matrixmessenger.domain.repository.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager,
    private val roomMapper: RoomMapper
) : RoomRepository {

    override fun getRooms(): Flow<List<MatrixRoom>> {
        return matrixClientManager.getRoomsFlow().map { list ->
            list.map { 
                val isEncrypted = matrixClientManager.isRoomEncrypted(it.roomId)
                roomMapper.map(it, isEncrypted)
            }
        }
    }

    override suspend fun createDirectChat(userId: String): Result<String> {
        return matrixClientManager.createDirectChat(userId)
    }

    override suspend fun createGroup(name: String, topic: String, userIds: List<String>): Result<String> {
        return matrixClientManager.createGroup(name, topic, userIds)
    }

    override suspend fun joinRoom(roomIdOrAlias: String): Result<String> {
        return matrixClientManager.joinRoom(roomIdOrAlias)
    }

    override suspend fun leaveRoom(roomId: String): Result<Unit> {
        return matrixClientManager.leaveRoom(roomId)
    }
}
