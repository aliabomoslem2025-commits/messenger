package com.matrixmessenger.domain.repository

import android.net.Uri
import com.matrixmessenger.domain.model.MatrixMessage
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun observeMessages(roomId: String): Flow<List<MatrixMessage>>
    suspend fun sendTextMessage(roomId: String, text: String, replyToEventId: String? = null): Result<Unit>
    suspend fun sendMediaMessage(roomId: String, mediaUri: Uri, mimeType: String, caption: String? = null): Result<Unit>
    suspend fun editMessage(roomId: String, eventId: String, newText: String): Result<Unit>
    suspend fun deleteMessage(roomId: String, eventId: String): Result<Unit>
    suspend fun sendReaction(roomId: String, eventId: String, emoji: String): Result<Unit>
}
