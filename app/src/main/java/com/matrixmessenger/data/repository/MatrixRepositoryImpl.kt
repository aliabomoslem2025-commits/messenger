package com.matrixmessenger.data.repository

import android.content.Context
import com.matrixmessenger.data.local.AppPreferences
import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.data.matrix.mapper.MessageMapper
import com.matrixmessenger.data.matrix.mapper.RoomMapper
import com.matrixmessenger.domain.model.*
import com.matrixmessenger.domain.model.MessageType as DomainMessageType
import com.matrixmessenger.domain.model.PresenceState as DomainPresenceState
import com.matrixmessenger.domain.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.auth.registration.RegistrationResult
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.RoomMemberSummary
import org.matrix.android.sdk.api.session.room.model.ReadReceipt
import org.matrix.android.sdk.api.session.room.model.create.CreateRoomParams
import org.matrix.android.sdk.api.session.room.model.message.*
import org.matrix.android.sdk.api.session.room.RoomSortOrder
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.room.sender.SenderInfo
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import org.matrix.android.sdk.api.session.room.timeline.hasBeenEdited
import org.matrix.android.sdk.api.session.sync.SyncState
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import org.matrix.android.sdk.api.session.presence.model.PresenceEnum
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.room.roomSummaryQueryParams
import org.matrix.android.sdk.api.session.room.members.roomMemberQueryParams
import org.matrix.android.sdk.api.failure.Failure
import androidx.lifecycle.asFlow
import timber.log.Timber
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences,
    private val matrixClientManager: MatrixClientManager,
    private val messageMapper: MessageMapper,
    private val roomMapper: RoomMapper
) : MatrixRepository {

    override suspend fun login(username: String, password: String, homeserverUrl: String): Result<AuthData> {
        return matrixClientManager.loginWithPassword(homeserverUrl, username, password).mapCatching { session ->
            val authData = AuthData(
                userId = session.myUserId,
                accessToken = session.sessionParams.credentials.accessToken,
                deviceId = session.sessionParams.credentials.deviceId,
                homeserverUrl = homeserverUrl,
                refreshToken = session.sessionParams.credentials.refreshToken
            )
            appPreferences.saveAuthData(authData)
            authData
        }
    }

    override suspend fun register(username: String, password: String, homeserverUrl: String): Result<AuthData> {
        return matrixClientManager.register(homeserverUrl, username, password).mapCatching { session ->
            val authData = AuthData(
                userId = session.myUserId,
                accessToken = session.sessionParams.credentials.accessToken,
                deviceId = session.sessionParams.credentials.deviceId,
                homeserverUrl = homeserverUrl,
                refreshToken = session.sessionParams.credentials.refreshToken
            )
            appPreferences.saveAuthData(authData)
            authData
        }
    }

    override suspend fun logout(): Result<Unit> {
        return matrixClientManager.logout().onSuccess {
            appPreferences.clearAuthData()
        }
    }

    override suspend fun getAuthState(): AuthData? = appPreferences.authState.first()

    override fun observeAuthState(): Flow<AuthData?> = appPreferences.authState

    override suspend fun getCurrentUserId(): String? = matrixClientManager.getCurrentUserId()

    override suspend fun getMyUserProfile(): Result<UserProfile> {
        val userId = matrixClientManager.getCurrentUserId() ?: return Result.failure(Exception("Not logged in"))
        return matrixClientManager.getUserProfile(userId).map { user ->
            UserProfile(
                userId = user.userId,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
                email = null,
                phone = null,
                bio = null
            )
        }
    }

    override suspend fun updateDisplayName(displayName: String): Result<Unit> {
        return matrixClientManager.updateDisplayName(displayName)
    }

    override suspend fun updateAvatar(avatarFile: File): Result<String> {
        return matrixClientManager.updateAvatar(android.net.Uri.fromFile(avatarFile)).map {
            matrixClientManager.getCurrentSession()?.profileService()?.getAvatarUrl(matrixClientManager.getCurrentUserId() ?: "")?.getOrNull() ?: ""
        }
    }

    override suspend fun removeAvatar(): Result<Unit> {
        return matrixClientManager.updateAvatar(android.net.Uri.EMPTY)
    }

    override suspend fun setPassword(oldPassword: String, newPassword: String): Result<Unit> {
        return Result.failure(Exception("Not implemented"))
    }

    override suspend fun deleteAccount(password: String): Result<Unit> {
        return Result.failure(Exception("Not implemented"))
    }

    override fun observeRooms(): Flow<List<MatrixRoom>> {
        return matrixClientManager.getRoomsFlow().map { summaries ->
            summaries.map { summary ->
                val isEncrypted = matrixClientManager.isRoomEncrypted(summary.roomId)
                val room = matrixClientManager.getRoom(summary.roomId)
                val notificationState = room?.roomPushRuleService()?.getLiveRoomNotificationState()?.value
                roomMapper.map(summary, isEncrypted, notificationState)
            }
        }
    }

    override suspend fun getRoom(roomId: String): Result<MatrixRoom> {
        return runCatching {
            val room = matrixClientManager.getRoom(roomId) ?: throw Exception("Room $roomId not found")
            val summary = room.roomSummary() ?: throw Exception("Room summary not found")
            val isEncrypted = matrixClientManager.isRoomEncrypted(roomId)
            val notificationState = room.roomPushRuleService().getLiveRoomNotificationState().value
            roomMapper.map(summary, isEncrypted, notificationState)
        }
    }

    override suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean,
        invitedUserIds: List<String>,
        isEncrypted: Boolean
    ): Result<String> {
        return matrixClientManager.createGroup(name ?: "New Chat", topic ?: "", invitedUserIds)
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

    override suspend fun kickUser(roomId: String, userId: String, reason: String?): Result<Unit> {
        return matrixClientManager.kickUser(roomId, userId, reason)
    }

    override suspend fun banUser(roomId: String, userId: String, reason: String?): Result<Unit> {
        return matrixClientManager.banUser(roomId, userId, reason)
    }

    override suspend fun unbanUser(roomId: String, userId: String): Result<Unit> {
        return matrixClientManager.unbanUser(roomId, userId)
    }

    override suspend fun updateRoomName(roomId: String, name: String): Result<Unit> {
        return matrixClientManager.updateRoomName(roomId, name)
    }

    override suspend fun updateRoomTopic(roomId: String, topic: String): Result<Unit> {
        return matrixClientManager.updateRoomTopic(roomId, topic)
    }

    override suspend fun updateRoomAvatar(roomId: String, avatarFile: File): Result<String> {
        return matrixClientManager.updateRoomAvatar(roomId, android.net.Uri.fromFile(avatarFile)).map {
            matrixClientManager.getRoom(roomId)?.roomSummary()?.avatarUrl ?: ""
        }
    }

    override suspend fun removeRoomAvatar(roomId: String): Result<Unit> {
        return matrixClientManager.updateRoomAvatar(roomId, android.net.Uri.EMPTY)
    }

    override suspend fun getRoomMembers(roomId: String): Result<List<RoomMember>> {
        return runCatching {
            val room = matrixClientManager.getRoom(roomId) ?: throw Exception("Room not found")
            room.membershipService().getRoomMembers(roomMemberQueryParams { }).map { 
                it.toDomainRoomMember()
            }
        }
    }

    override fun observeRoomMembers(roomId: String): Flow<List<RoomMember>> {
        val room = matrixClientManager.getRoom(roomId) ?: return emptyFlow()
        return room.membershipService().getRoomMembersLive(roomMemberQueryParams { }).asFlow().map { members ->
            members.map { it.toDomainRoomMember() }
        }
    }

    override fun observeMessages(roomId: String, limit: Int): Flow<List<MatrixMessage>> {
        val currentUserId = matrixClientManager.getCurrentUserId() ?: ""
        return matrixClientManager.getTimelineEventFlow(roomId, limit)
            .map { events ->
                val latestOtherReadReceiptTs = events.asSequence()
                    .flatMap { it.readReceipts.asSequence() }
                    .filter { it.roomMember.userId != currentUserId }
                    .map { it.originServerTs }
                    .maxOrNull() ?: 0L

                events.filter { it.roomId == roomId }
                    .map { messageMapper.mapToMatrixMessage(it, currentUserId, latestOtherReadReceiptTs) }
                    .sortedByDescending { it.timestamp }
            }
    }

    override suspend fun sendMessage(
        roomId: String,
        body: String,
        formattedBody: String?,
        messageType: DomainMessageType,
        replyToEventId: String?
    ): Result<String> {
        return runCatching {
            if (replyToEventId != null) {
                 val room = matrixClientManager.getRoom(roomId) ?: throw Exception("Room not found")
                 val event = room.timelineService().getTimelineEvent(replyToEventId) ?: throw Exception("Original event not found")
                 matrixClientManager.sendReply(roomId, body, event).getOrThrow()
            } else {
                if (formattedBody != null) {
                    matrixClientManager.sendFormattedMessage(roomId, body, formattedBody).getOrThrow()
                } else {
                    matrixClientManager.sendTextMessage(roomId, body).getOrThrow()
                }
            }
            "local_id_${System.currentTimeMillis()}"
        }
    }

    override suspend fun loadMoreMessages(roomId: String, limit: Int): Result<Unit> {
        return matrixClientManager.paginateTimeline(roomId, backward = true, limit = limit)
    }

    override suspend fun sendTextMessage(roomId: String, text: String, replyToId: String?): Result<String> {
        return sendMessage(roomId, text, null, DomainMessageType.TEXT, replyToId)
    }

    override suspend fun sendImageMessage(roomId: String, imageFile: File, caption: String?, mimeType: String): Result<String> {
        return matrixClientManager.sendImageMessage(roomId, imageFile, caption).map { "local_id_${System.currentTimeMillis()}" }
    }

    override suspend fun sendVideoMessage(roomId: String, videoFile: File, caption: String?, thumbnailFile: File?): Result<String> {
        return matrixClientManager.sendVideo(roomId, android.net.Uri.fromFile(videoFile), caption).map { "local_id_${System.currentTimeMillis()}" }
    }

    override suspend fun sendAudioMessage(roomId: String, audioFile: File, durationMs: Long, caption: String?): Result<String> {
        return matrixClientManager.sendVoiceMessage(roomId, android.net.Uri.fromFile(audioFile), durationMs, emptyList()).map { "local_id_${System.currentTimeMillis()}" }
    }

    override suspend fun sendVideoNote(roomId: String, videoFile: File, durationMs: Long, width: Int, height: Int): Result<String> {
        return matrixClientManager.sendVideoNote(roomId, android.net.Uri.fromFile(videoFile), durationMs, width, height).map { "local_id_${System.currentTimeMillis()}" }
    }

    override suspend fun sendFileMessage(roomId: String, file: File, caption: String?): Result<String> {
        return matrixClientManager.sendFile(roomId, android.net.Uri.fromFile(file)).map { "local_id_${System.currentTimeMillis()}" }
    }

    override suspend fun editMessage(roomId: String, originalEventId: String, newBody: String, newFormattedBody: String?): Result<Unit> {
        return matrixClientManager.editMessage(roomId, originalEventId, newBody)
    }

    override suspend fun deleteMessage(roomId: String, eventId: String): Result<Unit> {
        return matrixClientManager.deleteMessage(roomId, eventId, null)
    }

    override suspend fun redactEvent(roomId: String, eventId: String, reason: String?): Result<Unit> {
        return matrixClientManager.deleteMessage(roomId, eventId, reason)
    }

    override suspend fun sendReaction(roomId: String, eventId: String, key: String): Result<Unit> {
        return matrixClientManager.sendReaction(roomId, eventId, key)
    }

    override suspend fun removeReaction(roomId: String, eventId: String, key: String): Result<Unit> {
        return matrixClientManager.removeReaction(roomId, eventId, key)
    }

    override suspend fun markRoomAsRead(roomId: String): Result<Unit> {
        return matrixClientManager.markAsRead(roomId)
    }

    override suspend fun markRoomAsUnread(roomId: String): Result<Unit> {
        return Result.failure(Exception("Not supported by Matrix SDK"))
    }

    override suspend fun resendMessage(roomId: String, localId: String): Result<Unit> {
        return matrixClientManager.resendMessage(roomId, localId)
    }

    override suspend fun cancelSend(roomId: String, localId: String): Result<Unit> {
        return matrixClientManager.cancelSend(roomId, localId)
    }

    override suspend fun searchUsers(query: String, limit: Int): Result<List<MatrixUser>> {
        return matrixClientManager.searchUsers(query)
    }

    override suspend fun searchMessages(roomId: String, query: String, limit: Int): Result<List<MatrixMessage>> {
        return matrixClientManager.searchMessages(query, roomId).map { events ->
             val currentUserId = matrixClientManager.getCurrentUserId() ?: ""
             events.map { messageMapper.mapToMatrixMessage(it.toTimelineEvent(), currentUserId) }
        }
    }

    override suspend fun getPublicRooms(server: String?, filter: String?, limit: Int): Result<List<PublicRoom>> {
        return Result.success(emptyList())
    }

    override suspend fun sendTypingNotification(roomId: String, isTyping: Boolean): Result<Unit> {
        return matrixClientManager.sendTyping(roomId, isTyping)
    }

    override suspend fun setPresence(presence: DomainPresenceState, statusMessage: String?): Result<Unit> {
        val enum = when (presence) {
            DomainPresenceState.ONLINE -> PresenceEnum.ONLINE
            DomainPresenceState.UNAVAILABLE -> PresenceEnum.UNAVAILABLE
            DomainPresenceState.OFFLINE -> PresenceEnum.OFFLINE
            DomainPresenceState.UNKNOWN -> PresenceEnum.OFFLINE
        }
        return matrixClientManager.setPresence(enum)
    }

    override fun observePresence(userId: String): Flow<DomainPresenceState> {
        return matrixClientManager.observePresence(userId)
    }

    override suspend fun downloadMedia(url: String, file: File): Result<Unit> {
        return runCatching {
            matrixClientManager.getCurrentSession()?.fileService()?.downloadFile(url, null, null, null)
            Unit
        }
    }

    override suspend fun getMediaCacheFile(url: String): File? = null

    override fun clearMediaCache() {}

    override fun startSync() {
        matrixClientManager.getCurrentSession()?.syncService()?.startSync(true)
    }

    override fun stopSync() {
        matrixClientManager.getCurrentSession()?.syncService()?.stopSync()
    }

    override fun isSyncing(): Boolean = matrixClientManager.syncState.value is SyncState.Running

    override suspend fun initiateCall(roomId: String, type: CallType): Result<String> = Result.failure(Exception("Not implemented"))
    override suspend fun answerCall(callId: String): Result<Unit> = Result.failure(Exception("Not implemented"))
    override suspend fun rejectCall(callId: String): Result<Unit> = Result.failure(Exception("Not implemented"))
    override suspend fun endCall(callId: String): Result<Unit> = Result.failure(Exception("Not implemented"))
    override fun observeIncomingCalls(): Flow<IncomingCall> = emptyFlow()

    private fun RoomMemberSummary.toDomainRoomMember(): RoomMember = RoomMember(
        userId = this.userId,
        displayName = this.displayName,
        avatarUrl = this.avatarUrl,
        membership = when (this.membership) {
            Membership.INVITE -> MembershipState.INVITE
            Membership.JOIN -> MembershipState.JOIN
            Membership.LEAVE -> MembershipState.LEAVE
            Membership.BAN -> MembershipState.BAN
            Membership.KNOCK -> MembershipState.KNOCK
            else -> MembershipState.LEAVE
        },
        powerLevel = 0,
        presence = DomainPresenceState.OFFLINE
    )
    
    private fun Event.toTimelineEvent(): TimelineEvent {
        return TimelineEvent(
            root = this,
            localId = 0,
            eventId = this.eventId ?: "",
            displayIndex = 0,
            senderInfo = SenderInfo(userId = this.senderId ?: "", displayName = null, isUniqueDisplayName = true, avatarUrl = null)
        )
    }
}
