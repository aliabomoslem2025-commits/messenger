package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixMessage
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessages(roomId: String): Flow<List<MatrixMessage>>
    suspend fun sendTextMessage(roomId: String, text: String, replyToEventId: String?): Result<String>
    suspend fun sendMediaMessage(roomId: String, mediaUri: String, mimeType: String, caption: String?): Result<String>
    suspend fun sendVoiceMessage(roomId: String, audioUri: String, durationMs: Long, waveform: List<Int>): Result<String>
    suspend fun sendVideoNote(roomId: String, videoUri: String, durationMs: Long, thumbnailUri: String?): Result<String>
    suspend fun editMessage(roomId: String, eventId: String, newText: String): Result<Unit>
    suspend fun deleteMessage(roomId: String, eventId: String): Result<Unit>
    suspend fun sendReaction(roomId: String, eventId: String, emoji: String): Result<Unit>
    suspend fun redactReaction(roomId: String, eventId: String, emoji: String): Result<Unit>
    suspend fun forwardMessage(roomId: String, eventId: String, targetRoomIds: List<String>): Result<Unit>
    suspend fun reportMessage(roomId: String, eventId: String, reason: String): Result<Unit>
}
