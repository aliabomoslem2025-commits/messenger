package com.matrixmessenger.domain.usecase.auth

import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.repository.AuthRepository

class LoginUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(userId: String, password: String, homeserverUrl: String): Result<MatrixUser> {
        return authRepository.login(userId, password, homeserverUrl)
    }
}
