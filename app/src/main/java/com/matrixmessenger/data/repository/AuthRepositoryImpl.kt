package com.matrixmessenger.data.repository

import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager
) : AuthRepository {

    override suspend fun login(userId: String, password: String, homeserverUrl: String): Result<MatrixUser> {
        return matrixClientManager.loginWithPassword(homeserverUrl, userId, password).map { session ->
            MatrixUser(
                userId = session.myUserId,
                displayName = null, // Will be fetched later
                avatarUrl = null
            )
        }
    }

    override suspend fun register(username: String, password: String, homeserverUrl: String): Result<MatrixUser> {
        return matrixClientManager.register(homeserverUrl, username, password).map { session ->
            MatrixUser(
                userId = session.myUserId,
                displayName = null,
                avatarUrl = null
            )
        }
    }

    override suspend fun logout(): Result<Unit> {
        return matrixClientManager.logout()
    }

    override suspend fun getCurrentSession(): Result<MatrixUser?> {
        val session = matrixClientManager.getCurrentSession()
        return Result.success(session?.let {
            MatrixUser(
                userId = it.myUserId,
                displayName = null,
                avatarUrl = null
            )
        })
    }

    override fun observeSessionState(): Flow<Boolean> {
        return matrixClientManager.sessionState.map { it is MatrixClientManager.SessionState.Active }
    }
}
