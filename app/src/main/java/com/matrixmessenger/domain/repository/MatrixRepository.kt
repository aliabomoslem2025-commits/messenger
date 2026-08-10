package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

interface MatrixRepository {
    
    // Authentication
    suspend fun login(username: String, password: String, homeserverUrl: String): Result<AuthData>
    suspend fun register(username: String, password: String, homeserverUrl: String): Result<AuthData>
    suspend fun logout(): Result<Unit>
    suspend fun getAuthState(): AuthData?
    fun observeAuthState(): Flow<AuthData?>
    
    // Account
    suspend fun getMyUserProfile(): Result<UserProfile>
    suspend fun updateDisplayName(displayName: String): Result<Unit>
    suspend fun updateAvatar(avatarFile: File): Result<String>
    suspend fun removeAvatar(): Result<Unit>
    suspend fun setPassword(oldPassword: String, newPassword: String): Result<Unit>
    suspend fun deleteAccount(password: String): Result<Unit>
    
    // Rooms
    fun observeRooms(): Flow<List<Room>>
    suspend fun getRoom(roomId: String): Result<Room>
    suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean = false,
        invitedUserIds: List<String> = emptyList(),
        isEncrypted: Boolean = true
    ): Result<String>
    suspend fun joinRoom(roomIdOrAlias: String): Result<String>
    suspend fun leaveRoom(roomId: String): Result<Unit>
    suspend fun inviteUser(roomId: String, userId: String): Result<Unit>
    suspend fun kickUser(roomId: String, userId: String, reason: String? = null): Result<Unit>
    suspend fun banUser(roomId: String, userId: String, reason: String? = null): Result<Unit>
    suspend fun unbanUser(roomId: String, userId: String): Result<Unit>
    suspend fun updateRoomName(roomId: String, name: String): Result<Unit>
    suspend fun updateRoomTopic(roomId: String, topic: String): Result<Unit>
    suspend fun updateRoomAvatar(roomId: String, avatarFile: File): Result<String>
    suspend fun removeRoomAvatar(roomId: String): Result<Unit>
    suspend fun getRoomMembers(roomId: String): Result<List<RoomMember>>
    
    // Messages
    fun observeMessages(roomId: String, limit: Int = 50): Flow<List<Message>>
    suspend fun sendMessage(
        roomId: String,
        body: String,
        formattedBody: String? = null,
        messageType: MessageType = MessageType.TEXT,
        replyToEventId: String? = null
    ): Result<String>
    suspend fun sendImageMessage(
        roomId: String,
        imageFile: File,
        caption: String? = null,
        mimeType: String = "image/jpeg"
    ): Result<String>
    suspend fun sendVideoMessage(
        roomId: String,
        videoFile: File,
        caption: String? = null,
        thumbnailFile: File? = null
    ): Result<String>
    suspend fun sendAudioMessage(
        roomId: String,
        audioFile: File,
        caption: String? = null
    ): Result<String>
    suspend fun sendFileMessage(
        roomId: String,
        file: File,
        caption: String? = null
    ): Result<String>
    suspend fun editMessage(
        roomId: String,
        originalEventId: String,
        newBody: String,
        newFormattedBody: String? = null
    ): Result<Unit>
    suspend fun deleteMessage(roomId: String, eventId: String): Result<Unit>
    suspend fun redactEvent(roomId: String, eventId: String, reason: String? = null): Result<Unit>
    suspend fun sendReaction(roomId: String, eventId: String, key: String): Result<Unit>
    suspend fun removeReaction(roomId: String, eventId: String, key: String): Result<Unit>
    suspend fun markRoomAsRead(roomId: String): Result<Unit>
    suspend fun markRoomAsUnread(roomId: String): Result<Unit>
    
    // Search
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<MatrixUser>>
    suspend fun searchMessages(roomId: String, query: String, limit: Int = 20): Result<List<Message>>
    suspend fun getPublicRooms(server: String? = null, filter: String? = null, limit: Int = 20): Result<List<PublicRoom>>
    
    // Typing & Presence
    suspend fun sendTypingNotification(roomId: String, isTyping: Boolean): Result<Unit>
    suspend fun setPresence(presence: PresenceState, statusMessage: String? = null): Result<Unit>
    fun observePresence(userId: String): Flow<PresenceState>
    
    // Media
    suspend fun downloadMedia(url: String, file: File): Result<Unit>
    suspend fun getMediaCacheFile(url: String): File?
    fun clearMediaCache()
    
    // Sync
    fun startSync()
    fun stopSync()
    fun isSyncing(): Boolean
    
    // Calls (VoIP)
    suspend fun initiateCall(roomId: String, type: CallType): Result<String>
    suspend fun answerCall(callId: String): Result<Unit>
    suspend fun rejectCall(callId: String): Result<Unit>
    suspend fun endCall(callId: String): Result<Unit>
    fun observeIncomingCalls(): Flow<IncomingCall>
}

data class AuthData(
    val userId: String,
    val accessToken: String,
    val deviceId: String,
    val homeserverUrl: String,
    val refreshToken: String? = null
)

data class RoomMember(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val membership: MembershipState,
    val powerLevel: Int = 0,
    val presence: PresenceState = PresenceState.OFFLINE
)

data class PublicRoom(
    val roomId: String,
    val name: String?,
    val topic: String?,
    val avatarUrl: String?,
    val memberCount: Int,
    val canonicalAlias: String?,
    val isEncrypted: Boolean
)

enum class CallType {
    VOICE,
    VIDEO
}

data class IncomingCall(
    val callId: String,
    val roomId: String,
    val callerId: String,
    val callerDisplayName: String?,
    val type: CallType,
    val timestamp: Long
)
