package com.matrixmessenger.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.matrixmessenger.data.local.database.dao.MessageDao
import com.matrixmessenger.data.local.database.dao.RoomDao
import com.matrixmessenger.data.local.database.dao.UserDao
import com.matrixmessenger.data.local.database.entity.MessageEntity
import com.matrixmessenger.data.local.database.entity.RoomEntity
import com.matrixmessenger.data.local.database.entity.UserEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Database(
    entities = [
        RoomEntity::class,
        MessageEntity::class,
        UserEntity::class
    ],
    version = 1,
    exportSchema = false
)
@Singleton
abstract class AppDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) : RoomDatabase() {
    
    abstract fun roomDao(): RoomDao
    abstract fun messageDao(): MessageDao
    abstract fun userDao(): UserDao
    
    companion object {
        private const val DATABASE_NAME = "matrix_messenger_db"
        
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
