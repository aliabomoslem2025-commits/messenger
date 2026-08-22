package com.matrixmessenger.domain.usecase.auth

import com.matrixmessenger.domain.repository.AuthRepository

class LogoutUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke() {
        authRepository.logout()
    }
}
