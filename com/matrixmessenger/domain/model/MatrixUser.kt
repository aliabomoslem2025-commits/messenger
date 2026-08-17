package com.matrixmessenger.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class PresenceStatus {
    ONLINE,
    AWAY,
    OFFLINE
}

@Parcelize
data class MatrixUser(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val isOnline: Boolean,
    val lastSeen: Long?,
    val presenceStatus: PresenceStatus?
) : Parcelable

@Parcelize
data class MediaItem(
    val eventId: String,
    val url: String,
    val thumbnailUrl: String?,
    val type: MessageType,
    val width: Int,
    val height: Int,
    val duration: Long?,
    val caption: String?,
    val senderName: String,
    val timestamp: Long
) : Parcelable

data class CallState(
    val callId: String,
    val roomId: String,
    val remoteUserId: String,
    val remoteDisplayName: String,
    val remoteAvatarUrl: String?,
    val isVideo: Boolean,
    val status: CallStatus,
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val isSpeakerOn: Boolean
)

enum class CallStatus {
    CONNECTING, RINGING, CONNECTED, ON_HOLD, ENDING, ENDED, FAILED
}
