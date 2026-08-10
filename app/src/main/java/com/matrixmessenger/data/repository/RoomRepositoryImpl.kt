package com.matrixmessenger.data.repository

import com.matrixmessenger.data.local.database.dao.RoomDao
import com.matrixmessenger.data.local.database.entity.RoomEntity
import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.data.matrix.mapper.RoomMapper
import com.matrixmessenger.domain.model.MatrixRoom
import com.matrixmessenger.domain.repository.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.matrix.android.sdk.api.session.room.model.Membership
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager,
    private val roomDao: RoomDao,
    private val roomMapper: RoomMapper
) : RoomRepository {

    override fun getRoomsFlow(): Flow<List<MatrixRoom>> {
        return matrixClientManager.getRoomsFlow()
            .map { summaries ->
                summaries
                    .filter { it.membership == Membership.JOIN }
                    .sortedByDescending { it.latestPreviewableEvent?.root?.originServerTs }
                    .map { roomMapper.mapToMatrixRoom(it) }
            }
    }

    override fun getRoomFlow(roomId: String): Flow<MatrixRoom?> {
        return matrixClientManager.getRoomSummaryFlow(roomId)
            .map { optional ->
                optional.getOrNull()?.let { roomMapper.mapToMatrixRoom(it) }
            }
    }

    override suspend fun createDirectChat(userId: String): Result<String> {
        return matrixClientManager.createDirectChat(userId)
    }

    override suspend fun createGroup(
        name: String,
        topic: String,
        userIds: List<String>
    ): Result<String> {
        return matrixClientManager.createGroup(name, topic, userIds)
    }

    override suspend fun joinRoom(roomIdOrAlias: String): Result<String> {
        return matrixClientManager.joinRoom(roomIdOrAlias)
    }

    override suspend fun leaveRoom(roomId: String): Result<Unit> {
        return matrixClientManager.leaveRoom(roomId)
    }

    override suspend fun inviteUser(roomId: String, userId: String): Result<Unit> {
        return matrixClientManager.inviteUser(roomId, userId)
    }

    override suspend fun kickUser(
        roomId: String,
        userId: String,
        reason: String?
    ): Result<Unit> {
        return matrixClientManager.kickUser(roomId, userId, reason)
    }

    override suspend fun updateRoomName(roomId: String, name: String): Result<Unit> {
        return matrixClientManager.updateRoomName(roomId, name)
    }

    override suspend fun updateRoomTopic(roomId: String, topic: String): Result<Unit> {
        return matrixClientManager.updateRoomTopic(roomId, topic)
    }

    override suspend fun updateRoomAvatar(
        roomId: String,
        uri: android.net.Uri
    ): Result<Unit> {
        return matrixClientManager.updateRoomAvatar(roomId, uri)
    }

    override fun getTypingUsersFlow(roomId: String): Flow<List<String>> {
        return matrixClientManager.getTypingUsersFlow(roomId)
    }

    override fun getUnreadCountFlow(roomId: String): Flow<Int> {
        return matrixClientManager.getUnreadCountFlow(roomId)
    }
}
