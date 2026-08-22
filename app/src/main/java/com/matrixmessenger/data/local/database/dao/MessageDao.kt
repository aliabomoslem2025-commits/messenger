package com.matrixmessenger.data.local.database.dao

import androidx.room.*
import com.matrixmessenger.data.local.database.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE roomId = :roomId ORDER BY timestamp DESC")
    fun getMessagesByRoom(roomId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE eventId = :eventId")
    suspend fun getMessageById(eventId: String): MessageEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE roomId = :roomId")
    suspend fun deleteAllMessagesForRoom(roomId: String)
    
    @Query("SELECT * FROM messages WHERE status = 'SENDING' OR status = 'FAILED'")
    fun getPendingMessages(): Flow<List<MessageEntity>>
}
