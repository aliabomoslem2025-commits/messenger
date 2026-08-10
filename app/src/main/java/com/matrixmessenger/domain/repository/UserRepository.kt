package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixUser
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for user operations
 */
interface UserRepository {
    /**
     * Get current user's profile
     */
    suspend fun getCurrentUserProfile(): Result<MatrixUser>
    
    /**
     * Get user profile by ID
     */
    suspend fun getUserProfile(userId: String): Result<MatrixUser>
    
    /**
     * Update display name
     */
    suspend fun updateDisplayName(displayName: String): Result<Unit>
    
    /**
     * Update avatar URL
     */
    suspend fun updateAvatar(avatarUrl: String): Result<Unit>
    
    /**
     * Remove avatar
     */
    suspend fun removeAvatar(): Result<Unit>
    
    /**
     * Set presence status
     */
    suspend fun setPresence(presence: String, statusMessage: String? = null): Result<Unit>
    
    /**
     * Observe user presence
     */
    fun observePresence(userId: String): Flow<String>
    
    /**
     * Search users on homeserver
     */
    suspend fun searchUsers(query: String, limit: Int = 20): Result<List<MatrixUser>>
    
    /**
     * Get room members
     */
    suspend fun getRoomMembers(roomId: String): Result<List<MatrixUser>>
    
    /**
     * Ignore a user
     */
    suspend fun ignoreUser(userId: String): Result<Unit>
    
    /**
     * Unignore a user
     */
    suspend fun unignoreUser(userId: String): Result<Unit>
    
    /**
     * Get ignored users list
     */
    suspend fun getIgnoredUsers(): Result<List<String>>
}
