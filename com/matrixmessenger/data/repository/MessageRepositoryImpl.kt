package com.matrixmessenger.data.repository

import com.matrixmessenger.data.local.database.dao.MessageDao
import com.matrixmessenger.data.local.database.entity.MessageEntity
import com.matrixmessenger.domain.model.MessageType
import com.matrixmessenger.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.room.message.*
import timber.log.Timber

class MessageRepositoryImpl(
    private val sessionProvider: () -> Session?,
    private val messageDao: MessageDao
) : MessageRepository {
    
    override fun getMessages(roomId: String): Flow<List<com.matrixmessenger.domain.model.MatrixMessage>> {
        return messageDao.getMessagesByRoom(roomId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun sendTextMessage(roomId: String, text: String, replyToEventId: String?): Result<String> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            val content = MessageContentFactory.createTextContent(text)
            
            val eventId = if (replyToEventId != null) {
                // Send as reply
                room.sendReply(replyToEventId, text)
            } else {
                room.sendTextMessage(text)
            }
            
            Result.success(eventId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send text message")
            Result.failure(e)
        }
    }
    
    override suspend fun sendMediaMessage(roomId: String, mediaUri: String, mimeType: String, caption: String?): Result<String> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            val file = java.io.File(mediaUri)
            
            val eventId = when {
                mimeType.startsWith("image/") -> {
                    room.sendImage(file, mimeType, caption)
                }
                mimeType.startsWith("video/") -> {
                    room.sendVideo(file, mimeType, caption)
                }
                mimeType.startsWith("audio/") -> {
                    room.sendAudio(file, mimeType, caption)
                }
                else -> {
                    room.sendFile(file, mimeType, caption)
                }
            }
            
            Result.success(eventId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send media message")
            Result.failure(e)
        }
    }
    
    override suspend fun sendVoiceMessage(roomId: String, audioUri: String, durationMs: Long, waveform: List<Int>): Result<String> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            val file = java.io.File(audioUri)
            val eventId = room.sendVoiceMessage(file, "audio/ogg", durationMs, waveform)
            
            Result.success(eventId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send voice message")
            Result.failure(e)
        }
    }
    
    override suspend fun sendVideoNote(roomId: String, videoUri: String, durationMs: Long, thumbnailUri: String?): Result<String> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            val file = java.io.File(videoUri)
            val thumbnailFile = thumbnailUri?.let { java.io.File(it) }
            
            val eventId = room.sendVideoNote(file, "video/mp4", durationMs, thumbnailFile)
            
            Result.success(eventId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send video note")
            Result.failure(e)
        }
    }
    
    override suspend fun editMessage(roomId: String, eventId: String, newText: String): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            room.editTextMessage(eventId, newText)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to edit message")
            Result.failure(e)
        }
    }
    
    override suspend fun deleteMessage(roomId: String, eventId: String): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            room.redactEvent(eventId, "Message deleted by user")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete message")
            Result.failure(e)
        }
    }
    
    override suspend fun sendReaction(roomId: String, eventId: String, emoji: String): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            room.sendReaction(eventId, emoji)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send reaction")
            Result.failure(e)
        }
    }
    
    override suspend fun redactReaction(roomId: String, eventId: String, emoji: String): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            room.redactReaction(eventId, emoji)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to redact reaction")
            Result.failure(e)
        }
    }
    
    override suspend fun forwardMessage(roomId: String, eventId: String, targetRoomIds: List<String>): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            targetRoomIds.forEach { targetRoomId ->
                val targetRoom = session.getRoom(targetRoomId)
                targetRoom?.forwardEvent(eventId)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to forward message")
            Result.failure(e)
        }
    }
    
    override suspend fun reportMessage(roomId: String, eventId: String, reason: String): Result<Unit> {
        return try {
            val session = sessionProvider()
                ?: return Result.failure(IllegalStateException("No active session"))
            
            val room = session.getRoom(roomId)
                ?: return Result.failure(IllegalStateException("Room not found"))
            
            room.reportContent(eventId, reason)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to report message")
            Result.failure(e)
        }
    }
}

// Extension function to convert MessageEntity to domain model
private fun MessageEntity.toDomainModel(): com.matrixmessenger.domain.model.MatrixMessage {
    return com.matrixmessenger.domain.model.MatrixMessage(
        eventId = eventId,
        roomId = roomId,
        senderId = senderId,
        senderDisplayName = senderDisplayName,
        senderAvatarUrl = senderAvatarUrl,
        timestamp = timestamp,
        type = MessageType.valueOf(type),
        text = text,
        formattedText = formattedText,
        status = com.matrixmessenger.domain.model.MessageStatus.valueOf(status),
        isOwn = isOwn,
        isEdited = isEdited,
        isForwarded = isForwarded,
        forwardedFromName = forwardedFromName,
        replyToEventId = replyToEventId,
        replyToMessage = replyToEventId?.let {
            com.matrixmessenger.domain.model.ReplyData(
                eventId = it,
                senderId = replyToSenderId ?: "",
                senderName = replyToSenderName ?: "",
                text = replyToText,
                mediaUrl = null,
                mediaType = null
            )
        },
        mediaUrl = mediaUrl,
        mediaLocalPath = mediaLocalPath,
        mediaMimeType = mediaMimeType,
        mediaSize = mediaSize,
        mediaWidth = mediaWidth,
        mediaHeight = mediaHeight,
        mediaDurationMs = mediaDurationMs,
        voiceWaveform = voiceWaveform?.let { org.json.JSONArray(it).toList().map { i -> i as Int } },
        thumbnailUrl = thumbnailUrl,
        fileName = fileName,
        reactions = emptyMap(),
        latitude = latitude,
        longitude = longitude,
        locationDescription = locationDescription,
        pollData = null,
        isRedacted = isRedacted,
        redactionReason = redactionReason,
        selfDestructAfterMs = null
    )
}
