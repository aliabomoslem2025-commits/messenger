package com.matrixmessenger.data.matrix.mapper

import org.matrix.android.sdk.api.session.events.model.getRelationContent
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.hasBeenEdited
import org.matrix.android.sdk.api.session.events.model.EventType
import org.matrix.android.sdk.api.session.room.model.message.MessageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageType
import org.matrix.android.sdk.api.session.room.model.message.MessageImageContent
import org.matrix.android.sdk.api.session.room.model.message.MessageVideoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageAudioContent
import org.matrix.android.sdk.api.session.room.model.message.MessageFileContent
import org.matrix.android.sdk.api.session.room.model.message.MessageStickerContent
import org.matrix.android.sdk.api.session.room.model.message.MessageContentWithFormattedBody
import org.matrix.android.sdk.api.session.room.send.SendState
import com.matrixmessenger.domain.model.MatrixMessage
import com.matrixmessenger.domain.model.MessageType as DomainMessageType
import com.matrixmessenger.domain.model.DeliveryStatus
import com.matrixmessenger.domain.model.Attachment
import com.matrixmessenger.domain.model.Reaction
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageMapper @Inject constructor() {

    fun mapToMatrixMessage(
        event: TimelineEvent, 
        currentUserId: String,
        latestOtherReadReceiptTs: Long = 0L
    ): MatrixMessage {
        val root = event.root
        val content = root.getClearContent().toModel<MessageContent>()
        
        val senderId = root.senderId ?: ""
        
        // Determine message type
        val domainMessageType = when (root.getClearType()) {
            EventType.MESSAGE -> {
                val msgType = content?.msgType
                when (msgType) {
                    MessageType.MSGTYPE_TEXT -> DomainMessageType.TEXT
                    MessageType.MSGTYPE_IMAGE -> DomainMessageType.IMAGE
                    MessageType.MSGTYPE_VIDEO -> {
                        val isVideoNote = root.getClearContent()?.get("org.matrix.msc2457.video_note") != null
                        if (isVideoNote) DomainMessageType.VIDEO_NOTE else DomainMessageType.VIDEO
                    }
                    MessageType.MSGTYPE_AUDIO -> DomainMessageType.AUDIO
                    MessageType.MSGTYPE_FILE -> DomainMessageType.FILE
                    MessageType.MSGTYPE_LOCATION -> DomainMessageType.LOCATION
                    MessageType.MSGTYPE_EMOTE -> DomainMessageType.EMOTE
                    MessageType.MSGTYPE_NOTICE -> DomainMessageType.NOTICE
                    else -> DomainMessageType.TEXT
                }
            }
            EventType.STICKER -> DomainMessageType.STICKER
            EventType.REACTION -> DomainMessageType.TEXT
            else -> DomainMessageType.UNKNOWN
        }

        // Extract text content
        val text = content?.body ?: ""
        val formattedText = (content as? MessageContentWithFormattedBody)?.formattedBody

        // Determine delivery status
        val status = when (event.root.sendState) {
            SendState.SYNCED -> {
                val eventTs = event.root.originServerTs ?: 0L
                if (senderId == currentUserId) {
                    if (latestOtherReadReceiptTs > 0 && eventTs <= latestOtherReadReceiptTs) {
                        DeliveryStatus.READ
                    } else {
                        DeliveryStatus.SENT
                    }
                } else {
                    DeliveryStatus.READ // Incoming messages are "read" by default in terms of delivery
                }
            }
            SendState.SENDING -> DeliveryStatus.SENDING
            SendState.FAILED_UNKNOWN_DEVICES,
            SendState.UNDELIVERED -> DeliveryStatus.FAILED
            else -> {
                if (event.root.sendState.isSending()) {
                    DeliveryStatus.SENDING
                } else if (senderId == currentUserId) {
                    val eventTs = event.root.originServerTs ?: 0L
                    if (latestOtherReadReceiptTs > 0 && eventTs > 0 && eventTs <= latestOtherReadReceiptTs) {
                        DeliveryStatus.READ
                    } else {
                        DeliveryStatus.SENT
                    }
                } else {
                    DeliveryStatus.READ
                }
            }
        }

        // Extract reactions
        val reactions = mutableListOf<Reaction>()
        event.annotations?.reactionsSummary?.forEach { reactionSummary ->
            reactions.add(
                Reaction(
                    key = reactionSummary.key,
                    count = reactionSummary.count,
                    isAddedByMe = reactionSummary.addedByMe
                )
            )
        }

        // Attachments
        val attachments = mutableListOf<Attachment>()
        extractAttachment(content)?.let { attachments.add(it) }

        // Redaction & Forwarding
        val isRedacted = root.type == EventType.REDACTION || event.root.unsignedData?.redactedEvent != null
        val redactionReason = event.root.unsignedData?.redactedEvent?.content?.get("reason") as? String
        
        // Telegram-style forwarding check
        val relatesTo = content?.relatesTo
        val isForwarded = relatesTo?.type == "m.forward" || root.content?.get("m.relates_to")?.let { 
            (it as? Map<*, *>)?.get("rel_type") == "m.forward"
        } ?: false

        return MatrixMessage(
            eventId = event.eventId,
            roomId = event.roomId,
            senderId = senderId,
            senderDisplayName = event.senderInfo.disambiguatedDisplayName,
            senderAvatarUrl = event.senderInfo.avatarUrl,
            body = text,
            formattedBody = formattedText,
            messageType = domainMessageType,
            timestamp = Date(root.originServerTs ?: 0L),
            deliveryStatus = status,
            isEdited = event.hasBeenEdited(),
            isRedacted = isRedacted,
            redactionReason = redactionReason,
            isForwarded = isForwarded,
            reactions = reactions,
            replyToEventId = root.getRelationContent()?.eventId,
            attachments = attachments,
            localId = event.localId.toString()
        )
    }

    private fun extractAttachment(content: Any?): Attachment? {
        val messageContent = content as? MessageContent ?: return null
        return when (messageContent) {
            is MessageImageContent -> Attachment(
                url = messageContent.url ?: "",
                mimeType = messageContent.info?.mimeType ?: "image/jpeg",
                size = messageContent.info?.size ?: 0L,
                fileName = messageContent.body,
                width = messageContent.info?.width,
                height = messageContent.info?.height
            )
            is MessageVideoContent -> Attachment(
                url = messageContent.url ?: "",
                mimeType = messageContent.videoInfo?.mimeType ?: "video/mp4",
                size = messageContent.videoInfo?.size ?: 0L,
                fileName = messageContent.body,
                width = messageContent.videoInfo?.width,
                height = messageContent.videoInfo?.height,
                duration = messageContent.videoInfo?.duration?.toLong(),
                thumbnailUrl = messageContent.videoInfo?.thumbnailUrl
            )
            is MessageAudioContent -> Attachment(
                url = messageContent.url ?: "",
                mimeType = messageContent.audioInfo?.mimeType ?: "audio/mpeg",
                size = messageContent.audioInfo?.size ?: 0L,
                fileName = messageContent.body,
                duration = messageContent.audioInfo?.duration?.toLong()
            )
            is MessageFileContent -> Attachment(
                url = messageContent.url ?: "",
                mimeType = messageContent.info?.mimeType ?: "application/octet-stream",
                size = messageContent.info?.size ?: 0L,
                fileName = messageContent.body
            )
            is MessageStickerContent -> Attachment(
                url = messageContent.url ?: "",
                mimeType = messageContent.info?.mimeType ?: "image/png",
                size = messageContent.info?.size ?: 0L,
                fileName = messageContent.body,
                width = messageContent.info?.width,
                height = messageContent.info?.height
            )
            else -> null
        }
    }

}
