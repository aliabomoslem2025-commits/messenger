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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import org.matrix.android.sdk.api.session.crypto.attachments.toElementToDecrypt
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.room.RoomSummaryQueryParams
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
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.query.RoomCategoryFilter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stories are implemented as private, encrypted, app-specific Matrix rooms.
 *
 * Matrix does not currently provide a stable, universally-supported built-in Stories
 * event type, so this app uses standard m.image/m.video events plus a namespaced
 * expiry payload. Each story is a separate room; it is not public, is encrypted, and
 * only invites members from the author's existing joined DM rooms. Story rooms are
 * hidden from the chat-folder queries by STORY_ROOM_TYPE.
 *
 * Expiry is enforced by clients when listing/viewing stories. Homeservers may retain
 * historical events according to their server retention policy; this does not claim
 * a network-wide hard-delete guarantee.
 */
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
    ) {
        val isVideo: Boolean
            get() = content.msgType == MSGTYPE_VIDEO
    }

    suspend fun publishStory(uri: Uri): String = withContext(Dispatchers.IO) {
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
        val expiresAt = now + STORY_LIFETIME_MS

        // A contacts-only audience: users from joined one-to-one Matrix rooms.
        // The room remains private and encrypted; no arbitrary/public audience exists.
        val dmQuery = RoomSummaryQueryParams.Builder().also {
            it.memberships = listOf(Membership.JOIN)
            it.roomCategoryFilter = RoomCategoryFilter.ONLY_DM
            it.excludeType = listOf(STORY_ROOM_TYPE, "m.space")
        }.build()
        val recipients = session.roomService()
                .getRoomSummaries(dmQuery)
                .flatMap { it.otherMemberIds + listOfNotNull(it.directUserId) }
                .filter { it.isNotBlank() && it != session.myUserId }
                .distinct()

        val roomName = "${STORY_ROOM_NAME_PREFIX}|${session.myUserId}|$now"
        val params = CreateRoomParams().apply {
            this.name = roomName
            roomType = STORY_ROOM_TYPE
            visibility = RoomDirectoryVisibility.PRIVATE
            preset = CreateRoomPreset.PRESET_PRIVATE_CHAT
            historyVisibility = RoomHistoryVisibility.SHARED
            invitedUserIds.addAll(recipients)
            enableEncryption()
            // Invited viewers may read the story, but only its author can post.
            powerLevelContentOverride = PowerLevelsContent(eventsDefault = 100)
        }
        val roomId = session.roomService().createRoom(params)
        val room = session.roomService().getRoom(roomId)
                ?: error("Matrix created the story room but it is not available locally")

        val attachment = ContentAttachmentData(
                size = size,
                name = name,
                queryUri = uri,
                mimeType = mimeType,
                type = mediaType,
                date = now,
        )
        room.sendService().sendMedia(
                attachment = attachment,
                compressBeforeSending = mediaType == ContentAttachmentData.Type.IMAGE,
                roomIds = setOf(roomId),
                additionalContent = mapOf(
                        STORY_METADATA_KEY to mapOf(
                                "version" to STORY_FORMAT_VERSION,
                                "created_at" to now,
                                "expires_at" to expiresAt,
                        )
                ),
        )
        roomId
    }

    suspend fun loadStories(joinInvitations: Boolean = true): List<Story> = withContext(Dispatchers.IO) {
        val session = activeSessionHolder.getSafeActiveSession() ?: return@withContext emptyList()
        val query = RoomSummaryQueryParams.Builder().also {
            it.memberships = listOf(Membership.JOIN, Membership.INVITE)
            it.includeType = listOf(STORY_ROOM_TYPE)
            it.excludeType = null
        }.build()

        // Invitations are joined only after the user opens Stories, so the home
        // screen itself does not silently join newly invited rooms.
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
                if (expiresAt <= now) return@eventLoop
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
                )
            }
        }

        result.sortedByDescending { it.createdAt }
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

    private fun parseOwner(roomName: String): String? {
        if (!roomName.startsWith(STORY_ROOM_NAME_PREFIX + "|")) return null
        return roomName.removePrefix(STORY_ROOM_NAME_PREFIX + "|")
                .substringBefore('|')
                .takeIf { it.startsWith("@") && it.contains(':') }
    }

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
        private const val STORY_SEEN_ACCOUNT_DATA = "org.messenger.story.seen"
        private const val STORY_LIFETIME_MS = 24L * 60L * 60L * 1000L
        private const val MAX_SEEN_EVENT_IDS = 500
        private const val STORY_FORMAT_VERSION = 1
        private const val MSGTYPE_IMAGE = "m.image"
        private const val MSGTYPE_VIDEO = "m.video"
    }
}
