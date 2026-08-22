package com.matrixmessenger.data.repository

import android.net.Uri
import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.data.matrix.mapper.MessageMapper
import com.matrixmessenger.domain.model.MatrixMessage
import com.matrixmessenger.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager,
    private val messageMapper: MessageMapper
) : MessageRepository {

    override fun observeMessages(roomId: String): Flow<List<MatrixMessage>> {
        return matrixClientManager.getTimelineEventFlow(roomId).map { events ->
            events.map { messageMapper.mapToMatrixMessage(it, matrixClientManager.getCurrentUserId() ?: "") }
                .sortedByDescending { it.timestamp }
        }
    }

    override suspend fun sendTextMessage(roomId: String, text: String, replyToEventId: String?): Result<Unit> {
        return matrixClientManager.sendTextMessage(roomId, text)
    }

    override suspend fun sendMediaMessage(roomId: String, mediaUri: Uri, mimeType: String, caption: String?): Result<Unit> {
        return when {
            mimeType.startsWith("image/") -> matrixClientManager.sendImage(roomId, mediaUri, caption)
            mimeType.startsWith("video/") -> matrixClientManager.sendVideo(roomId, mediaUri, caption)
            else -> matrixClientManager.sendFile(roomId, mediaUri)
        }
    }

    override suspend fun editMessage(roomId: String, eventId: String, newText: String): Result<Unit> {
        return matrixClientManager.editMessage(roomId, eventId, newText)
    }

    override suspend fun deleteMessage(roomId: String, eventId: String): Result<Unit> {
        return matrixClientManager.deleteMessage(roomId, eventId)
    }

    override suspend fun sendReaction(roomId: String, eventId: String, emoji: String): Result<Unit> {
        return matrixClientManager.sendReaction(roomId, eventId, emoji)
    }
}
