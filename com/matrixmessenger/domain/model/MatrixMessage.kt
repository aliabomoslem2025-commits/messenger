package com.matrixmessenger.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MatrixMessage(
    val eventId: String,
    val roomId: String,
    val senderId: String,
    val senderDisplayName: String,
    val senderAvatarUrl: String?,
    val timestamp: Long,
    val type: MessageType,
    val text: String?,
    val formattedText: String?,
    val status: MessageStatus,
    val isOwn: Boolean,
    val isEdited: Boolean,
    val isForwarded: Boolean,
    val forwardedFromName: String?,
    val replyToEventId: String?,
    val replyToMessage: ReplyData?,
    val mediaUrl: String?,
    val mediaLocalPath: String?,
    val mediaMimeType: String?,
    val mediaSize: Long?,
    val mediaWidth: Int?,
    val mediaHeight: Int?,
    val mediaDurationMs: Long?,
    val voiceWaveform: List<Int>?,
    val thumbnailUrl: String?,
    val fileName: String?,
    val reactions: Map<String, ReactionData>,
    val latitude: Double?,
    val longitude: Double?,
    val locationDescription: String?,
    val pollData: PollData?,
    val isRedacted: Boolean,
    val redactionReason: String?,
    val selfDestructAfterMs: Long?
) : Parcelable

@Parcelize
data class ReplyData(
    val eventId: String,
    val senderId: String,
    val senderName: String,
    val text: String?,
    val mediaUrl: String?,
    val mediaType: MessageType?
) : Parcelable

@Parcelize
data class ReactionData(
    val emoji: String,
    val count: Int,
    val senders: List<String>,
    val isMine: Boolean
) : Parcelable

@Parcelize
data class PollData(
    val question: String,
    val options: List<PollOption>,
    val totalVotes: Int,
    val isClosed: Boolean,
    val myVote: String?
) : Parcelable

@Parcelize
data class PollOption(
    val id: String,
    val text: String,
    val voteCount: Int,
    val percentage: Float
) : Parcelable
