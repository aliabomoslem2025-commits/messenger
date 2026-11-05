/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.features.media.ImageContentRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.matrix.android.sdk.api.query.RoomCategoryFilter
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import org.matrix.android.sdk.api.session.crypto.attachments.toElementToDecrypt
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.room.Room
import org.matrix.android.sdk.api.session.room.RoomSummaryQueryParams
import org.matrix.android.sdk.api.session.room.members.RoomMemberQueryParams
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.PowerLevelsContent
import org.matrix.android.sdk.api.session.room.model.RoomDirectoryVisibility
import org.matrix.android.sdk.api.session.room.model.RoomHistoryVisibility
import org.matrix.android.sdk.api.session.room.model.create.CreateRoomParams
import org.matrix.android.sdk.api.session.room.model.create.CreateRoomPreset
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageImageInfoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageVideoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageWithAttachmentContent
import org.matrix.android.sdk.api.session.room.model.message.VideoInfo
import org.matrix.android.sdk.api.session.room.model.message.getFileUrl
import org.matrix.android.sdk.api.session.room.model.message.getThumbnailUrl
import org.matrix.android.sdk.api.session.room.model.roomdirectory.PublicRoomsFilter
import org.matrix.android.sdk.api.session.room.model.roomdirectory.PublicRoomsParams
import org.matrix.android.sdk.api.session.room.send.SendState
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stories use standard Matrix media events with app-specific expiry metadata.
 *
 * "Everyone" stories are posted to a public, world-readable room owned by the author.
 * The author's public room is listed in the homeserver room directory and federates
 * normally; private audiences continue to use one encrypted room per story.
 * Homeservers may retain historical events according to their retention policy, so
 * expiry is enforced by this client rather than being a network-wide hard-delete.
 */
data class StoryContact(
        val userId: String,
        val displayName: String,
)

data class StoryRoom(
        val roomId: String,
        val displayName: String,
        val joinedMembersCount: Int,
)

data class StoryDraft(
        val caption: String = "",
        val overlayTexts: List<String> = emptyList(),
        val stickers: List<String> = emptyList(),
        val overlayTextPositions: List<String> = emptyList(),
        val stickerPositions: List<String> = emptyList(),
        val drawingPaths: List<String> = emptyList(),
        val brightnessPercent: Int = 100,
        val cropToFill: Boolean = true,
        val allowScreenshots: Boolean = true,
        val keepOnProfile: Boolean = false,
        val durationHours: Int = 24,
        val audience: String = "everyone",
        val selectedUserIds: Set<String> = emptySet(),
        val selectedRoomIds: Set<String> = emptySet(),
        val excludedUserIds: Set<String> = emptySet(),
        val albumName: String = "All Stories",
)

