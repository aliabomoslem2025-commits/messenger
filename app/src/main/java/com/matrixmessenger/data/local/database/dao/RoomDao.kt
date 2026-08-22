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
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoom(room: RoomEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(rooms: List<RoomEntity>)
    
    @Update
    suspend fun updateRoom(room: RoomEntity)
    
    @Delete
    suspend fun deleteRoom(room: RoomEntity)
    
    @Query("DELETE FROM rooms")
    suspend fun deleteAllRooms()
    
    @Query("SELECT * FROM rooms WHERE isPinned = 1 ORDER BY lastMessageTimestamp DESC")
    fun getPinnedRooms(): Flow<List<RoomEntity>>
    
    @Query("SELECT * FROM rooms WHERE unreadCount > 0 ORDER BY lastMessageTimestamp DESC")
    fun getUnreadRooms(): Flow<List<RoomEntity>>
}
