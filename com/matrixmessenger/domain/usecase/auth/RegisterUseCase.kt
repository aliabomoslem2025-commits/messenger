package com.matrixmessenger.domain.usecase.auth

import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.repository.AuthRepository

class RegisterUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String, homeserverUrl: String): Result<MatrixUser> {
        return authRepository.register(username, password, homeserverUrl)
    }
}
