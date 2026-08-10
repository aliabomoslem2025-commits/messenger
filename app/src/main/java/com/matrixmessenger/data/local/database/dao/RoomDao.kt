package com.matrixmessenger.data.local.database.dao

import androidx.room.*
import com.matrixmessenger.data.local.database.entity.RoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoomDao {
    
    @Query("SELECT * FROM rooms ORDER BY lastMessageTimestamp DESC")
    fun getAllRooms(): Flow<List<RoomEntity>>
    
    @Query("SELECT * FROM rooms WHERE roomId = :roomId")
    suspend fun getRoomById(roomId: String): RoomEntity?
    
    @Query("SELECT * FROM rooms WHERE roomId = :roomId")
    fun getRoomByIdFlow(roomId: String): Flow<RoomEntity?>
    
    @Query("SELECT * FROM rooms WHERE isDirect = 1 AND directUserId = :userId LIMIT 1")
    suspend fun getDirectRoomByUserId(userId: String): RoomEntity?
    
    @Query("SELECT * FROM rooms WHERE isPinned = 1 ORDER BY lastMessageTimestamp DESC")
    fun getPinnedRooms(): Flow<List<RoomEntity>>
    
    @Query("SELECT * FROM rooms WHERE unreadCount > 0 ORDER BY lastMessageTimestamp DESC")
    fun getUnreadRooms(): Flow<List<RoomEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(rooms: List<RoomEntity>)
    
    @Update
    suspend fun updateRoom(room: RoomEntity)
    
    @Delete
    suspend fun deleteRoom(room: RoomEntity)
    
    @Query("DELETE FROM rooms WHERE roomId = :roomId")
    suspend fun deleteRoomById(roomId: String)
    
    @Query("UPDATE rooms SET unreadCount = 0 WHERE roomId = :roomId")
    suspend fun clearUnreadCount(roomId: String)
    
    @Query("UPDATE rooms SET draft = :draft WHERE roomId = :roomId")
    suspend fun updateDraft(roomId: String, draft: String?)
    
    @Query("SELECT * FROM rooms WHERE displayName LIKE '%' || :query || '%' OR topic LIKE '%' || :query || '%'")
    fun searchRooms(query: String): Flow<List<RoomEntity>>
}
