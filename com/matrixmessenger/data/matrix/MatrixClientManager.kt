package com.matrixmessenger.data.matrix

import android.content.Context
import com.matrixmessenger.data.local.preferences.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.MatrixConfiguration
import org.matrix.android.sdk.api.auth.AuthenticationService
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.auth.data.LoginFlowResult
import org.matrix.android.sdk.api.auth.registration.RegistrationResult
import org.matrix.android.sdk.api.auth.registration.Stage
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.SessionLifecycleObserver
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.events.model.toContent
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.failure.JoinRoomFailure
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.create.CreateRoomParams
import org.matrix.android.sdk.api.session.room.model.create.RoomPreset
import org.matrix.android.sdk.api.session.room.send.UserDraft
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import org.matrix.android.sdk.api.session.sync.SyncState
import org.matrix.android.sdk.api.util.Optional
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixClientManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var matrix: Matrix
    private var currentSession: Session? = null

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _sessionState = MutableStateFlow<SessionState>(SessionState.NoSession)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    sealed class SessionState {
        object NoSession : SessionState()
        object Loading : SessionState()
        data class Active(val userId: String) : SessionState()
        data class Error(val message: String) : SessionState()
    }

    fun initialize() {
        matrix = Matrix.getInstance(context)
        tryRestoreSession()
    }

    private fun tryRestoreSession() {
        managerScope.launch {
            val lastSession = matrix.authenticationService().getLastAuthenticatedSession()
            if (lastSession != null) {
                currentSession = lastSession
                openSession(lastSession)
            }
        }
    }

    private fun openSession(session: Session) {
        session.open()

        session.addListener(object : SessionLifecycleObserver {
            override fun onSessionStarted(session: Session) {
                _sessionState.value = SessionState.Active(session.myUserId)
            }

            override fun onSessionStopped(session: Session) {
                _sessionState.value = SessionState.NoSession
            }
        })

        session.syncService().startSync(isInBackground = false)

        managerScope.launch {
            session.syncService().getSyncStateFlow()
                .collect { state ->
                    _syncState.value = state
                }
        }

        _sessionState.value = SessionState.Active(session.myUserId)
    }

    // ── Authentication ──────────────────────────────────

    suspend fun getLoginFlows(homeserverUrl: String): Result<LoginFlowResult> {
        return runCatching {
            val config = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(android.net.Uri.parse(homeserverUrl))
                .build()
            matrix.authenticationService().getLoginFlow(config)
        }
    }

    suspend fun loginWithPassword(
        homeserverUrl: String,
        login: String,
        password: String
    ): Result<Session> {
        return runCatching {
            _sessionState.value = SessionState.Loading
            val config = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(android.net.Uri.parse(homeserverUrl))
                .build()
            matrix.authenticationService().getLoginFlow(config)
            val loginWizard = matrix.authenticationService().getLoginWizard()
            val session = loginWizard.login(
                login = login,
                password = password,
                deviceName = "Matrix Messenger Android"
            )
            currentSession = session
            appPreferences.saveHomeserverUrl(homeserverUrl)
            openSession(session)
            session
        }.onFailure {
            _sessionState.value = SessionState.Error(it.message ?: "Login failed")
            Timber.e(it, "Login failed")
        }
    }

    suspend fun loginWithSso(homeserverUrl: String, token: String): Result<Session> {
        return runCatching {
            _sessionState.value = SessionState.Loading
            val config = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(android.net.Uri.parse(homeserverUrl))
                .build()
            matrix.authenticationService().getLoginFlow(config)
            val loginWizard = matrix.authenticationService().getLoginWizard()
            val session = loginWizard.loginWithToken(token)
            currentSession = session
            openSession(session)
            session
        }
    }

    suspend fun register(
        homeserverUrl: String,
        username: String,
        password: String
    ): Result<Session> {
        return runCatching {
            _sessionState.value = SessionState.Loading
            val config = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(android.net.Uri.parse(homeserverUrl))
                .build()
            matrix.authenticationService().getLoginFlow(config)

            val registrationWizard = matrix.authenticationService().getRegistrationWizard()

            val result = registrationWizard.createAccount(
                userName = username,
                password = password,
                initialDeviceName = "Matrix Messenger"
            )

            when (result) {
                is RegistrationResult.Success -> {
                    val session = result.session
                    currentSession = session
                    openSession(session)
                    session
                }
                is RegistrationResult.FlowResponse -> {
                    // Handle additional registration stages (captcha, email, etc.)
                    var currentResult: RegistrationResult = result
                    var session: Session? = null

                    while (currentResult is RegistrationResult.FlowResponse && session == null) {
                        val nextStage = (currentResult as RegistrationResult.FlowResponse)
                            .flowResult.missingStages.firstOrNull()

                        currentResult = when (nextStage) {
                            is Stage.ReCaptcha -> {
                                registrationWizard.performReCaptcha(nextStage.publicKey)
                            }
                            is Stage.Dummy -> {
                                registrationWizard.dummy()
                            }
                            else -> break
                        }

                        if (currentResult is RegistrationResult.Success) {
                            session = (currentResult as RegistrationResult.Success).session
                        }
                    }

                    session ?: throw Exception("Registration incomplete")
                    currentSession = session
                    openSession(session)
                    session
                }
            }
        }.onFailure {
            _sessionState.value = SessionState.Error(it.message ?: "Registration failed")
            Timber.e(it, "Registration failed")
        }
    }

    suspend fun logout(): Result<Unit> {
        return runCatching {
            val session = currentSession ?: return@runCatching
            session.syncService().stopSync()
            session.signOutService().signOut(true)
            session.close()
            currentSession = null
            _sessionState.value = SessionState.NoSession
        }
    }

    fun getCurrentSession(): Session? = currentSession

    fun getCurrentUserId(): String? = currentSession?.myUserId

    // ── Room Operations ─────────────────────────────────

    fun getRoomsFlow(): Flow<List<RoomSummary>> {
        val session = currentSession ?: return emptyFlow()
        return session.roomService().getRoomSummariesLive(
            queryParams = org.matrix.android.sdk.api.session.room.RoomSortOrder.ACTIVITY
        ).asFlow()
    }

    fun getRoom(roomId: String): Room? {
        return currentSession?.roomService()?.getRoom(roomId)
    }

    suspend fun createDirectChat(userId: String): Result<String> {
        return runCatching {
            val session = requireSession()
            val params = CreateRoomParams().apply {
                isDirect = true
                invitedUserIds.add(userId)
                setDirectMessage()
                preset = RoomPreset.TRUSTED_PRIVATE_CHAT
            }
            session.roomService().createRoom(params)
        }
    }

    suspend fun createGroup(
        name: String,
        topic: String,
        userIds: List<String>
    ): Result<String> {
        return runCatching {
            val session = requireSession()
            val params = CreateRoomParams().apply {
                this.name = name
                this.topic = topic
                invitedUserIds.addAll(userIds)
                preset = RoomPreset.PRIVATE_CHAT
            }
            session.roomService().createRoom(params)
        }
    }

    suspend fun joinRoom(roomIdOrAlias: String): Result<String> {
        return runCatching {
            val session = requireSession()
            session.roomService().joinRoom(
                roomIdOrAlias = roomIdOrAlias,
                reason = null,
                viaServers = emptyList()
            )
        }.mapCatching { roomId ->
            roomId ?: throw Exception("Failed to join room")
        }
    }

    suspend fun leaveRoom(roomId: String, reason: String? = null): Result<Unit> {
        return runCatching {
            requireRoom(roomId).membershipService().leave(reason)
        }
    }

    // ── Messaging ───────────────────────────────────────

    suspend fun sendTextMessage(
        roomId: String,
        text: String,
        formattedText: String? = null
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).sendService()
                .sendTextMessage(text = text, autoMarkdown = true)
        }
    }

    suspend fun sendFormattedMessage(
        roomId: String,
        text: String,
        htmlText: String
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).sendService().sendFormattedTextMessage(
                text = text,
                formattedText = htmlText
            )
        }
    }

    suspend fun sendReply(
        roomId: String,
        replyText: String,
        originalEvent: TimelineEvent
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).sendService().replyToMessage(
                eventReplied = originalEvent.root,
                replyText = replyText,
                autoMarkdown = true
            )
        }
    }

    suspend fun sendImage(
        roomId: String,
        uri: android.net.Uri,
        caption: String? = null
    ): Result<Unit> {
        return runCatching {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val attachment = ContentAttachmentData(
                mimeType = mimeType,
                type = ContentAttachmentData.Type.IMAGE,
                uri = uri,
                name = getFileName(uri),
                size = getFileSize(uri)
            )
            requireRoom(roomId).sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = true,
                roomIds = emptySet()
            )
        }
    }

    suspend fun sendVideo(
        roomId: String,
        uri: android.net.Uri,
        caption: String? = null
    ): Result<Unit> {
        return runCatching {
            val attachment = ContentAttachmentData(
                mimeType = "video/mp4",
                type = ContentAttachmentData.Type.VIDEO,
                uri = uri,
                name = getFileName(uri),
                size = getFileSize(uri)
            )
            requireRoom(roomId).sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = false,
                roomIds = emptySet()
            )
        }
    }

    suspend fun sendVoiceMessage(
        roomId: String,
        uri: android.net.Uri,
        durationMs: Long,
        waveform: List<Int>
    ): Result<Unit> {
        return runCatching {
            val fileSize = getFileSize(uri)
            val attachment = ContentAttachmentData(
                mimeType = "audio/ogg",
                type = ContentAttachmentData.Type.AUDIO,
                uri = uri,
                name = "voice_message.ogg",
                size = fileSize,
                duration = durationMs,
                waveform = waveform
            )
            requireRoom(roomId).sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = false,
                roomIds = emptySet()
            )
        }
    }

    suspend fun sendFile(
        roomId: String,
        uri: android.net.Uri
    ): Result<Unit> {
        return runCatching {
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val attachment = ContentAttachmentData(
                mimeType = mimeType,
                type = ContentAttachmentData.Type.FILE,
                uri = uri,
                name = getFileName(uri),
                size = getFileSize(uri)
            )
            requireRoom(roomId).sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = false,
                roomIds = emptySet()
            )
        }
    }

    suspend fun sendSticker(
        roomId: String,
        url: String,
        body: String,
        width: Int,
        height: Int,
        mimeType: String
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).sendService().sendEvent(
                eventType = EventType.STICKER,
                content = mapOf(
                    "url" to url,
                    "body" to body,
                    "info" to mapOf(
                        "w" to width,
                        "h" to height,
                        "mimetype" to mimeType
                    )
                ).toContent()
            )
        }
    }

    suspend fun sendLocation(
        roomId: String,
        latitude: Double,
        longitude: Double,
        description: String
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).sendService().sendEvent(
                eventType = EventType.MESSAGE,
                content = mapOf(
                    "msgtype" to "m.location",
                    "body" to description,
                    "geo_uri" to "geo:$latitude,$longitude",
                    "info" to mapOf(
                        "thumbnail_info" to mapOf(
                            "mimetype" to "image/png"
                        )
                    ),
                    "org.matrix.msc3488.location" to mapOf(
                        "uri" to "geo:$latitude,$longitude",
                        "description" to description
                    )
                ).toContent()
            )
        }
    }

    suspend fun editMessage(
        roomId: String,
        targetEventId: String,
        newText: String
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).sendService().editTextMessage(
                targetEventId = targetEventId,
                msgType = org.matrix.android.sdk.api.session.events
                    .model.content.MessageType.MSGTYPE_TEXT,
                newBodyText = newText,
                newBodyAutoMarkdown = true,
                compatibilityBodyText = "* $newText"
            )
        }
    }

    suspend fun deleteMessage(
        roomId: String,
        eventId: String,
        reason: String? = null
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).sendService().redactEvent(
                eventId = eventId,
                reason = reason
            )
        }
    }

    suspend fun sendReaction(
        roomId: String,
        targetEventId: String,
        emoji: String
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).relationService().sendReaction(
                targetEventId = targetEventId,
                reaction = emoji
            )
        }
    }

    suspend fun removeReaction(
        roomId: String,
        targetEventId: String,
        emoji: String
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).relationService().undoReaction(
                targetEventId = targetEventId,
                reaction = emoji
            )
        }
    }

    // ── Typing ──────────────────────────────────────────

    suspend fun sendTyping(roomId: String, isTyping: Boolean): Result<Unit> {
        return runCatching {
            if (isTyping) {
                requireRoom(roomId).typingService().userIsTyping()
            } else {
                requireRoom(roomId).typingService().userStopsTyping()
            }
        }
    }

    // ── Read Receipts ───────────────────────────────────

    suspend fun markAsRead(roomId: String, event: TimelineEvent): Result<Unit> {
        return runCatching {
            requireRoom(roomId).readService().markAsRead(
                params = org.matrix.android.sdk.api.session.room.read
                    .ReadService.MarkAsReadParams.READ_RECEIPT
            )
        }
    }

    // ── Timeline ────────────────────────────────────────

    fun createTimeline(roomId: String, eventId: String? = null): Timeline? {
        val room = getRoom(roomId) ?: return null
        val settings = TimelineSettings(initialSize = 50)
        return if (eventId != null) {
            room.timelineService().createTimeline(eventId, settings)
        } else {
            room.timelineService().createTimeline(null, settings)
        }
    }

    fun getTimelineEventFlow(roomId: String): Flow<List<TimelineEvent>> {
        val room = getRoom(roomId) ?: return emptyFlow()
        return room.flow().liveTimeline()
    }

    // ── Pinned Messages ─────────────────────────────────

    suspend fun pinMessage(roomId: String, eventId: String): Result<Unit> {
        return runCatching {
            val room = requireRoom(roomId)
            val currentPinned = room.stateService()
                .getStateEvent(EventType.STATE_ROOM_PINNED_EVENT, "")
                ?.content?.get("pinned") as? List<String> ?: emptyList()

            room.stateService().sendStateEvent(
                eventType = EventType.STATE_ROOM_PINNED_EVENT,
                stateKey = "",
                body = mapOf("pinned" to (currentPinned + eventId)).toContent()
            )
        }
    }

    suspend fun unpinMessage(roomId: String, eventId: String): Result<Unit> {
        return runCatching {
            val room = requireRoom(roomId)
            val currentPinned = room.stateService()
                .getStateEvent(EventType.STATE_ROOM_PINNED_EVENT, "")
                ?.content?.get("pinned") as? List<String> ?: emptyList()

            room.stateService().sendStateEvent(
                eventType = EventType.STATE_ROOM_PINNED_EVENT,
                stateKey = "",
                body = mapOf(
                    "pinned" to currentPinned.filter { it != eventId }
                ).toContent()
            )
        }
    }

    // ── Room Settings ───────────────────────────────────

    suspend fun updateRoomName(roomId: String, name: String): Result<Unit> {
        return runCatching {
            requireRoom(roomId).stateService().updateName(name)
        }
    }

    suspend fun updateRoomTopic(roomId: String, topic: String): Result<Unit> {
        return runCatching {
            requireRoom(roomId).stateService().updateTopic(topic)
        }
    }

    suspend fun updateRoomAvatar(roomId: String, uri: android.net.Uri): Result<Unit> {
        return runCatching {
            val session = requireSession()
            val response = session.fileService().uploadFromUri(
                uri = uri,
                fileName = "room_avatar.jpg",
                mimeType = "image/jpeg",
                progressCallback = null
            )
            requireRoom(roomId).stateService().updateAvatar(
                avatarUri = android.net.Uri.parse(response)
            )
        }
    }

    // ── Member Management ───────────────────────────────

    suspend fun inviteUser(roomId: String, userId: String): Result<Unit> {
        return runCatching {
            requireRoom(roomId).membershipService().invite(userId)
        }
    }

    suspend fun kickUser(
        roomId: String,
        userId: String,
        reason: String? = null
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).membershipService().remove(userId, reason)
        }
    }

    suspend fun banUser(
        roomId: String,
        userId: String,
        reason: String? = null
    ): Result<Unit> {
        return runCatching {
            requireRoom(roomId).membershipService().ban(userId, reason)
        }
    }

    // ── User Profile ────────────────────────────────────

    suspend fun updateDisplayName(displayName: String): Result<Unit> {
        return runCatching {
            val session = requireSession()
            session.profileService().setDisplayName(
                userId = session.myUserId,
                newDisplayName = displayName
            )
        }
    }

    suspend fun updateAvatar(uri: android.net.Uri): Result<Unit> {
        return runCatching {
            val session = requireSession()
            val response = session.fileService().uploadFromUri(
                uri = uri,
                fileName = "avatar.jpg",
                mimeType = "image/jpeg",
                progressCallback = null
            )
            session.profileService().setAvatar(
                userId = session.myUserId,
                newAvatarUri = android.net.Uri.parse(response)
            )
        }
    }

    suspend fun getUserProfile(userId: String): Result<MatrixUser> {
        return runCatching {
            val session = requireSession()
            val profile = session.profileService().getProfileAsUser(userId)
            MatrixUser(
                userId = userId,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
                isOnline = false,
                lastSeen = null,
                presenceStatus = null
            )
        }
    }

    // ── Search ──────────────────────────────────────────

    suspend fun searchUsers(query: String): Result<List<MatrixUser>> {
        return runCatching {
            val session = requireSession()
            val result = session.userService().searchUsersDirectory(
                search = query,
                limit = 50,
                excludedUserIds = setOf(session.myUserId)
            )
            result.map { user ->
                MatrixUser(
                    userId = user.userId,
                    displayName = user.displayName,
                    avatarUrl = user.avatarUrl,
                    isOnline = false,
                    lastSeen = null,
                    presenceStatus = null
                )
            }
        }
    }

    suspend fun searchMessages(
        query: String,
        roomId: String? = null
    ): Result<List<TimelineEvent>> {
        return runCatching {
            val session = requireSession()
            // Use Matrix search API
            val result = session.searchService().search(
                searchTerm = query,
                roomId = roomId,
                nextBatch = null,
                orderByRecent = true,
                limit = 50,
                beforeLimit = 0,
                afterLimit = 0,
                includeProfile = true
            )
            result.results.map { it.event }
        }
    }

    // ── Presence ────────────────────────────────────────

    suspend fun setPresence(
        presence: org.matrix.android.sdk.api.session.presence.model.PresenceEnum
    ): Result<Unit> {
        return runCatching {
            requireSession().presenceService().setMyPresence(
                presence = presence,
                statusMsg = null
            )
        }
    }

    // ── Draft ───────────────────────────────────────────

    suspend fun saveDraft(roomId: String, text: String): Result<Unit> {
        return runCatching {
            if (text.isBlank()) {
                requireRoom(roomId).draftService().deleteDraft()
            } else {
                requireRoom(roomId).draftService().saveDraft(
                    UserDraft.Regular(text)
                )
            }
        }
    }

    suspend fun getDraft(roomId: String): String? {
        return runCatching {
            val draft = requireRoom(roomId).draftService().getDraft()
            (draft as? UserDraft.Regular)?.content
        }.getOrNull()
    }

    // ── Encryption ──────────────────────────────────────

    suspend fun enableEncryption(roomId: String): Result<Unit> {
        return runCatching {
            requireRoom(roomId).stateService().enableEncryption()
        }
    }

    fun isRoomEncrypted(roomId: String): Boolean {
        val session = currentSession ?: return false
        return session.cryptoService().isRoomEncrypted(roomId)
    }

    // ── Flow Helpers ────────────────────────────────────

    fun getRoomSummaryFlow(roomId: String): Flow<Optional<RoomSummary>> {
        val room = getRoom(roomId) ?: return emptyFlow()
        return room.flow().liveRoomSummary()
    }

    fun getTypingUsersFlow(roomId: String): Flow<List<String>> {
        val room = getRoom(roomId) ?: return emptyFlow()
        return room.flow().liveRoomSummary().map { optional ->
            optional.getOrNull()?.typingUsers ?: emptyList()
        }
    }

    fun getUnreadCountFlow(roomId: String): Flow<Int> {
        val room = getRoom(roomId) ?: return emptyFlow()
        return room.flow().liveRoomSummary().map { optional ->
            optional.getOrNull()?.notificationCount ?: 0
        }
    }

    fun getSyncStateFlow(): Flow<SyncState> = _syncState.asStateFlow()

    // ── Helper Functions ────────────────────────────────

    private fun requireSession(): Session {
        return currentSession
            ?: throw IllegalStateException("No active Matrix session. Please login first.")
    }

    private fun requireRoom(roomId: String): Room {
        return requireSession().roomService().getRoom(roomId)
            ?: throw IllegalStateException("Room $roomId not found")
    }

    private fun getFileName(uri: android.net.Uri): String {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(
                android.provider.OpenableColumns.DISPLAY_NAME
            )
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        } ?: "file_${System.currentTimeMillis()}"
    }

    private fun getFileSize(uri: android.net.Uri): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            cursor.moveToFirst()
            cursor.getLong(sizeIndex)
        } ?: 0L
    }
}
