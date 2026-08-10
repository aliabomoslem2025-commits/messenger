package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixRoom
import com.matrixmessenger.domain.model.MatrixMessage
import com.matrixmessenger.domain.model.MatrixUser
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for authentication operations
 */
interface AuthRepository {
    /**
     * Login to Matrix server
     */
    suspend fun login(username: String, password: String, homeserverUrl: String): Result<String>
    
    /**
     * Register new account on Matrix server
     */
    suspend fun register(
        username: String, 
        password: String, 
        homeserverUrl: String,
        email: String? = null
    ): Result<String>
    
    /**
     * Logout and clear session
     */
    suspend fun logout(): Result<Unit>
    
    /**
     * Get current session user ID if logged in
     */
    suspend fun getCurrentSessionUserId(): String?
    
    /**
     * Check if user is authenticated
     */
    suspend fun isAuthenticated(): Boolean
    
    /**
     * Get active session
     */
    suspend fun getActiveSession(): Any?
    
    /**
     * Observe authentication state changes
     */
    fun observeAuthState(): Flow<Boolean>
}
