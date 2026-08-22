package com.matrixmessenger.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * Represents a Matrix user
 */
@Parcelize
data class MatrixUser(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val presence: PresenceState = PresenceState.OFFLINE,
    val lastSeen: Date? = null,
    val statusMessage: String? = null,
    val isBot: Boolean = false
) : Parcelable {

    /**
     * Get display name or fall back to user ID
     */
    fun getDisplayNameOrId(): String {
        return displayName ?: userId.extractUsername()
    }

    /**
     * Get initials for avatar
     */
    fun getInitials(): String {
        return (displayName ?: userId)
            .filter { it.isLetter() || it.isDigit() }
            .take(2)
            .uppercase()
    }

    /**
     * Extract username from Matrix user ID
     */
    private fun String.extractUsername(): String {
        return removePrefix("@").substringBefore(":")
    }
}

@Parcelize
enum class PresenceState : Parcelable {
    ONLINE,
    UNAVAILABLE,
    OFFLINE,
    UNKNOWN
}

/**
 * Represents a Matrix room (chat)
 */
@Parcelize
data class MatrixRoom(
    val roomId: String,
    val name: String?,
    val topic: String?,
    val avatarUrl: String?,
    val alias: String? = null,
    val isDirect: Boolean = false,
    val isEncrypted: Boolean = true,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val membership: MembershipState = MembershipState.JOIN,
    val unreadCount: Int = 0,
    val lastMessage: MatrixMessage?,
    val timestamp: Date?,
    val memberCount: Int = 0,
    val inviter: MatrixUser? = null,
    val canonicalAlias: String? = null
) : Parcelable {

    /**
     * Get display name for the room
     */
    fun getDisplayName(): String {
        return when {
            name?.isNotBlank() == true -> name!!
            alias?.isNotBlank() == true -> alias!!
            canonicalAlias?.isNotBlank() == true -> canonicalAlias!!
            else -> "Unknown Room"
        }
    }

    /**
     * Get short identifier for the room
     */
    fun getShortId(): String {
        return roomId.substringAfter(":", "").take(8)
    }

    /**
     * Check if room has unread messages
     */
    fun hasUnreadMessages(): Boolean = unreadCount > 0
}

@Parcelize
enum class MembershipState : Parcelable {
    INVITE,
    JOIN,
    LEAVE,
    BAN,
    KNOCK
}

/**
 * Represents a message in a Matrix room
 */
@Parcelize
data class MatrixMessage(
    val eventId: String,
    val roomId: String,
    val senderId: String,
    val senderDisplayName: String?,
    val senderAvatarUrl: String?,
    val body: String,
    val formattedBody: String?,
    val messageType: MessageType = MessageType.TEXT,
    val timestamp: Date,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isRedacted: Boolean = false,
    val redactionReason: String? = null,
    val isForwarded: Boolean = false,
    val reactions: List<Reaction> = emptyList(),
    val replyToEventId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
    val localId: String? = null,
    val editTimestamp: Long? = null
) : Parcelable {

    /**
     * Check if message was sent by current user
     */
    fun isFromCurrentUser(currentUserId: String): Boolean {
        return senderId == currentUserId
    }

    /**
     * Get display body (formatted or plain)
     */
    fun getDisplayBody(): String {
        return formattedBody ?: body
    }

    /**
     * Check if message is media type
     */
    fun isMediaType(): Boolean {
        return messageType in listOf(
            MessageType.IMAGE,
            MessageType.VIDEO,
            MessageType.AUDIO,
            MessageType.FILE,
            MessageType.STICKER
        )
    }
}

@Parcelize
data class Reaction(
    val key: String,
    val count: Int,
    val isAddedByMe: Boolean = false
) : Parcelable

@Parcelize
enum class MessageType : Parcelable {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    LOCATION,
    STICKER,
    VIDEO_NOTE,
    EMOTE,
    NOTICE,
    REDACTED,
    UNKNOWN
}

@Parcelize
data class Attachment(
    val url: String,
    val mimeType: String,
    val size: Long,
    val fileName: String?,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null,
    val thumbnailUrl: String? = null
) : Parcelable

@Parcelize
enum class DeliveryStatus : Parcelable {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

/**
 * Represents media item (image, video, file)
 */
@Parcelize
data class MediaItem(
    val id: String,
    val url: String,
    val thumbnailUrl: String?,
    val mimeType: String,
    val fileSize: Long,
    val fileName: String?,
    val width: Int? = null,
    val height: Int? = null,
    val duration: Long? = null
) : Parcelable

/**
 * Represents user profile information
 */
@Parcelize
data class UserProfile(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val email: String?,
    val phone: String?,
    val bio: String?,
    val threePids: List<ThirdPartyIdentifier> = emptyList()
) : Parcelable

@Parcelize
data class ThirdPartyIdentifier(
    val medium: String,
    val address: String,
    val validatedAt: Date?
) : Parcelable

@Parcelize
data class SyncToken(
    val token: String,
    val timestamp: Date
) : Parcelable

/**
 * Represents call state for voice/video calls
 */
@Parcelize
data class CallState(
    val roomId: String,
    val callId: String,
    val callerId: String,
    val type: CallType,
    val status: CallStatus,
    val timestamp: Long
) : Parcelable {

    enum class CallType {
        VOICE,
        VIDEO
    }

    enum class CallStatus {
        RINGING,
        CONNECTED,
        ENDED,
        MISSED,
        REJECTED
    }
}

typealias Room = MatrixRoom
typealias Message = MatrixMessage
typealias User = MatrixUser
