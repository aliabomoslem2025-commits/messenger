package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixUser
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    fun getUser(userId: String): Flow<MatrixUser?>
    suspend fun getUserProfile(userId: String): Result<MatrixUser>
    suspend fun updateDisplayName(displayName: String): Result<Unit>
    suspend fun updateAvatar(avatarUri: String): Result<String>
    suspend fun removeAvatar(): Result<Unit>
    suspend fun setPresence(isOnline: Boolean, statusMsg: String?): Result<Unit>
}
