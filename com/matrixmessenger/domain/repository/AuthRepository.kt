package com.matrixmessenger.domain.repository

import com.matrixmessenger.domain.model.MatrixUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun isLoggedIn(): Flow<Boolean>
    suspend fun login(userId: String, password: String, homeserverUrl: String): Result<MatrixUser>
    suspend fun register(username: String, password: String, homeserverUrl: String): Result<MatrixUser>
    suspend fun logout()
    suspend fun getCurrentSession(): MatrixUser?
}
