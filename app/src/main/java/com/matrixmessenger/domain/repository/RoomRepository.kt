package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixRoom
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for room operations
 */
interface RoomRepository {
    /**
     * Get list of rooms as a Flow
     */
    fun observeRooms(): Flow<List<MatrixRoom>>
    
    /**
     * Get specific room by ID
     */
    suspend fun getRoom(roomId: String): Result<MatrixRoom>
    
    /**
     * Create a new room
     */
    suspend fun createRoom(
        name: String? = null,
        topic: String? = null,
        isDirect: Boolean = false,
        invitedUserIds: List<String> = emptyList(),
        isEncrypted: Boolean = true
    ): Result<String>
    
    /**
     * Create a direct chat with a user
     */
    suspend fun createDirectChat(userId: String): Result<String>
    
    /**
     * Join a room by ID or alias
     */
    suspend fun joinRoom(roomIdOrAlias: String): Result<String>
    
    /**
     * Leave a room
     */
    suspend fun leaveRoom(roomId: String): Result<Unit>
    
    /**
     * Forget a left room
     */
    suspend fun forgetRoom(roomId: String): Result<Unit>
    
    /**
     * Invite a user to a room
     */
    suspend fun inviteUser(roomId: String, userId: String, reason: String? = null): Result<Unit>
    
    /**
     * Kick a user from a room
     */
    suspend fun kickUser(roomId: String, userId: String, reason: String? = null): Result<Unit>
    
    /**
     * Ban a user from a room
     */
    suspend fun banUser(roomId: String, userId: String, reason: String? = null): Result<Unit>
    
    /**
     * Unban a user from a room
     */
    suspend fun unbanUser(roomId: String, userId: String): Result<Unit>
    
    /**
     * Update room name
     */
    suspend fun updateRoomName(roomId: String, name: String): Result<Unit>
    
    /**
     * Update room topic
     */
    suspend fun updateRoomTopic(roomId: String, topic: String): Result<Unit>
    
    /**
     * Update room avatar
     */
    suspend fun updateRoomAvatar(roomId: String, avatarUrl: String): Result<Unit>
    
    /**
     * Remove room avatar
     */
    suspend fun removeRoomAvatar(roomId: String): Result<Unit>
    
    /**
     * Get room members
     */
    suspend fun getRoomMembers(roomId: String): Result<List<MatrixRoom.Member>>
    
    /**
     * Get joined room members only
     */
    suspend fun getJoinedMembers(roomId: String): Result<List<MatrixRoom.Member>>
    
    /**
     * Mark room as read
     */
    suspend fun markRoomAsRead(roomId: String): Result<Unit>
    
    /**
     * Get public rooms list
     */
    suspend fun getPublicRooms(server: String? = null, filter: String? = null, limit: Int = 20): Result<List<MatrixRoom>>
}
