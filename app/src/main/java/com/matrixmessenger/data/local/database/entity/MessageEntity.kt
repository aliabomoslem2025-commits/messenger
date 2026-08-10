package com.matrixmessenger.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val eventId: String,
    val roomId: String,
    val senderId: String,
    val senderDisplayName: String,
    val senderAvatarUrl: String?,
    val timestamp: Long,
    val type: String, // TEXT, IMAGE, VIDEO, VOICE, FILE, etc.
    val text: String?,
    val formattedText: String?,
    val status: String, // SENDING, SENT, DELIVERED, READ, FAILED
    val isOwn: Boolean,
    val isEdited: Boolean,
    val isForwarded: Boolean,
    val forwardedFromName: String?,
    val replyToEventId: String?,
    val replyToSenderId: String?,
    val replyToSenderName: String?,
    val replyToText: String?,
    val mediaUrl: String?,
    val mediaLocalPath: String?,
    val mediaMimeType: String?,
    val mediaSize: Long?,
    val mediaWidth: Int?,
    val mediaHeight: Int?,
    val mediaDurationMs: Long?,
    val voiceWaveform: String?, // JSON array of integers
    val thumbnailUrl: String?,
    val fileName: String?,
    val reactions: String?, // JSON map
    val latitude: Double?,
    val longitude: Double?,
    val locationDescription: String?,
    val pollData: String?, // JSON
    val isRedacted: Boolean,
    val redactionReason: String?
)
