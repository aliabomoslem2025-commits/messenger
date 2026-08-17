package com.matrixmessenger.feature.call.domain.model

import com.matrixmessenger.core.model.MatrixUser

/**
 * Represents the current state of a VoIP call.
 */
sealed interface CallState {
    data object Idle : CallState
    data object Dialing : CallState // Outgoing, waiting for answer
    data object Ringing : CallState // Incoming, waiting for user action
    data object Connecting : CallState // WebRTC negotiation in progress
    data object Connected : CallState // Media flowing
    data object Reconnecting : CallState // Network instability
    data object Ended : CallState
    data class Failed(val reason: String) : CallState
}

/**
 * Represents the type of the call.
 */
enum class CallType {
    AUDIO,
    VIDEO
}

/**
 * Represents the direction of the call.
 */
enum class CallDirection {
    INCOMING,
    OUTGOING
}

/**
 * UI Model for a completed call history entry.
 */
data class CallHistoryEntry(
    val id: String,
    val roomId: String,
    val otherUser: MatrixUser,
    val type: CallType,
    val direction: CallDirection,
    val timestamp: Long,
    val durationSeconds: Int,
    val wasMissed: Boolean
)

/**
 * Aggregated state of the local media devices.
 */
data class LocalMediaState(
    val isMicrophoneMuted: Boolean = false,
    val isCameraEnabled: Boolean = false, // For video calls
    val isSpeakerOn: Boolean = false,
    val hasFrontCamera: Boolean = true,
    val hasBackCamera: Boolean = true,
    val isUsingFrontCamera: Boolean = true
)
