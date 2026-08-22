package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.model.PresenceState
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getCurrentUserProfile(): Result<MatrixUser>
    suspend fun getUserProfile(userId: String): Result<MatrixUser>
    suspend fun updateDisplayName(displayName: String): Result<Unit>
    suspend fun updateAvatar(avatarUrl: String): Result<Unit>
    suspend fun removeAvatar(): Result<Unit>
    suspend fun setPresence(presence: PresenceState, statusMessage: String?): Result<Unit>
    fun observePresence(userId: String): Flow<PresenceState>
    suspend fun searchUsers(query: String, limit: Int): Result<List<MatrixUser>>
    suspend fun getRoomMembers(roomId: String): Result<List<MatrixUser>>
    suspend fun ignoreUser(userId: String): Result<Unit>
    suspend fun unignoreUser(userId: String): Result<Unit>
    suspend fun getIgnoredUsers(): Result<List<String>>
}
