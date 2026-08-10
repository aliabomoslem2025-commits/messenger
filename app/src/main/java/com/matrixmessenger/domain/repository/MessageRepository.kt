package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixMessage
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for message operations
 */
interface MessageRepository {
    /**
     * Observe messages in a room as a Flow
     */
    fun observeMessages(roomId: String, limit: Int = 50): Flow<List<MatrixMessage>>
    
    /**
     * Send a text message
     */
    suspend fun sendTextMessage(
        roomId: String,
        body: String,
        formattedBody: String? = null,
        isEmote: Boolean = false
    ): Result<String>
    
    /**
     * Send an image message
     */
    suspend fun sendImageMessage(
        roomId: String,
        imageUrl: String,
        caption: String? = null,
        mimeType: String = "image/jpeg",
        width: Int? = null,
        height: Int? = null,
        fileSize: Long? = null
    ): Result<String>
    
    /**
     * Send a video message
     */
    suspend fun sendVideoMessage(
        roomId: String,
        videoUrl: String,
        caption: String? = null,
        mimeType: String = "video/mp4",
        width: Int? = null,
        height: Int? = null,
        duration: Long? = null,
        thumbnailUrl: String? = null,
        fileSize: Long? = null
    ): Result<String>
    
    /**
     * Send an audio message
     */
    suspend fun sendAudioMessage(
        roomId: String,
        audioUrl: String,
        caption: String? = null,
        mimeType: String = "audio/mpeg",
        duration: Long? = null,
        fileSize: Long? = null
    ): Result<String>
    
    /**
     * Send a file message
     */
    suspend fun sendFileMessage(
        roomId: String,
        fileUrl: String,
        fileName: String,
        caption: String? = null,
        mimeType: String = "application/octet-stream",
        fileSize: Long? = null
    ): Result<String>
    
    /**
     * Send a location message
     */
    suspend fun sendLocationMessage(
        roomId: String,
        latitude: Double,
        longitude: Double,
        description: String? = null
    ): Result<String>
    
    /**
     * Edit an existing message
     */
    suspend fun editMessage(
        roomId: String,
        originalEventId: String,
        newBody: String,
        newFormattedBody: String? = null
    ): Result<Unit>
    
    /**
     * Delete/redact a message
     */
    suspend fun deleteMessage(roomId: String, eventId: String, reason: String? = null): Result<Unit>
    
    /**
     * Send a reaction to a message
     */
    suspend fun sendReaction(roomId: String, eventId: String, key: String): Result<Unit>
    
    /**
     * Remove a reaction from a message
     */
    suspend fun removeReaction(roomId: String, eventId: String, key: String): Result<Unit>
    
    /**
     * Reply to a message
     */
    suspend fun replyToMessage(
        roomId: String,
        replyToEventId: String,
        body: String,
        formattedBody: String? = null
    ): Result<String>
    
    /**
     * Forward a message to another room
     */
    suspend fun forwardMessage(
        sourceRoomId: String,
        targetRoomId: String,
        eventId: String
    ): Result<Unit>
    
    /**
     * Search messages in a room
     */
    suspend fun searchMessages(roomId: String, query: String, limit: Int = 20): Result<List<MatrixMessage>>
}
