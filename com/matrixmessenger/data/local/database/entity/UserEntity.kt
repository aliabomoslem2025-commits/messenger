package com.matrixmessenger.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val isOnline: Boolean,
    val lastSeen: Long?,
    val presenceStatus: String?
)
