package com.matrixmessenger.data.repository

import com.matrixmessenger.data.local.database.dao.RoomDao
import com.matrixmessenger.data.local.database.entity.RoomEntity
import com.matrixmessenger.data.matrix.mapper.toDomainModel
import com.matrixmessenger.data.matrix.mapper.toEntity
import com.matrixmessenger.domain.model.MatrixRoom
import com.matrixmessenger.domain.model.RoomType
import com.matrixmessenger.domain.repository.RoomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.room.Room
import timber.log.Timber

class RoomRepositoryImpl(
    private val sessionProvider: () -> Session?,
    private val roomDao: RoomDao
) : RoomRepository {
    
    override fun getRooms(): Flow<List<MatrixRoom>> {
        return roomDao.getAllRooms().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun createDirectChat(userId: String): Result<String> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            // Create a direct message room
            val roomId = session.createDirectMessageRoom(userId)
            Result.success(roomId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create direct chat")
            Result.failure(e)
        }
    }
    
    override suspend fun createGroup(name: String, topic: String?, userIds: List<String>): Result<String> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val params = org.matrix.android.sdk.api.session.room.creation.CreateRoomParams()
            params.name = name
            params.topic = topic
            params.visibility = org.matrix.android.sdk.api.session.room.model.Visibility.PRIVATE
            
            val roomId = session.createRoom(params)
            
            // Invite users
            userIds.forEach { invitedUserId ->
                try {
                    session.getRoom(roomId)?.invite(invitedUserId)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to invite $invitedUserId")
                }
            }
            
            Result.success(roomId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create group")
            Result.failure(e)
        }
    }
    
    override suspend fun joinRoom(roomIdOrAlias: String): Result<String> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val roomId = session.joinRoom(roomIdOrAlias)
            Result.success(roomId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to join room")
            Result.failure(e)
        }
    }
    
    override suspend fun leaveRoom(roomId: String): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            session.getRoom(roomId)?.leave()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to leave room")
            Result.failure(e)
        }
    }
    
    override suspend fun updateRoomAvatar(roomId: String, avatarUrl: String?): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            if (avatarUrl != null) {
                room.updateAvatar(avatarUrl)
            } else {
                room.removeAvatar()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update room avatar")
            Result.failure(e)
        }
    }
    
    override suspend fun updateRoomName(roomId: String, name: String): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            room.updateName(name)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update room name")
            Result.failure(e)
        }
    }
}
