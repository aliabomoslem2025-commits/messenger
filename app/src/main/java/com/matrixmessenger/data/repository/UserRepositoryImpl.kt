package com.matrixmessenger.data.repository

import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.model.PresenceState
import com.matrixmessenger.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager
) : UserRepository {
    override suspend fun getCurrentUserProfile(): Result<MatrixUser> = matrixClientManager.getUserProfile(matrixClientManager.getCurrentUserId() ?: "")
    override suspend fun getUserProfile(userId: String): Result<MatrixUser> = matrixClientManager.getUserProfile(userId)
    override suspend fun updateDisplayName(displayName: String): Result<Unit> = matrixClientManager.updateDisplayName(displayName)
    override suspend fun updateAvatar(avatarUrl: String): Result<Unit> = Result.failure(Exception("Not implemented - needs Uri"))
    override suspend fun removeAvatar(): Result<Unit> = Result.failure(Exception("Not implemented"))
    override suspend fun setPresence(presence: PresenceState, statusMessage: String?): Result<Unit> = Result.failure(Exception("Not implemented"))
    override fun observePresence(userId: String): Flow<PresenceState> = matrixClientManager.observePresence(userId)
    override suspend fun searchUsers(query: String, limit: Int): Result<List<MatrixUser>> = matrixClientManager.searchUsers(query)
    override suspend fun getRoomMembers(roomId: String): Result<List<MatrixUser>> = Result.failure(Exception("Not implemented"))
    override suspend fun ignoreUser(userId: String): Result<Unit> = Result.failure(Exception("Not implemented"))
    override suspend fun unignoreUser(userId: String): Result<Unit> = Result.failure(Exception("Not implemented"))
    override suspend fun getIgnoredUsers(): Result<List<String>> = Result.failure(Exception("Not implemented"))
}
