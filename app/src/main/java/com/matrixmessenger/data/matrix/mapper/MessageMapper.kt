package com.matrixmessenger.data.matrix.mapper

import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.events.model.content.MessageContent
import org.matrix.android.sdk.api.session.events.model.content.MessageType
import com.matrixmessenger.domain.model.MatrixMessage
import com.matrixmessenger.domain.model.MessageType as DomainMessageType
import com.matrixmessenger.domain.model.MessageStatus
import com.matrixmessenger.domain.model.ReplyData
import com.matrixmessenger.domain.model.ReactionData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageMapper @Inject constructor() {

    fun mapToMatrixMessage(event: TimelineEvent, currentUserId: String): MatrixMessage {
        val root = event.root
        val content = root.getClearContent()
        
        val senderId = root.senderId ?: ""
        val isOwn = senderId == currentUserId
        
        // Determine message type
        val messageType = when (root.getClearType()) {
            EventType.MESSAGE -> {
                val msgType = (content as? MessageContent)?.msgType
                when (msgType) {
                    MessageType.MSGTYPE_TEXT -> DomainMessageType.TEXT
                    MessageType.MSGTYPE_IMAGE -> DomainMessageType.IMAGE
                    MessageType.MSGTYPE_VIDEO -> DomainMessageType.VIDEO
                    MessageType.MSGTYPE_AUDIO -> DomainMessageType.VOICE
                    MessageType.MSGTYPE_FILE -> DomainMessageType.FILE
                    MessageType.MSGTYPE_LOCATION -> DomainMessageType.LOCATION
                    else -> DomainMessageType.TEXT
                }
            }
            EventType.STICKER -> DomainMessageType.STICKER
            EventType.REACTION -> DomainMessageType.TEXT // Reactions are handled separately
            else -> DomainMessageType.TEXT
        }

        // Extract text content
        val text = (content as? MessageContent)?.body
        val formattedText = (content as? MessageContent)?.formattedBody

        // Check if message is a reply
        val replyData = extractReplyData(content)

        // Determine message status based on send state
        val status = when {
            event.isLocalEcho() -> MessageStatus.SENDING
            event.sendState.isSent() -> MessageStatus.SENT
            event.sendState.isDelivered() -> MessageStatus.DELIVERED
            event.sendState.hasFailed() -> MessageStatus.FAILED
            else -> MessageStatus.SENT
        }

        // Extract reactions
        val reactions = extractReactions(event)

        // Check forward info
        val forwardInfo = extractForwardInfo(content)

        // Extract poll data if present
        val pollData = extractPollData(content)

        // Extract redaction reason if redacted
        val redactionReason = if (root.type == EventType.REDACTION) {
            root.redactsEvent?.content?.reason
        } else null

        return MatrixMessage(
            eventId = event.eventId,
            roomId = event.roomId ?: "",
            senderId = senderId,
            senderDisplayName = event.senderInfo.disambiguatedDisplayName ?: senderId,
            senderAvatarUrl = event.senderInfo.avatarUrl,
            timestamp = root.originServerTs ?: 0L,
            type = messageType,
            text = text,
            formattedText = formattedText,
            status = status,
            isOwn = isOwn,
            isEdited = event.isEdited(),
            isForwarded = forwardInfo != null,
            forwardedFromName = forwardInfo?.forwardedFromName,
            replyToEventId = replyData?.eventId,
            replyToMessage = replyData,
            mediaUrl = extractMediaUrl(content),
            mediaLocalPath = null,
            mediaMimeType = (content as? MessageContent)?.info?.mimeType,
            mediaSize = (content as? MessageContent)?.info?.size,
            mediaWidth = (content as? MessageContent)?.info?.width,
            mediaHeight = (content as? MessageContent)?.info?.height,
            mediaDurationMs = (content as? MessageContent)?.info?.duration,
            voiceWaveform = (content as? MessageContent)?.info?.waveform,
            thumbnailUrl = (content as? MessageContent)?.info?.thumbnailUrl,
            fileName = (content as? MessageContent)?.body,
            reactions = reactions,
            latitude = extractLatitude(content),
            longitude = extractLongitude(content),
            locationDescription = text,
            pollData = pollData,
            isRedacted = root.type == EventType.REDACTION,
            redactionReason = redactionReason,
            selfDestructAfterMs = null // Self-destruct not yet supported in Matrix
        )
    }

    private fun extractReplyData(content: Any?): ReplyData? {
        val messageContent = content as? MessageContent ?: return null
        val relatesTo = messageContent.relatesTo ?: return null
        
        // Check if it's a reply
        if (relatesTo.type != "m.reply") return null
        
        val inReplyTo = relatesTo.otherProperties?.get("in_reply_to") as? Map<*, *> ?: return null
        val eventId = inReplyTo["event_id"] as? String ?: return null
        
        // Extract preview data from the fallback
        val fallback = inReplyTo["fallback"] as? String ?: ""
        val parts = fallback.split(": ", limit = 2)
        
        return ReplyData(
            eventId = eventId,
            senderId = parts.firstOrNull()?.trim()?.removePrefix("* ") ?: "",
            senderName = parts.firstOrNull()?.trim()?.removePrefix("* ") ?: "",
            text = parts.lastOrNull()?.trim(),
            mediaUrl = null,
            mediaType = null
        )
    }

    private fun extractReactions(event: TimelineEvent): Map<String, ReactionData> {
        // Get aggregated reactions from the event
        val reactions = mutableMapOf<String, ReactionData>()
        
        // Use the annotations service to get reactions
        event.annotations?.reactions?.forEach { (emoji, reactionSummary) ->
            reactions[emoji] = ReactionData(
                emoji = emoji,
                count = reactionSummary.totalCount,
                senders = reactionSummary.reactions.map { it.senderId },
                isMine = reactionSummary.reactions.any { it.senderId == event.root.senderId }
            )
        }
        
        return reactions
    }

    private fun extractMediaUrl(content: Any?): String? {
        return (content as? MessageContent)?.url
    }

    private fun extractLatitude(content: Any?): Double? {
        val messageContent = content as? MessageContent ?: return null
        return messageContent.otherFields?.get("geo_uri")
            ?.toString()
            ?.substringAfter("geo:")
            ?.split(",")
            ?.firstOrNull()
            ?.toDoubleOrNull()
    }

    private fun extractLongitude(content: Any?): Double? {
        val messageContent = content as? MessageContent ?: return null
        return messageContent.otherFields?.get("geo_uri")
            ?.toString()
            ?.substringAfter("geo:")
            ?.split(",")
            ?.lastOrNull()
            ?.toDoubleOrNull()
    }

    private data class ForwardInfo(
        val forwardedFromName: String?
    )

    private fun extractForwardInfo(content: Any?): ForwardInfo? {
        val messageContent = content as? MessageContent ?: return null
        val relatesTo = messageContent.relatesTo ?: return null
        
        // Check if it's a forward (m.forward relation type)
        if (relatesTo.type != "m.forward") return null
        
        // Extract forwarded from info
        val forwardedFromDisplayName = relatesTo.otherProperties?.get("forwarded_from_display_name") as? String
        
        return ForwardInfo(
            forwardedFromName = forwardedFromDisplayName
        )
    }

    private fun extractPollData(content: Any?): com.matrixmessenger.domain.model.PollData? {
        val messageContent = content as? MessageContent ?: return null
        
        // Check if this is a poll event type
        if (messageContent.msgType != "m.poll.start") return null
        
        // Extract poll information from the content
        val question = messageContent.body
        val pollInfo = messageContent.otherFields?.get("poll") as? Map<*, *> ?: return null
        val answersList = pollInfo["answers"] as? List<Map<*, *>> ?: return null
        
        val answers = answersList.mapNotNull { answer ->
            val id = answer["id"] as? String ?: return@mapNotNull null
            val text = answer["text"] as? String ?: return@mapNotNull null
            com.matrixmessenger.domain.model.PollAnswer(id = id, text = text)
        }
        
        val allowMultiple = pollInfo["allow_multiple"] as? Boolean ?: false
        
        return com.matrixmessenger.domain.model.PollData(
            question = question,
            answers = answers,
            allowMultiple = allowMultiple,
            totalVotes = 0, // Will be populated from aggregations
            userVotes = emptyList()
        )
    }
}