@Singleton
class TelegramStoriesRepository @Inject constructor(
        @ApplicationContext private val context: Context,
        private val activeSessionHolder: ActiveSessionHolder,
) {

    data class Story(
            val roomId: String,
            val event: TimelineEvent,
            val ownerUserId: String,
            val createdAt: Long,
            val expiresAt: Long,
            val content: MessageContent,
            val isSeen: Boolean,
            val caption: String = "",
            val keepOnProfile: Boolean = false,
            val allowScreenshots: Boolean = true,
    ) {
        val isVideo: Boolean
            get() = content.msgType == MSGTYPE_VIDEO
    }

    suspend fun publishStory(uri: Uri, draft: StoryDraft = StoryDraft()): String = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession()
                ?: error("No active Matrix session")
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri)?.substringBefore(';')?.trim()
                ?: error("The selected media type could not be determined")
        val mediaType = when {
            mimeType.startsWith("image/") -> ContentAttachmentData.Type.IMAGE
            mimeType.startsWith("video/") -> ContentAttachmentData.Type.VIDEO
            else -> error("Stories support images and videos only")
        }
        val (name, size) = queryMetadata(resolver, uri)
        val now = System.currentTimeMillis()
        val durationHours = draft.durationHours.coerceIn(6, 48)
        val expiresAt = if (draft.keepOnProfile) {
            Long.MAX_VALUE
        } else {
            now + durationHours * 60L * 60L * 1000L
        }

        val room = if (draft.audience == AUDIENCE_EVERYONE) {
            getOrCreatePublicStoriesRoom(session)
        } else {
            val recipients = when (draft.audience) {
                AUDIENCE_SELECTED -> {
                    val knownContacts = getEligibleContacts().map { it.userId }.toSet()
                    draft.selectedUserIds
                            .intersect(knownContacts)
                            .minus(draft.excludedUserIds)
                            .minus(session.myUserId)
                            .also {
                                if (it.isEmpty()) error("Select at least one person who can view this story")
                            }
                }
                AUDIENCE_SELECTED_ROOMS -> {
                    getSelectedRoomMemberIds(session, draft.selectedRoomIds)
                            .minus(draft.excludedUserIds)
                            .minus(session.myUserId)
                            .also {
                                if (it.isEmpty()) error("The selected rooms have no other joined members")
                            }
                }
                else -> error("Unsupported story audience")
            }

            val roomName = "${STORY_ROOM_NAME_PREFIX}|${session.myUserId}|$now"
            val params = CreateRoomParams().apply {
                name = roomName
                roomType = STORY_ROOM_TYPE
                visibility = RoomDirectoryVisibility.PRIVATE
                preset = CreateRoomPreset.PRESET_PRIVATE_CHAT
                historyVisibility = RoomHistoryVisibility.SHARED
                invitedUserIds.addAll(recipients)
                enableEncryption()
                // Invited viewers may read the story, but only its author can post.
                powerLevelContentOverride = PowerLevelsContent(eventsDefault = 100)
            }
            val privateRoomId = session.roomService().createRoom(params)
            session.roomService().getRoom(privateRoomId)
                    ?: error("Matrix created the private story room but it is not available locally")
        }

        val attachment = ContentAttachmentData(
                size = size,
                name = name,
                queryUri = uri,
                mimeType = mimeType,
                type = mediaType,
                date = now,
        )
        val storyId = UUID.randomUUID().toString()
        val sendOperation = room.sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = mediaType == ContentAttachmentData.Type.IMAGE,
                roomIds = setOf(room.roomId),
                additionalContent = mapOf(
                        STORY_METADATA_KEY to mapOf(
                                "version" to STORY_FORMAT_VERSION,
                                "story_id" to storyId,
                                "created_at" to now,
                                "expires_at" to expiresAt,
                                "caption" to draft.caption.take(2048),
                                "overlay_texts" to draft.overlayTexts.take(20),
                                "stickers" to draft.stickers.take(50),
                                "overlay_text_positions" to draft.overlayTextPositions.take(20),
                                "sticker_positions" to draft.stickerPositions.take(50),
                                "drawing_paths" to draft.drawingPaths.take(100),
                                "brightness_percent" to draft.brightnessPercent.coerceIn(0, 200),
                                "crop_to_fill" to draft.cropToFill,
                                "allow_screenshots" to draft.allowScreenshots,
                                "keep_on_profile" to draft.keepOnProfile,
                                "duration_hours" to durationHours,
                                "album" to draft.albumName.take(64),
                                "audience" to draft.audience,
                        )
                ),
        )
        try {
            awaitStoryUpload(room, storyId, session.myUserId)
        } catch (failure: Throwable) {
            runCatching { sendOperation.cancel() }
            throw failure
        }
        room.roomId
    }

    private suspend fun getOrCreatePublicStoriesRoom(session: Session): Room {
        val roomService = session.roomService()
        val roomName = publicStoryRoomName(session.myUserId)
        val query = RoomSummaryQueryParams.Builder().also {
            it.memberships = listOf(Membership.JOIN)
            it.includeType = listOf(STORY_ROOM_TYPE)
            it.excludeType = null
        }.build()
        val existing = roomService.getRoomSummaries(query).firstOrNull { summary ->
            summary.name == roomName || summary.displayName == roomName
        }
        val roomId = existing?.roomId ?: roomService.createRoom(
                CreateRoomParams().apply {
                    name = roomName
                    topic = "Public stories from ${session.myUserId}"
                    roomType = STORY_ROOM_TYPE
                    visibility = RoomDirectoryVisibility.PUBLIC
                    preset = CreateRoomPreset.PRESET_PUBLIC_CHAT
                    historyVisibility = RoomHistoryVisibility.WORLD_READABLE
                    // The room owner is the only user allowed to publish events.
                    powerLevelContentOverride = PowerLevelsContent(eventsDefault = 100)
                }
        )

        val directory = session.roomDirectoryService()
        if (directory.getRoomDirectoryVisibility(roomId) != RoomDirectoryVisibility.PUBLIC) {
            directory.setRoomDirectoryVisibility(roomId, RoomDirectoryVisibility.PUBLIC)
        }
        check(directory.getRoomDirectoryVisibility(roomId) == RoomDirectoryVisibility.PUBLIC) {
            "The homeserver did not make the story room public"
        }
        return roomService.getRoom(roomId)
                ?: error("The public story room is not available locally")
    }

    private suspend fun awaitStoryUpload(room: Room, storyId: String, senderId: String) {
        try {
            withTimeout(STORY_UPLOAD_TIMEOUT_MS) {
                while (true) {
                    val matchingEvent = room.timelineService().getAttachmentMessages().firstOrNull { event ->
                        if (event.root.senderId != senderId) {
                            false
                        } else {
                            val metadata = event.root.getClearContent()
                                    ?.get(STORY_METADATA_KEY) as? Map<*, *>
                            metadata?.get("story_id") == storyId
                        }
                    }
                    if (matchingEvent != null) {
                        when (matchingEvent.root.sendState) {
                            SendState.SENT,
                            SendState.SYNCED -> return@withTimeout
                            SendState.UNDELIVERED,
                            SendState.FAILED_UNKNOWN_DEVICES -> error(
                                    matchingEvent.root.sendStateDetails
                                            ?: "Matrix could not send the story media"
                            )
                            else -> Unit
                        }
                    }
                    delay(STORY_UPLOAD_POLL_INTERVAL_MS)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw IllegalStateException(
                    "Matrix did not confirm the story upload. Check your connection and try again.",
                    timeout
            )
        }
    }

    private suspend fun discoverPublicStoryRooms(session: org.matrix.android.sdk.api.session.Session) {
        val remoteServers = try {
            getEligibleContacts()
                    .map { it.userId.substringAfter(':', "") }
                    .filter { it.isNotBlank() && it != session.myUserId.substringAfter(':', "") }
                    .distinct()
                    .take(MAX_PUBLIC_DIRECTORY_SERVERS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        val servers: List<String?> = listOf(null) + remoteServers
        val discoveredRoomIds = linkedSetOf<String>()

        for (server in servers) {
            var nextBatch: String? = null
            for (page in 0 until MAX_PUBLIC_DIRECTORY_PAGES) {
                val response = try {
                    session.roomDirectoryService().getPublicRooms(
                            server = server,
                            publicRoomsParams = PublicRoomsParams(
                                    limit = PUBLIC_DIRECTORY_PAGE_SIZE,
                                    since = nextBatch,
                                    filter = PublicRoomsFilter(PUBLIC_STORY_ROOM_SEARCH_TERM),
                            ),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    break
                }
                response.chunk.orEmpty()
                        .asSequence()
                        .filter { publicRoom ->
                            publicRoom.worldReadable &&
                                    publicRoom.name.orEmpty().startsWith(PUBLIC_STORY_ROOM_NAME_PREFIX)
                        }
                        .forEach { publicRoom ->
                            if (discoveredRoomIds.size < MAX_PUBLIC_STORY_ROOMS_TO_JOIN) {
                                discoveredRoomIds.add(publicRoom.roomId)
                            }
                        }
                val next = response.nextBatch
                if (next.isNullOrBlank() || next == nextBatch ||
                        discoveredRoomIds.size >= MAX_PUBLIC_STORY_ROOMS_TO_JOIN ||
                        page == MAX_PUBLIC_DIRECTORY_PAGES - 1) {
                    break
                }
                nextBatch = next
            }
            if (discoveredRoomIds.size >= MAX_PUBLIC_STORY_ROOMS_TO_JOIN) break
        }

        discoveredRoomIds.forEach { roomId ->
            if (session.roomService().getRoomSummary(roomId)?.membership != Membership.JOIN) {
                try {
                    session.roomService().joinRoom(roomId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A room can disappear or reject federation between directory lookup and join.
                }
            }
        }
    }

    suspend fun loadStories(joinInvitations: Boolean = true): List<Story> = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession() ?: return@withContext emptyList()
        val query = RoomSummaryQueryParams.Builder().also {
            it.memberships = listOf(Membership.JOIN, Membership.INVITE)
            it.includeType = listOf(STORY_ROOM_TYPE)
            it.excludeType = null
        }.build()

        // Public stories are discovered through Matrix room directories. We only join
        // rooms using this app's public-story name prefix and world-readable history.
        if (joinInvitations) {
            discoverPublicStoryRooms(session)
        }

        // Private story invitations are joined only after the user opens Stories.
        val initialSummaries = session.roomService().getRoomSummaries(query)
        if (joinInvitations) {
            initialSummaries.filter { it.membership == Membership.INVITE }.forEach { summary ->
                runCatching { session.roomService().joinRoom(summary.roomId) }
            }
        }

        val summaries = session.roomService().getRoomSummaries(query).filter { summary ->
            joinInvitations || summary.membership == Membership.JOIN
        }
        val seenIds = session.accountDataService()
                .getUserAccountDataEvent(STORY_SEEN_ACCOUNT_DATA)
                ?.content
                ?.get("event_ids")
                .let { value -> (value as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty() }
        val now = System.currentTimeMillis()
        val result = mutableListOf<Story>()

        summaries.forEach { summary ->
            val roomName = summary.name.ifBlank { summary.displayName }
            val owner = parseOwner(roomName) ?: return@forEach
            val room = session.roomService().getRoom(summary.roomId) ?: return@forEach
            val events = runCatching { room.timelineService().getAttachmentMessages() }.getOrDefault(emptyList())
            events.forEach eventLoop@{ event ->
                val clearContent = event.root.getClearContent() ?: return@eventLoop
                val metadata = clearContent[STORY_METADATA_KEY] as? Map<*, *> ?: return@eventLoop
                val expiresAt = (metadata["expires_at"] as? Number)?.toLong() ?: return@eventLoop
                val keepOnProfile = metadata["keep_on_profile"] as? Boolean ?: false
                if (expiresAt <= now && !keepOnProfile) return@eventLoop
                val message = clearContent.toModel<MessageContent>() ?: return@eventLoop
                if (message.msgType != MSGTYPE_IMAGE && message.msgType != MSGTYPE_VIDEO) return@eventLoop
                val createdAt = (metadata["created_at"] as? Number)?.toLong()
                        ?: event.root.originServerTs
                        ?: event.root.ageLocalTs
                        ?: now
                result += Story(
                        roomId = summary.roomId,
                        event = event,
                        ownerUserId = owner,
                        createdAt = createdAt,
                        expiresAt = expiresAt,
                        content = message,
                        isSeen = event.eventId in seenIds,
                        caption = metadata["caption"] as? String ?: "",
                        keepOnProfile = keepOnProfile,
                        allowScreenshots = metadata["allow_screenshots"] as? Boolean ?: true,
                )
            }
        }

        result.distinctBy { it.event.eventId }.sortedByDescending { it.createdAt }
    }

    suspend fun markSeen(eventId: String) = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession() ?: return@withContext
        val current = session.accountDataService()
                .getUserAccountDataEvent(STORY_SEEN_ACCOUNT_DATA)
                ?.content
                ?.get("event_ids")
        val seen = (current as? List<*>)?.filterIsInstance<String>()?.toMutableSet() ?: mutableSetOf()
        seen.add(eventId)
        // Account data syncs between the user's Matrix clients. Keep the list bounded.
        session.accountDataService().updateUserAccountData(
                STORY_SEEN_ACCOUNT_DATA,
                mapOf("event_ids" to seen.toList().takeLast(MAX_SEEN_EVENT_IDS)),
        )
    }

    fun buildThumbnailData(story: Story): ImageContentRenderer.Data? {
        val metrics = context.resources.displayMetrics
        val maxWidth = metrics.widthPixels.coerceAtLeast(1)
        val maxHeight = metrics.heightPixels.coerceAtLeast(1)

        return when (val message = story.content) {
            is MessageVideoContent -> {
                val videoInfo: VideoInfo = message.videoInfo ?: return null
                ImageContentRenderer.Data(
                        eventId = story.event.eventId,
                        filename = message.body,
                        mimeType = videoInfo.mimeType,
                        url = videoInfo.getThumbnailUrl(),
                        elementToDecrypt = videoInfo.thumbnailFile?.toElementToDecrypt(),
                        height = videoInfo.height,
                        maxHeight = maxHeight,
                        width = videoInfo.width,
                        maxWidth = maxWidth,
                )
            }
            is MessageImageInfoContent -> {
                val attachment = message as? MessageWithAttachmentContent ?: return null
                ImageContentRenderer.Data(
                        eventId = story.event.eventId,
                        filename = message.body,
                        mimeType = attachment.mimeType,
                        url = attachment.getFileUrl(),
                        elementToDecrypt = attachment.encryptedFileInfo?.toElementToDecrypt(),
                        height = message.info?.height,
                        maxHeight = maxHeight,
                        width = message.info?.width,
                        maxWidth = maxWidth,
                )
            }
            else -> null
        }
    }

    suspend fun getEligibleRooms(): List<StoryRoom> = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession() ?: return@withContext emptyList()
        val query = RoomSummaryQueryParams.Builder().also {
            it.memberships = listOf(Membership.JOIN)
            it.roomCategoryFilter = RoomCategoryFilter.ONLY_ROOMS
            it.excludeType = listOf(STORY_ROOM_TYPE, "m.space")
        }.build()
        session.roomService().getRoomSummaries(query)
                .filter { !it.isDirect && it.roomType != STORY_ROOM_TYPE }
                .map { summary ->
                    StoryRoom(
                            roomId = summary.roomId,
                            displayName = summary.displayName.takeIf { it.isNotBlank() }
                                    ?: summary.name.takeIf { it.isNotBlank() }
                                    ?: summary.roomId,
                            joinedMembersCount = summary.joinedMembersCount ?: 0,
                    )
                }
                .sortedBy { it.displayName.lowercase() }
    }

    private suspend fun getSelectedRoomMemberIds(
            session: Session,
            selectedRoomIds: Set<String>,
    ): Set<String> {
        if (selectedRoomIds.isEmpty()) {
            error("Select at least one room whose members can view this story")
        }
        val validRooms = getEligibleRooms().associateBy { it.roomId }
        val unknownRoomIds = selectedRoomIds - validRooms.keys
        if (unknownRoomIds.isNotEmpty()) {
            error("One or more selected rooms are no longer available. Reopen the room picker and try again.")
        }

        val memberQuery = RoomMemberQueryParams.Builder().apply {
            memberships = listOf(Membership.JOIN)
            excludeSelf = true
        }.build()
        val recipientIds = linkedSetOf<String>()
        selectedRoomIds.forEach { roomId ->
            val room = session.roomService().getRoom(roomId)
                    ?: error("A selected room is no longer available")
            room.membershipService().loadRoomMembersIfNeeded()
            room.membershipService().getRoomMembers(memberQuery)
                    .asSequence()
                    .filter { it.membership == Membership.JOIN && it.userId != session.myUserId }
                    .mapTo(recipientIds) { it.userId }
        }
        if (recipientIds.isEmpty()) {
            error("The selected rooms have no other joined members")
        }
        return recipientIds
    }

    suspend fun getEligibleContacts(): List<StoryContact> = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession() ?: return@withContext emptyList()
        val query = RoomSummaryQueryParams.Builder().also {
            it.memberships = listOf(Membership.JOIN)
            it.roomCategoryFilter = RoomCategoryFilter.ONLY_DM
            it.excludeType = listOf(STORY_ROOM_TYPE, "m.space")
        }.build()
        val labels = linkedMapOf<String, String>()
        session.roomService().getRoomSummaries(query).forEach { summary ->
            (summary.otherMemberIds + listOfNotNull(summary.directUserId))
                    .filter { it.isNotBlank() && it != session.myUserId }
                    .forEach { userId ->
                        labels.putIfAbsent(
                                userId,
                                summary.displayName.takeIf { it.isNotBlank() }
                                        ?: userId.substringBefore(':').removePrefix("@")
                        )
                    }
        }
        labels.map { StoryContact(it.key, it.value) }
                .sortedBy { it.displayName.lowercase() }
    }

    suspend fun getCloseFriends(): Set<String> = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession() ?: return@withContext emptySet()
        val current = session.accountDataService()
                .getUserAccountDataEvent(STORY_CLOSE_FRIENDS)
                ?.content
                ?.get("user_ids")
        (current as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty()
    }

    suspend fun saveCloseFriends(userIds: Set<String>) = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession() ?: return@withContext
        val validIds = userIds.filter { it.startsWith("@") && it.contains(':') }.distinct().take(500)
        session.accountDataService().updateUserAccountData(
                STORY_CLOSE_FRIENDS,
                mapOf("user_ids" to validIds),
        )
    }

    private fun parseOwner(roomName: String): String? {
        if (roomName.startsWith(PUBLIC_STORY_ROOM_NAME_PREFIX)) {
            return roomName.removePrefix(PUBLIC_STORY_ROOM_NAME_PREFIX)
                    .takeIf { it.startsWith("@") && it.contains(':') }
        }
        if (!roomName.startsWith(STORY_ROOM_NAME_PREFIX + "|")) return null
        return roomName.removePrefix(STORY_ROOM_NAME_PREFIX + "|")
                .substringBefore('|')
                .takeIf { it.startsWith("@") && it.contains(':') }
    }

    private fun publicStoryRoomName(userId: String): String =
            "$PUBLIC_STORY_ROOM_NAME_PREFIX$userId"

    private fun queryMetadata(resolver: ContentResolver, uri: Uri): Pair<String?, Long> {
        var name: String? = null
        var size = 0L
        val cursor: Cursor? = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = it.getString(nameIndex)
                if (sizeIndex >= 0) size = it.getLong(sizeIndex).coerceAtLeast(0L)
            }
        }
        return (name ?: uri.lastPathSegment ?: "story-media") to size
    }

    companion object {
        const val STORY_ROOM_TYPE = "org.messenger.stories"
        const val STORY_ROOM_NAME_PREFIX = "org.messenger.story"
        const val STORY_METADATA_KEY = "org.messenger.story"
        private const val PUBLIC_STORY_ROOM_NAME_PREFIX = "Messenger Stories | "
        private const val PUBLIC_STORY_ROOM_SEARCH_TERM = "Messenger Stories"
        private const val STORY_UPLOAD_TIMEOUT_MS = 180_000L
        private const val STORY_UPLOAD_POLL_INTERVAL_MS = 300L
        private const val PUBLIC_DIRECTORY_PAGE_SIZE = 100
        private const val MAX_PUBLIC_DIRECTORY_PAGES = 2
        private const val MAX_PUBLIC_DIRECTORY_SERVERS = 4
        private const val MAX_PUBLIC_STORY_ROOMS_TO_JOIN = 60
        private const val STORY_SEEN_ACCOUNT_DATA = "org.messenger.story.seen"
        private const val STORY_CLOSE_FRIENDS = "org.messenger.story.close_friends"
        const val AUDIENCE_EVERYONE = "everyone"
        const val AUDIENCE_CONTACTS = "contacts"
        const val AUDIENCE_CLOSE_FRIENDS = "close_friends"
        const val AUDIENCE_SELECTED = "selected"
        const val AUDIENCE_SELECTED_ROOMS = "selected_rooms"
        private const val MAX_SEEN_EVENT_IDS = 500
        private const val STORY_FORMAT_VERSION = 2
        private const val MSGTYPE_IMAGE = "m.image"
        private const val MSGTYPE_VIDEO = "m.video"
    }
}
