package com.matrixmessenger.data.repository

import com.matrixmessenger.data.local.preferences.SessionPreferences
import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

class AuthRepositoryImpl(
    private val matrixClientManager: MatrixClientManager,
    private val sessionPreferences: SessionPreferences
) : AuthRepository {
    
    override fun isLoggedIn(): Flow<Boolean> {
        return sessionPreferences.isLoggedInFlow
    }
    
    override suspend fun login(userId: String, password: String, homeserverUrl: String): Result<MatrixUser> {
        return try {
            // Initialize with homeserver
            val initResult = matrixClientManager.initialize(homeserverUrl)
            if (initResult.isFailure) {
                return Result.failure(initResult.exceptionOrNull()!!)
            }
            
            // Perform login
            val loginResult = matrixClientManager.login(userId, password)
            
            loginResult.fold(
                onSuccess = { session ->
                    val matrixUser = MatrixUser(
                        userId = session.myUserId,
                        displayName = session.getUserInfo(session.myUserId)?.displayName,
                        avatarUrl = session.getUserInfo(session.myUserId)?.avatarUrl,
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        presenceStatus = "online"
                    )
                    Result.success(matrixUser)
                },
                onFailure = { error ->
                    Timber.e(error, "Login failed")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Login exception")
            Result.failure(e)
        }
    }
    
    override suspend fun register(username: String, password: String, homeserverUrl: String): Result<MatrixUser> {
        return try {
            val initResult = matrixClientManager.initialize(homeserverUrl)
            if (initResult.isFailure) {
                return Result.failure(initResult.exceptionOrNull()!!)
            }
            
            val registerResult = matrixClientManager.register(username, password)
            
            registerResult.fold(
                onSuccess = { registrationResult ->
                    // Auto-login after registration
                    val loginResult = matrixClientManager.login(username, password)
                    loginResult.fold(
                        onSuccess = { session ->
                            val matrixUser = MatrixUser(
                                userId = session.myUserId,
                                displayName = username,
                                avatarUrl = null,
                                isOnline = true,
                                lastSeen = System.currentTimeMillis(),
                                presenceStatus = "online"
                            )
                            Result.success(matrixUser)
                        },
                        onFailure = { error ->
                            Result.failure(error)
                        }
                    )
                },
                onFailure = { error ->
                    Timber.e(error, "Registration failed")
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Registration exception")
            Result.failure(e)
        }
    }
    
    override suspend fun logout() {
        matrixClientManager.logout()
    }
    
    override suspend fun getCurrentSession(): MatrixUser? {
        val session = matrixClientManager.getCurrentSession()
        return session?.let {
            MatrixUser(
                userId = it.myUserId,
                displayName = it.getUserInfo(it.myUserId)?.displayName,
                avatarUrl = it.getUserInfo(it.myUserId)?.avatarUrl,
                isOnline = true,
                lastSeen = System.currentTimeMillis(),
                presenceStatus = "online"
            )
        }
    }
}
