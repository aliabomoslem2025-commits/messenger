package com.matrixmessenger.data.local.database.dao

import androidx.room.*
import com.matrixmessenger.data.local.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesByRoomId(roomId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(roomId: String, limit: Int = 50): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE eventId = :eventId")
    suspend fun getMessageById(eventId: String): MessageEntity?
    
    @Query("SELECT * FROM messages WHERE roomId = :roomId AND isOwn = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastOwnMessage(roomId: String): MessageEntity?
    
    @Query("SELECT * FROM messages WHERE roomId = :roomId AND type = 'IMAGE' OR type = 'VIDEO' ORDER BY timestamp DESC")
    fun getMediaMessages(roomId: String): Flow<List<MessageEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE eventId = :eventId")
    suspend fun deleteMessageById(eventId: String)
    
    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteAllMessagesInRoom(roomId: String)
    
    @Query("UPDATE messages SET status = :status WHERE eventId = :eventId")
    suspend fun updateMessageStatus(eventId: String, status: String)
    
    @Query("UPDATE messages SET reactions = :reactions WHERE eventId = :eventId")
    suspend fun updateMessageReactions(eventId: String, reactions: String)
    
    @Query("SELECT * FROM messages WHERE text LIKE '%' || :query || '%' AND roomId = :roomId ORDER BY timestamp DESC")
    fun searchMessagesInRoom(query: String, roomId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE text LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    fun searchMessages(query: String): Flow<List<MessageEntity>>
}
