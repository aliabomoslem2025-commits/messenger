package com.matrixmessenger.domain.usecase.auth

import com.matrixmessenger.domain.model.MatrixUser
import com.matrixmessenger.domain.repository.AuthRepository

class GetCurrentSessionUseCase(private val authRepository: AuthRepository) {
    suspend operator fun invoke(): MatrixUser? {
        return authRepository.getCurrentSession().getOrNull()
    }
}
