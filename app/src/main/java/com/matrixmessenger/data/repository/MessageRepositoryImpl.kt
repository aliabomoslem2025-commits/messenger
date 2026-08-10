package com.matrixmessenger.data.repository

import com.matrixmessenger.data.local.database.dao.MessageDao
import com.matrixmessenger.data.local.database.entity.MessageEntity
import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.data.matrix.mapper.MessageMapper
import com.matrixmessenger.domain.model.MatrixMessage
import com.matrixmessenger.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager,
    private val messageDao: MessageDao,
    private val messageMapper: MessageMapper
) : MessageRepository {

    private val activeTimelines = mutableMapOf<String, Timeline>()

    override fun getMessagesFlow(roomId: String): Flow<List<MatrixMessage>> {
        val timeline = getOrCreateTimeline(roomId)
        val currentUserId = matrixClientManager.getCurrentUserId() ?: ""

        return timeline.timelineEventFlow()
            .map { events ->
                events.map { event ->
                    messageMapper.mapToMatrixMessage(event, currentUserId)
                }
            }
    }

    private fun getOrCreateTimeline(roomId: String): Timeline {
        return activeTimelines.getOrPut(roomId) {
            val timeline = matrixClientManager.createTimeline(roomId)
                ?: throw IllegalStateException("Cannot create timeline for room $roomId")
            timeline.start()
            timeline
        }
    }

    override suspend fun loadMoreMessages(roomId: String): Result<Boolean> {
        return runCatching {
            val timeline = activeTimelines[roomId] ?: return@runCatching false
            timeline.paginate(
                direction = org.matrix.android.sdk.api.session.room.timeline.Timeline.Direction.BACKWARDS,
                count = 30
            )
            true
        }
    }

    override suspend fun sendTextMessage(
        roomId: String,
        text: String
    ): Result<Unit> {
        return matrixClientManager.sendTextMessage(roomId, text)
    }

    override suspend fun sendReply(
        roomId: String,
        replyText: String,
        originalEventId: String
    ): Result<Unit> {
        return runCatching {
            val room = matrixClientManager.getRoom(roomId)
                ?: throw IllegalStateException("Room not found")
            val timeline = getOrCreateTimeline(roomId)
            
            // Find the original event in the timeline
            val events = timeline.getTimelineEvents()
            val originalEvent = events.find { it.eventId == originalEventId }
                ?: throw IllegalStateException("Original event not found")
            
            matrixClientManager.sendReply(roomId, replyText, originalEvent)
        }
    }

    override suspend fun sendImage(
        roomId: String,
        uri: android.net.Uri
    ): Result<Unit> {
        return matrixClientManager.sendImage(roomId, uri)
    }

    override suspend fun sendVideo(
        roomId: String,
        uri: android.net.Uri
    ): Result<Unit> {
        return matrixClientManager.sendVideo(roomId, uri)
    }

    override suspend fun sendVoiceMessage(
        roomId: String,
        uri: android.net.Uri,
        durationMs: Long,
        waveform: List<Int>
    ): Result<Unit> {
        return matrixClientManager.sendVoiceMessage(roomId, uri, durationMs, waveform)
    }

    override suspend fun sendFile(roomId: String, uri: android.net.Uri): Result<Unit> {
        return matrixClientManager.sendFile(roomId, uri)
    }

    override suspend fun editMessage(
        roomId: String,
        eventId: String,
        newText: String
    ): Result<Unit> {
        return matrixClientManager.editMessage(roomId, eventId, newText)
    }

    override suspend fun deleteMessage(
        roomId: String,
        eventId: String,
        reason: String?
    ): Result<Unit> {
        return matrixClientManager.deleteMessage(roomId, eventId, reason)
    }

    override suspend fun sendReaction(
        roomId: String,
        eventId: String,
        emoji: String
    ): Result<Unit> {
        return matrixClientManager.sendReaction(roomId, eventId, emoji)
    }

    override suspend fun removeReaction(
        roomId: String,
        eventId: String,
        emoji: String
    ): Result<Unit> {
        return matrixClientManager.removeReaction(roomId, eventId, emoji)
    }

    override suspend fun sendTyping(roomId: String, isTyping: Boolean): Result<Unit> {
        return matrixClientManager.sendTyping(roomId, isTyping)
    }

    override suspend fun markAsRead(
        roomId: String,
        eventId: String
    ): Result<Unit> {
        return matrixClientManager.markAsRead(roomId)
    }

    override suspend fun pinMessage(roomId: String, eventId: String): Result<Unit> {
        return matrixClientManager.pinMessage(roomId, eventId)
    }

    override suspend fun saveDraft(roomId: String, text: String): Result<Unit> {
        return matrixClientManager.saveDraft(roomId, text)
    }

    override suspend fun getDraft(roomId: String): String? {
        return matrixClientManager.getDraft(roomId)
    }

    override fun clearTimeline(roomId: String) {
        activeTimelines.remove(roomId)?.destroy()
    }
}
