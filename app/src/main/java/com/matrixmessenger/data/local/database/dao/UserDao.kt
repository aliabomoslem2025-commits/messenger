package com.matrixmessenger.data.local.database.dao

import androidx.room.*
import com.matrixmessenger.data.local.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserById(userId: String): Flow<UserEntity?>
    
    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserByIdOnce(userId: String): UserEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)
    
    @Update
    suspend fun updateUser(user: UserEntity)
    
    @Delete
    suspend fun deleteUser(user: UserEntity)
    
    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
