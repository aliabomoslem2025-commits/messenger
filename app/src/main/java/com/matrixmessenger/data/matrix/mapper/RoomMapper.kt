package com.matrixmessenger.data.matrix.mapper

import org.matrix.android.sdk.api.session.room.model.RoomSummary
import com.matrixmessenger.domain.model.MatrixRoom
import com.matrixmessenger.domain.model.RoomType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomMapper @Inject constructor() {

    fun mapToMatrixRoom(summary: RoomSummary): MatrixRoom {
        val isDirect = summary.isDirect
        val directUserId = if (isDirect) {
            summary.otherUserId
        } else null

        val roomType = when {
            summary.isDirect -> RoomType.DIRECT
            summary.name == "Saved Messages" -> RoomType.SAVED_MESSAGES
            else -> RoomType.GROUP
        }

        return MatrixRoom(
            roomId = summary.roomId,
            displayName = summary.displayName ?: summary.roomId,
            avatarUrl = summary.avatarUrl,
            topic = summary.topic,
            roomType = roomType,
            memberCount = summary.joinedMemberCount,
            lastMessage = null, // Will be populated by message mapper
            lastMessageTimestamp = summary.latestPreviewableEvent?.root?.originServerTs ?: 0L,
            unreadCount = summary.notificationCount,
            mentionCount = summary.highlightCount,
            isPinned = false, // TODO: Check pinned state
            isMuted = false, // TODO: Check mute state
            isEncrypted = summary.isEncrypted,
            isDirect = isDirect,
            directUserId = directUserId,
            isOnline = false, // Presence info from user mapper
            lastSeen = null,
            typingUsers = emptyList(), // Will be populated from flow
            draft = null, // Will be populated from draft service
            hasNewMessages = summary.notificationCount > 0
        )
    }
}
