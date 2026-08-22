package com.matrixmessenger.data.matrix

import android.content.Context
import com.matrixmessenger.core.coroutine.DispatcherProvider
import com.matrixmessenger.data.local.AppPreferences
import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.core.network.ConnectivityObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.auth.data.LoginFlowResult
import org.matrix.android.sdk.api.auth.registration.RegistrationResult
import org.matrix.android.sdk.api.auth.registration.Stage
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.create.CreateRoomParams
import org.matrix.android.sdk.api.session.room.model.create.CreateRoomPreset
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageType
import org.matrix.android.sdk.api.session.room.send.UserDraft
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import org.matrix.android.sdk.api.session.room.timeline.hasBeenEdited
import org.matrix.android.sdk.api.session.events.model.toContent
import org.matrix.android.sdk.api.session.events.model.getRelationContent
import org.matrix.android.sdk.api.session.presence.model.PresenceEnum
import org.matrix.android.sdk.api.session.room.model.relation.RelationService
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.search.SearchResult
import org.matrix.android.sdk.api.session.search.EventAndSender
import org.matrix.android.sdk.api.query.QueryStringValue
import org.matrix.android.sdk.api.query.QueryStateEventValue
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import java.util.Date
import java.io.File
import org.matrix.android.sdk.api.session.sync.SyncState
import org.matrix.android.sdk.api.util.Optional
import androidx.lifecycle.asFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixClientManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val matrix: Matrix,
    private val appPreferences: AppPreferences,
    private val dispatcherProvider: DispatcherProvider,
    private val connectivityObserver: ConnectivityObserver
) {
    private var managerScope = CoroutineScope(SupervisorJob() + dispatcherProvider.io)

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
        if (_sessionState.value !is SessionState.NoSession) return
        tryRestoreSession()
    }

    private fun tryRestoreSession() {
        managerScope.launch {
            _sessionState.value = SessionState.Loading
            val lastSession = matrix.authenticationService().getLastAuthenticatedSession()
            if (lastSession != null) {
                currentSession = lastSession
                openSession(lastSession)
            } else {
                _sessionState.value = SessionState.NoSession
            }
        }
    }

    private fun openSession(session: Session) {
        session.open()

        session.addListener(object : Session.Listener {
            override fun onSessionStarted(session: Session) {
                _sessionState.value = SessionState.Active(session.myUserId)
            }

            override fun onSessionStopped(session: Session) {
                _sessionState.value = SessionState.NoSession
            }
        })

        session.syncService().startSync(fromForeground = true)

        managerScope.launch {
            session.syncService().getSyncStateLive().asFlow()
                .collect { state ->
                    _syncState.value = state
                    if (state is SyncState.NoNetwork) {
                        Timber.e("Sync paused: No network")
                    }
                }
        }

        managerScope.launch {
            connectivityObserver.observe().collect { status ->
                val activeSession = currentSession ?: return@collect
                if (status == ConnectivityObserver.Status.Available) {
                    val currentState = activeSession.syncService().getSyncState()
                    if (currentState !is SyncState.Running) {
                        Timber.d("Network available, restarting sync")
                        activeSession.syncService().startSync(fromForeground = true)
                    }
                }
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
            
            val session = matrix.authenticationService().directAuthentication(
                homeServerConnectionConfig = config,
                matrixId = login,
                password = password,
                initialDeviceName = "Matrix Messenger Android"
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
                initialDeviceDisplayName = "Matrix Messenger"
            )

            when (result) {
                is RegistrationResult.Success -> {
                    val session = result.session
                    currentSession = session
                    openSession(session)
                    session
                }
                is RegistrationResult.FlowResponse -> {
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
            clearAllTimelines()
            dispose()
        }
    }

    fun dispose() {
        managerScope.coroutineContext[Job]?.cancelChildren()
    }

    private fun clearAllTimelines() {
        activeTimelines.values.forEach { it.dispose() }
        activeTimelines.clear()
        timelineObservers.clear()
    }

    fun getCurrentSession(): Session? = currentSession

    fun getCurrentUserId(): String? = currentSession?.myUserId

    // ── Room Operations ─────────────────────────────────

    fun getRoomsFlow(): Flow<List<RoomSummary>> {
        val session = currentSession ?: return emptyFlow()
        return session.roomService().getRoomSummariesLive(
            queryParams = org.matrix.android.sdk.api.session.room.roomSummaryQueryParams {  },
            sortOrder = org.matrix.android.sdk.api.session.room.RoomSortOrder.NONE
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
                preset = CreateRoomPreset.PRESET_TRUSTED_PRIVATE_CHAT
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
                preset = CreateRoomPreset.PRESET_PRIVATE_CHAT
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
            roomIdOrAlias // Return the ID/Alias as a placeholder if SDK returns Unit
        }
    }

    suspend fun leaveRoom(roomId: String, reason: String? = null): Result<Unit> {
        return runCatching {
            requireSession().roomService().leaveRoom(roomId, reason)
        }
    }

    // ── Messaging ───────────────────────────────────────

    suspend fun sendTextMessage(
        roomId: String,
        text: String,
        formattedText: String? = null
    ): Result<Unit> {
        return runCatching {
            val room = requireRoom(roomId)
            if (formattedText != null) {
                room.sendService().sendFormattedTextMessage(text, formattedText)
            } else {
                room.sendService().sendTextMessage(text)
            }
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
            requireRoom(roomId).relationService().replyToMessage(
                eventReplied = originalEvent,
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
                queryUri = uri,
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
                queryUri = uri,
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

    suspend fun sendVideoNote(
        roomId: String,
        uri: android.net.Uri,
        durationMs: Long,
        width: Int,
        height: Int
    ): Result<Unit> {
        return runCatching {
            val contentUri = if (uri.scheme == "file") {
                val file = java.io.File(uri.path!!)
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                uri
            }

            val fileSize = getFileSize(contentUri)
            val attachment = ContentAttachmentData(
                mimeType = "video/mp4",
                type = ContentAttachmentData.Type.VIDEO,
                queryUri = contentUri,
                name = "video_note.mp4",
                size = fileSize,
                duration = durationMs,
                width = width.toLong(),
                height = height.toLong()
            )
            
            requireRoom(roomId).sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = false,
                roomIds = emptySet(),
                additionalContent = mapOf("org.matrix.msc2457.video_note" to emptyMap<String, Any>())
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
                mimeType = "audio/mp4",
                type = ContentAttachmentData.Type.AUDIO,
                queryUri = uri,
                name = "voice_message.m4a",
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

    suspend fun sendImageMessage(
        roomId: String,
        imageFile: java.io.File,
        caption: String? = null
    ): Result<Unit> {
        return runCatching {
            val attachment = ContentAttachmentData(
                mimeType = "image/jpeg",
                type = ContentAttachmentData.Type.IMAGE,
                queryUri = android.net.Uri.fromFile(imageFile),
                name = imageFile.name,
                size = imageFile.length()
            )
            requireRoom(roomId).sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = true,
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
                queryUri = uri,
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
            val room = requireRoom(roomId)
            val event = room.timelineService().getTimelineEvent(targetEventId)
                ?: throw Exception("Event not found")
            room.relationService().editTextMessage(
                targetEvent = event,
                msgType = MessageType.MSGTYPE_TEXT,
                newBodyText = newText,
                newFormattedBodyText = null,
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
            val room = requireRoom(roomId)
            val event = room.timelineService().getTimelineEvent(eventId)
                ?: throw Exception("Event not found")
            room.sendService().redactEvent(
                event = event.root,
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

    suspend fun markAsRead(roomId: String): Result<Unit> {
        return runCatching {
            requireRoom(roomId).readService().markAsRead(
                params = org.matrix.android.sdk.api.session.room.read
                    .ReadService.MarkAsReadParams.READ_RECEIPT
            )
        }
    }

    suspend fun resendMessage(roomId: String, localId: String): Result<Unit> {
        return runCatching {
            val room = requireRoom(roomId)
            val event = room.timelineService().getTimelineEvent(localId) 
                ?: throw Exception("Local echo not found")
            
            if (event.root.getClearType() == EventType.MESSAGE) {
                val content = event.root.getClearContent()
                val msgType = (content as? MessageContent)?.msgType
                if (msgType == MessageType.MSGTYPE_TEXT || msgType == MessageType.MSGTYPE_EMOTE) {
                    room.sendService().resendTextMessage(event)
                } else {
                    room.sendService().resendMediaMessage(event)
                }
            } else {
                room.sendService().resendTextMessage(event)
            }
        }
    }

    suspend fun cancelSend(roomId: String, localId: String): Result<Unit> {
        return runCatching {
            val room = requireRoom(roomId)
            val event = room.timelineService().getTimelineEvent(localId)
            if (event != null) {
                room.sendService().deleteFailedEcho(event)
                room.sendService().cancelSend(localId)
            }
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

    private val activeTimelines = mutableMapOf<String, Timeline>()
    private val timelineObservers = mutableMapOf<String, Int>()

    fun getTimelineEventFlow(roomId: String, limit: Int = 50): Flow<List<TimelineEvent>> = callbackFlow {
        val room = getRoom(roomId)
        if (room == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        
        val timeline = activeTimelines.getOrPut(roomId) {
            room.timelineService().createTimeline(null, TimelineSettings(limit)).apply { start() }
        }
        timelineObservers[roomId] = (timelineObservers[roomId] ?: 0) + 1
        
        val listener = object : Timeline.Listener {
            override fun onTimelineUpdated(snapshot: List<TimelineEvent>) {
                trySend(snapshot)
            }
        }
        
        timeline.addListener(listener)
        trySend(timeline.getSnapshot())
        
        awaitClose {
            timeline.removeListener(listener)
            val observers = (timelineObservers[roomId] ?: 0) - 1
            if (observers <= 0) {
                timeline.dispose()
                activeTimelines.remove(roomId)
                timelineObservers.remove(roomId)
            } else {
                timelineObservers[roomId] = observers
            }
        }
    }

    suspend fun paginateTimeline(roomId: String, backward: Boolean = true, limit: Int = 20): Result<Unit> {
        return runCatching {
            val timeline = activeTimelines[roomId] ?: throw Exception("No active timeline for room $roomId")
            val direction = if (backward) Timeline.Direction.BACKWARDS else Timeline.Direction.FORWARDS
            timeline.paginate(direction, limit)
        }
    }

    // ── Pinned Messages ─────────────────────────────────

    suspend fun pinMessage(roomId: String, eventId: String): Result<Unit> {
        return runCatching {
            val room = requireRoom(roomId)
            val currentPinned = room.stateService()
                .getStateEvent(EventType.STATE_ROOM_PINNED_EVENT, QueryStringValue.Equals(""))
                ?.content?.get("pinned") as? List<*> ?: emptyList<String>()
            
            val newPinned = (currentPinned.filterIsInstance<String>() + eventId).distinct()

            room.stateService().sendStateEvent(
                eventType = EventType.STATE_ROOM_PINNED_EVENT,
                stateKey = "",
                body = mapOf("pinned" to newPinned).toContent()
            )
        }
    }

    suspend fun unpinMessage(roomId: String, eventId: String): Result<Unit> {
        return runCatching {
            val room = requireRoom(roomId)
            val currentPinned = room.stateService()
                .getStateEvent(EventType.STATE_ROOM_PINNED_EVENT, QueryStringValue.Equals(""))
                ?.content?.get("pinned") as? List<*> ?: emptyList<String>()

            val newPinned = currentPinned.filterIsInstance<String>().filter { it != eventId }

            room.stateService().sendStateEvent(
                eventType = EventType.STATE_ROOM_PINNED_EVENT,
                stateKey = "",
                body = mapOf(
                    "pinned" to newPinned
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
            val room = requireRoom(roomId)
            room.stateService().updateAvatar(
                avatarUri = uri,
                fileName = "room_avatar.jpg"
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

    suspend fun unbanUser(roomId: String, userId: String): Result<Unit> {
        return runCatching {
            requireRoom(roomId).membershipService().unban(userId, null)
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
            session.profileService().updateAvatar(
                userId = session.myUserId,
                newAvatarUri = uri,
                fileName = "avatar.jpg"
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
                presence = com.matrixmessenger.domain.model.PresenceState.OFFLINE
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
                    presence = com.matrixmessenger.domain.model.PresenceState.OFFLINE
                )
            }
        }
    }

    suspend fun searchMessages(
        query: String,
        roomId: String
    ): Result<List<Event>> {
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
            result.results?.map { it.event } ?: emptyList()
        }
    }

    // ── Presence ────────────────────────────────────────

    fun observePresence(userId: String): Flow<com.matrixmessenger.domain.model.PresenceState> {
        return flow {
            // Matrix SDK doesn't support live presence flow yet.
            // Returning a placeholder flow for now.
            emit(com.matrixmessenger.domain.model.PresenceState.UNKNOWN)
        }
    }

    suspend fun setPresence(
        presence: PresenceEnum
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
            requireRoom(roomId).roomCryptoService().enableEncryption()
        }
    }

    fun isRoomEncrypted(roomId: String): Boolean {
        val session = currentSession ?: return false
        return session.cryptoService().isRoomEncrypted(roomId)
    }

    // ── Flow Helpers ────────────────────────────────────

    fun getRoomSummaryFlow(roomId: String): Flow<Optional<RoomSummary>> {
        val room = getRoom(roomId) ?: return emptyFlow()
        return room.getRoomSummaryLive().asFlow()
    }

    fun getTypingUsersFlow(roomId: String): Flow<List<String>> {
        val room = getRoom(roomId) ?: return emptyFlow()
        return room.getRoomSummaryLive().asFlow().map { optional ->
            optional.getOrNull()?.typingUsers?.map { it.userId } ?: emptyList()
        }
    }

    fun getUnreadCountFlow(roomId: String): Flow<Int> {
        val room = getRoom(roomId) ?: return emptyFlow()
        return room.getRoomSummaryLive().asFlow().map { optional ->
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
        if (uri.scheme == "file") {
            return uri.path?.let { java.io.File(it).length() } ?: 0L
        }
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            try {
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else {
                    0L
                }
            } catch (e: Exception) {
                0L
            }
        } ?: 0L
    }
}
