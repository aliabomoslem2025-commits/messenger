package com.matrixmessenger.data.repository

import android.content.Context
import com.matrixmessenger.BuildConfig
import com.matrixmessenger.data.local.AppPreferences
import com.matrixmessenger.domain.model.*
import com.matrixmessenger.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.MatrixConfiguration
import org.matrix.android.sdk.api.MatrixCallback
import org.matrix.android.sdk.api.SessionHolder
import org.matrix.android.sdk.api.auth.UserIdentifier
import org.matrix.android.sdk.api.auth.authentication.AuthenticationService
import org.matrix.android.sdk.api.auth.registration.RegistrationResult
import org.matrix.android.sdk.api.auth.registration.RegistrationWizard
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.crypto.CryptoService
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.message.FileInfo
import org.matrix.android.sdk.api.session.room.model.message.ImageInfo
import org.matrix.android.sdk.api.session.room.model.message.MessageAudioContent
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageFileContent
import org.matrix.android.sdk.api.session.room.model.message.MessageImageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageTextContent
import org.matrix.android.sdk.api.session.room.model.message.MessageVideoContent
import org.matrix.android.sdk.api.session.room.model.message.VideoInfo
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) : MatrixRepository {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var session: Session? = null
    private var syncState: SyncState? = null
    
    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    private val _messagesMap = mutableMapOf<String, MutableStateFlow<List<Message>>>()
    private val _presenceMap = mutableMapOf<String, MutableStateFlow<PresenceState>>()
    private val _incomingCalls = MutableStateFlow<IncomingCall?>(null)
    
    override suspend fun login(username: String, password: String, homeserverUrl: String): Result<AuthData> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Attempting login for user: $username at $homeserverUrl")
                
                // Get or create authentication service
                val authService = getAuthenticationService(homeserverUrl)
                
                // Perform login
                val authData = authService.login(UserIdentifier(userIdOrEmail = username, password = password))
                
                Timber.d("Login successful for user: ${authData.userId}")
                
                // Save auth data to preferences
                appPreferences.saveAuthData(
                    AuthData(
                        userId = authData.userId,
                        accessToken = authData.accessToken,
                        deviceId = authData.deviceId,
                        homeserverUrl = homeserverUrl,
                        refreshToken = authData.refreshToken
                    )
                )
                
                // Initialize session
                initializeSession(authData.userId, authData.accessToken, homeserverUrl)
                
                Result.success(
                    AuthData(
                        userId = authData.userId,
                        accessToken = authData.accessToken,
                        deviceId = authData.deviceId,
                        homeserverUrl = homeserverUrl,
                        refreshToken = authData.refreshToken
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Login failed")
                Result.failure(mapMatrixError(e))
            }
        }
    }
    
    override suspend fun register(username: String, password: String, homeserverUrl: String): Result<AuthData> {
        return withContext(Dispatchers.IO) {
            try {
                Timber.d("Attempting registration for user: $username at $homeserverUrl")
                
                val authService = getAuthenticationService(homeserverUrl)
                
                // Start registration flow
                val registrationWizard = authService.getRegistrationWizard()
                
                // Check if username is available
                registrationWizard.checkUsernameAvailability(username)
                
                // Register with password
                val result = registrationWizard.registerWithPassword(
                    username = username,
                    password = password,
                    initialDeviceDisplayName = "MatrixMessenger Android"
                )
                
                // Complete registration
                val authData = when (result) {
                    is RegistrationResult.Success -> {
                        authService.login(UserIdentifier(userIdOrEmail = username, password = password))
                    }
                    is RegistrationResult.Failure -> {
                        throw Exception("Registration failed: ${result.error}")
                    }
                }
                
                Timber.d("Registration successful for user: ${authData.userId}")
                
                appPreferences.saveAuthData(
                    AuthData(
                        userId = authData.userId,
                        accessToken = authData.accessToken,
                        deviceId = authData.deviceId,
                        homeserverUrl = homeserverUrl,
                        refreshToken = authData.refreshToken
                    )
                )
                
                initializeSession(authData.userId, authData.accessToken, homeserverUrl)
                
                Result.success(
                    AuthData(
                        userId = authData.userId,
                        accessToken = authData.accessToken,
                        deviceId = authData.deviceId,
                        homeserverUrl = homeserverUrl,
                        refreshToken = authData.refreshToken
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Registration failed")
                Result.failure(mapMatrixError(e))
            }
        }
    }
    
    override suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                stopSync()
                session?.clearCryptoData()
                session = null
                appPreferences.clearAuthData()
                _rooms.value = emptyList()
                _messagesMap.clear()
                Timber.d("Logout successful")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getAuthState(): AuthData? {
        return appPreferences.authState.first()
    }
    
    override fun observeAuthState(): Flow<AuthData?> {
        return appPreferences.authState
    }
    
    override suspend fun getMyUserProfile(): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val myUserId = session?.myUserId ?: return@withContext Result.failure(Exception("Not logged in"))
                val user = session?.getUser(myUserId)
                
                val profile = UserProfile(
                    userId = myUserId,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatarUrl,
                    email = null,
                    phone = null,
                    bio = null,
                    threePids = emptyList()
                )
                
                Result.success(profile)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get user profile")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateDisplayName(displayName: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.setMyDisplayName(displayName)
                appPreferences.setDisplayName(displayName)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update display name")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateAvatar(avatarFile: File): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val contentUri = session?.uploadContent(avatarFile)
                contentUri?.let { uri ->
                    session?.setMyAvatarUrl(uri)
                    appPreferences.setAvatarUrl(uri)
                    Result.success(uri)
                } ?: Result.failure(Exception("Failed to upload avatar"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to update avatar")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun removeAvatar(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.setMyAvatarUrl("")
                appPreferences.setAvatarUrl("")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove avatar")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun setPassword(oldPassword: String, newPassword: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Password change requires re-authentication
                // This is a simplified implementation
                Result.failure(Exception("Password change not fully implemented"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to change password")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun deleteAccount(password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Account deletion is irreversible
                // Requires careful implementation
                Result.failure(Exception("Account deletion not fully implemented"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete account")
                Result.failure(e)
            }
        }
    }
    
    override fun observeRooms(): Flow<List<Room>> {
        return _rooms.asStateFlow()
    }
    
    override suspend fun getRoom(roomId: String): Result<Room> {
        return withContext(Dispatchers.IO) {
            try {
                val matrixRoom = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                Result.success(matrixRoom.toDomainRoom())
            } catch (e: Exception) {
                Timber.e(e, "Failed to get room")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean,
        invitedUserIds: List<String>,
        isEncrypted: Boolean
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val params = org.matrix.android.sdk.api.session.room.model.CreateRoomParams().apply {
                    this.name = name
                    this.topic = topic
                    this.invitedUserIds = invitedUserIds
                    this.isDirect = isDirect
                    if (isEncrypted) {
                        enableEncryption()
                    }
                }
                
                val roomId = session?.createRoom(params) ?: throw Exception("Failed to create room")
                Timber.d("Room created: $roomId")
                
                Result.success(roomId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to create room")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun joinRoom(roomIdOrAlias: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val roomId = session?.joinRoom(roomIdOrAlias)
                    ?: throw Exception("Failed to join room")
                Timber.d("Joined room: $roomId")
                Result.success(roomId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to join room")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun leaveRoom(roomId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.leave(roomId)
                Timber.d("Left room: $roomId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to leave room")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun inviteUser(roomId: String, userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.invite(roomId, userId)
                Timber.d("Invited $userId to $roomId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to invite user")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun kickUser(roomId: String, userId: String, reason: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.kick(roomId, userId, reason)
                Timber.d("Kicked $userId from $roomId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to kick user")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun banUser(roomId: String, userId: String, reason: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.ban(roomId, userId, reason)
                Timber.d("Banned $userId from $roomId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to ban user")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun unbanUser(roomId: String, userId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.unban(roomId, userId)
                Timber.d("Unbanned $userId from $roomId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to unban user")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateRoomName(roomId: String, name: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.updateRoomName(roomId, name)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update room name")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateRoomTopic(roomId: String, topic: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.updateRoomTopic(roomId, topic)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update room topic")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateRoomAvatar(roomId: String, avatarFile: File): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val contentUri = session?.uploadContent(avatarFile)
                contentUri?.let { uri ->
                    session?.updateRoomAvatar(roomId, uri)
                    Result.success(uri)
                } ?: Result.failure(Exception("Failed to upload avatar"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to update room avatar")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun removeRoomAvatar(roomId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.updateRoomAvatar(roomId, "")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove room avatar")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getRoomMembers(roomId: String): Result<List<RoomMember>> {
        return withContext(Dispatchers.IO) {
            try {
                val members = session?.getRoomMembers(roomId)
                    ?.map { it.toDomainRoomMember() }
                    ?: emptyList()
                Result.success(members)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get room members")
                Result.failure(e)
            }
        }
    }
    
    override fun observeMessages(roomId: String, limit: Int): Flow<List<Message>> {
        return _messagesMap.getOrPut(roomId) {
            MutableStateFlow(emptyList())
        }.asStateFlow()
    }
    
    override suspend fun sendMessage(
        roomId: String,
        body: String,
        formattedBody: String?,
        messageType: MessageType,
        replyToEventId: String?
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                val eventId = when (messageType) {
                    MessageType.TEXT -> {
                        if (formattedBody != null) {
                            room.sendTextMessage(body, formattedText = formattedBody, autoDetectLinks = true)
                        } else {
                            room.sendTextMessage(body, autoDetectLinks = true)
                        }
                    }
                    MessageType.EMOTE -> {
                        room.sendEmoteMessage(body)
                    }
                    MessageType.NOTICE -> {
                        room.sendTextMessage(body)
                    }
                    else -> {
                        throw Exception("Unsupported message type")
                    }
                }
                
                Timber.d("Message sent: $eventId")
                Result.success(eventId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send message")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun sendImageMessage(
        roomId: String,
        imageFile: File,
        caption: String?,
        mimeType: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                val info = ImageInfo().apply {
                    width = imageFile.width
                    height = imageFile.height
                    size = imageFile.length()
                    mimeType = mimeType
                }
                
                val eventId = room.sendImageMessage(imageFile, info, caption ?: imageFile.name)
                Timber.d("Image sent: $eventId")
                Result.success(eventId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send image")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun sendVideoMessage(
        roomId: String,
        videoFile: File,
        caption: String?,
        thumbnailFile: File?
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                val info = VideoInfo().apply {
                    size = videoFile.length()
                    mimeType = "video/mp4"
                }
                
                val eventId = room.sendVideoMessage(videoFile, info, caption ?: videoFile.name)
                Timber.d("Video sent: $eventId")
                Result.success(eventId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send video")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun sendAudioMessage(
        roomId: String,
        audioFile: File,
        caption: String?
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                val eventId = room.sendAudioMessage(audioFile, caption ?: audioFile.name)
                Timber.d("Audio sent: $eventId")
                Result.success(eventId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send audio")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun sendFileMessage(
        roomId: String,
        file: File,
        caption: String?
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                val info = FileInfo().apply {
                    size = file.length()
                    mimeType = "application/octet-stream"
                }
                
                val eventId = room.sendFileMessage(file, info, caption ?: file.name)
                Timber.d("File sent: $eventId")
                Result.success(eventId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send file")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun editMessage(
        roomId: String,
        originalEventId: String,
        newBody: String,
        newFormattedBody: String?
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                room.editTextMessage(originalEventId, newBody, newFormattedBody)
                Timber.d("Message edited: $originalEventId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to edit message")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun deleteMessage(roomId: String, eventId: String): Result<Unit> {
        return redactEvent(roomId, eventId, null)
    }
    
    override suspend fun redactEvent(roomId: String, eventId: String, reason: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                room.redactEvent(eventId, reason)
                Timber.d("Event redacted: $eventId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to redact event")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun sendReaction(roomId: String, eventId: String, key: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                room.sendReaction(eventId, key)
                Timber.d("Reaction sent: $key to $eventId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send reaction")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun removeReaction(roomId: String, eventId: String, key: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val room = session?.getRoom(roomId)
                    ?: return@withContext Result.failure(Exception("Room not found"))
                
                room.undoReaction(eventId, key)
                Timber.d("Reaction removed: $key from $eventId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove reaction")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun markRoomAsRead(roomId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.markAllAsRead(roomId)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark room as read")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun markRoomAsUnread(roomId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Mark as unread by setting read marker to earliest event
                session?.fullyReadMarker(roomId, null)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to mark room as unread")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun searchUsers(query: String, limit: Int): Result<List<MatrixUser>> {
        return withContext(Dispatchers.IO) {
            try {
                val users = session?.searchUsers(query, limit)
                    ?.map { it.toDomainUser() }
                    ?: emptyList()
                Result.success(users)
            } catch (e: Exception) {
                Timber.e(e, "Failed to search users")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun searchMessages(roomId: String, query: String, limit: Int): Result<List<Message>> {
        return withContext(Dispatchers.IO) {
            try {
                val events = session?.searchInRoom(roomId, query, limit)
                    ?.map { it.toDomainMessage() }
                    ?: emptyList()
                Result.success(events)
            } catch (e: Exception) {
                Timber.e(e, "Failed to search messages")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getPublicRooms(server: String?, filter: String?, limit: Int): Result<List<PublicRoom>> {
        return withContext(Dispatchers.IO) {
            try {
                val rooms = session?.getPublicRooms(server, filter, limit)
                    ?.map { room ->
                        PublicRoom(
                            roomId = room.roomId,
                            name = room.name,
                            topic = room.topic,
                            avatarUrl = room.avatarUrl,
                            memberCount = room.numMembers,
                            canonicalAlias = room.canonicalAlias,
                            isEncrypted = room.isEncrypted
                        )
                    }
                    ?: emptyList()
                Result.success(rooms)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get public rooms")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun sendTypingNotification(roomId: String, isTyping: Boolean): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.typingNotice(roomId, isTyping)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to send typing notification")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun setPresence(presence: PresenceState, statusMessage: String?): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val matrixPresence = when (presence) {
                    PresenceState.ONLINE -> org.matrix.android.sdk.api.session.presence.model.PresenceState.ONLINE
                    PresenceState.UNAVAILABLE -> org.matrix.android.sdk.api.session.presence.model.PresenceState.UNAVAILABLE
                    PresenceState.OFFLINE -> org.matrix.android.sdk.api.session.presence.model.PresenceState.OFFLINE
                    PresenceState.UNKNOWN -> org.matrix.android.sdk.api.session.presence.model.PresenceState.OFFLINE
                }
                
                session?.setPresence(matrixPresence, statusMessage)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to set presence")
                Result.failure(e)
            }
        }
    }
    
    override fun observePresence(userId: String): Flow<PresenceState> {
        return _presenceMap.getOrPut(userId) {
            MutableStateFlow(PresenceState.OFFLINE)
        }.asStateFlow()
    }
    
    override suspend fun downloadMedia(url: String, file: File): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                session?.downloadFile(url, file)
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download media")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getMediaCacheFile(url: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val fileName = url.substringAfterLast('/')
                val cacheFile = File(appPreferences.getMediaCacheDir(), fileName)
                if (cacheFile.exists()) {
                    cacheFile
                } else {
                    null
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get media cache file")
                null
            }
        }
    }
    
    override fun clearMediaCache() {
        appPreferences.getMediaCacheDir().deleteRecursively()
        Timber.d("Media cache cleared")
    }
    
    override fun startSync() {
        if (syncState != null && syncState?.isSyncing == true) {
            Timber.d("Sync already running")
            return
        }
        
        try {
            syncState = session?.startFullSync()
            Timber.d("Sync started")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start sync")
        }
    }
    
    override fun stopSync() {
        syncState?.stop()
        syncState = null
        Timber.d("Sync stopped")
    }
    
    override fun isSyncing(): Boolean {
        return syncState?.isSyncing == true
    }
    
    override suspend fun initiateCall(roomId: String, type: CallType): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                // VoIP call implementation using WebRTC
                // This requires additional setup and is complex
                Result.failure(Exception("VoIP calls not fully implemented yet"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to initiate call")
                Result.failure(e)
            }
        }
    }
    
    override suspend fun answerCall(callId: String): Result<Unit> {
        return Result.failure(Exception("VoIP calls not fully implemented yet"))
    }
    
    override suspend fun rejectCall(callId: String): Result<Unit> {
        return Result.failure(Exception("VoIP calls not fully implemented yet"))
    }
    
    override suspend fun endCall(callId: String): Result<Unit> {
        return Result.failure(Exception("VoIP calls not fully implemented yet"))
    }
    
    override fun observeIncomingCalls(): Flow<IncomingCall> {
        return _incomingCalls.asStateFlow()
    }
    
    // Helper methods
    
    private suspend fun getAuthenticationService(homeserverUrl: String): AuthenticationService {
        val matrix = Matrix.getInstance(context)
        val authService = matrix.getAuthenticationService(
            MatrixConfiguration(
                homeserverUrl = homeserverUrl,
                sessionStoreDirectory = context.filesDir.resolve("matrix_sessions").absolutePath
            )
        )
        return authService
    }
    
    private suspend fun initializeSession(userId: String, accessToken: String, homeserverUrl: String) {
        val matrix = Matrix.getInstance(context)
        session = matrix.createSession(
            sessionId = userId,
            configuration = MatrixConfiguration(
                homeserverUrl = homeserverUrl,
                sessionStoreDirectory = context.filesDir.resolve("matrix_sessions").absolutePath
            ),
            credentials = org.matrix.android.sdk.api.auth.Credentials(
                userId = userId,
                accessToken = accessToken,
                deviceId = "",
                homeServer = homeserverUrl
            )
        )
        
        // Set up room listeners
        setupRoomListeners()
        
        // Start sync
        startSync()
    }
    
    private fun setupRoomListeners() {
        session?.addListener(object : org.matrix.android.sdk.api.listener.SessionListener {
            override fun onInitialSyncDone() {
                Timber.d("Initial sync completed")
                loadRooms()
            }
            
            override fun onSyncFailed(error: Throwable) {
                Timber.e(error, "Sync failed")
            }
        })
    }
    
    private fun loadRooms() {
        coroutineScope.launch {
            try {
                val matrixRooms = session?.getRooms() ?: emptyList()
                _rooms.value = matrixRooms.map { it.toDomainRoom() }
                
                // Load messages for each room
                matrixRooms.forEach { room ->
                    loadRoomMessages(room.roomId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load rooms")
            }
        }
    }
    
    private fun loadRoomMessages(roomId: String) {
        coroutineScope.launch {
            try {
                val room = session?.getRoom(roomId) ?: return@launch
                
                val timeline = room.getTimeline(
                    TimelineSettings(
                        initialNumberOfEventsToLoad = 50,
                        encryptHistory = false
                    )
                )
                
                timeline.addListener(object : org.matrix.android.sdk.api.session.room.timeline.Timeline.Listener {
                    override fun onNewTimelineEvents(eventIds: List<String>) {
                        // Reload messages when new events arrive
                        loadMessagesForRoom(roomId)
                    }
                })
                
                loadMessagesForRoom(roomId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load messages for room: $roomId")
            }
        }
    }
    
    private fun loadMessagesForRoom(roomId: String) {
        coroutineScope.launch {
            try {
                val room = session?.getRoom(roomId) ?: return@launch
                val events = room.getTimeline()?.getSnapshot() ?: emptyList()
                
                val messages = events.mapNotNull { event ->
                    event.toDomainMessage()
                }
                
                _messagesMap.getOrPut(roomId) { MutableStateFlow(emptyList()) }.value = messages
            } catch (e: Exception) {
                Timber.e(e, "Failed to load messages")
            }
        }
    }
    
    private fun Throwable.toDomainError(): Exception {
        return when (this) {
            is Failure.NetworkError -> Exception("Network error: ${this.message}")
            is Failure.ApiError -> Exception("API error: ${this.httpCode}")
            is Failure.CryptoError -> Exception("Crypto error: ${this.message}")
            else -> Exception(this.message ?: "Unknown error")
        }
    }
    
    private fun mapMatrixError(e: Exception): Exception {
        return when (e) {
            is Failure -> e.toDomainError()
            else -> e
        }
    }
}

// Extension functions to convert Matrix SDK models to domain models

private fun org.matrix.android.sdk.api.session.room.Room.toDomainRoom(): Room {
    return Room(
        roomId = this.roomId,
        name = this.name,
        topic = this.summary?.topic,
        avatarUrl = this.summary?.avatarUrl,
        isDirect = this.isDirect,
        canonicalAlias = this.canonicalAlias,
        inviter = null,
        membership = this.membership.toDomainMembership(),
        unreadCount = this.summary?.notificationCount ?: 0,
        lastMessage = null, // Would need to fetch from timeline
        timestamp = this.summary?.lastMessageTimestamp?.let { java.util.Date(it) },
        isEncrypted = this.isEncrypted(),
        memberCount = this.summary?.joinedMemberCount ?: 0
    )
}

private fun Membership.toDomainMembership(): MembershipState {
    return when (this) {
        Membership.INVITE -> MembershipState.INVITE
        Membership.JOIN -> MembershipState.JOIN
        Membership.LEAVE -> MembershipState.LEAVE
        Membership.BAN -> MembershipState.BAN
        Membership.KNOCK -> MembershipState.KNOCK
        Membership.UNKNOWN -> MembershipState.LEAVE
    }
}

private fun org.matrix.android.sdk.api.session.room.model.RoomMember.toDomainRoomMember(): RoomMember {
    return RoomMember(
        userId = this.userId,
        displayName = this.displayName,
        avatarUrl = this.avatarUrl,
        membership = this.membership.toDomainMembership(),
        powerLevel = this.powerLevel,
        presence = PresenceState.OFFLINE // Would need to fetch from presence service
    )
}

private fun org.matrix.android.sdk.api.session.user.User.toDomainUser(): MatrixUser {
    return MatrixUser(
        userId = this.userId,
        displayName = this.displayName,
        avatarUrl = this.avatarUrl,
        presence = PresenceState.UNKNOWN
    )
}

private fun TimelineEvent.toDomainMessage(): Message? {
    val content = this.getClearContent() ?: this.root.content.toModel<MessageContent>()
    
    val messageType = when (content) {
        is MessageTextContent -> MessageType.TEXT
        is MessageImageContent -> MessageType.IMAGE
        is MessageVideoContent -> MessageType.VIDEO
        is MessageAudioContent -> MessageType.AUDIO
        is MessageFileContent -> MessageType.FILE
        else -> MessageType.TEXT
    }
    
    return Message(
        eventId = this.eventId,
        roomId = this.roomId,
        senderId = this.senderId,
        senderDisplayName = null,
        senderAvatarUrl = null,
        body = content?.body ?: "",
        formattedBody = null,
        messageType = messageType,
        timestamp = this.originServerTs?.let { java.util.Date(it) } ?: java.util.Date(),
        isEdited = this.isEdited(),
        isDeleted = false,
        reactions = emptyList(),
        replyToEventId = null,
        attachments = emptyList(),
        deliveryStatus = this.sendState.toDomainDeliveryStatus()
    )
}

private fun SendState.toDomainDeliveryStatus(): DeliveryStatus {
    return when (this) {
        SendState.SENT -> DeliveryStatus.SENT
        SendState.SENDING -> DeliveryStatus.SENDING
        SendState.UNDELIVERABLE -> DeliveryStatus.FAILED
        SendState.FAILURE -> DeliveryStatus.FAILED
    }
}
