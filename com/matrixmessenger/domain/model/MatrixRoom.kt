package com.matrixmessenger.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class RoomType { DIRECT, GROUP, CHANNEL, BOT, SAVED_MESSAGES }
enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }
enum class MessageType {
    TEXT, IMAGE, IMAGE_ALBUM, VIDEO, VOICE, VIDEO_NOTE,
    FILE, STICKER, GIF, LOCATION, CONTACT, POLL, SERVICE
}

@Parcelize
data class MatrixRoom(
    val roomId: String,
    val displayName: String,
    val avatarUrl: String?,
    val topic: String?,
    val roomType: RoomType,
    val memberCount: Int,
    val lastMessage: MatrixMessage?,
    val lastMessageTimestamp: Long,
    val unreadCount: Int,
    val mentionCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isEncrypted: Boolean,
    val isDirect: Boolean,
    val directUserId: String?,
    val isOnline: Boolean,
    val lastSeen: Long?,
    val typingUsers: List<String>,
    val draft: String?,
    val hasNewMessages: Boolean
) : Parcelable
