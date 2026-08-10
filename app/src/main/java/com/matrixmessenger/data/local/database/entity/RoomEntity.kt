package com.matrixmessenger.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rooms")
data class RoomEntity(
    @PrimaryKey val roomId: String,
    val displayName: String,
    val avatarUrl: String?,
    val topic: String?,
    val roomType: String, // DIRECT, GROUP, CHANNEL, BOT, SAVED_MESSAGES
    val memberCount: Int,
    val lastMessageId: String?,
    val lastMessageText: String?,
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
    val draft: String?
)
