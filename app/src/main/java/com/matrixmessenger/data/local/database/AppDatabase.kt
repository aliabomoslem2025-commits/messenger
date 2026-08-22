package com.matrixmessenger.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.matrixmessenger.data.local.database.dao.MessageDao
import com.matrixmessenger.data.local.database.dao.RoomDao
import com.matrixmessenger.data.local.database.dao.UserDao
import com.matrixmessenger.data.local.database.entity.MessageEntity
import com.matrixmessenger.data.local.database.entity.RoomEntity
import com.matrixmessenger.data.local.database.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        RoomEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun roomDao(): RoomDao
    abstract fun messageDao(): MessageDao
}
