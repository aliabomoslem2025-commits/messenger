package com.matrixmessenger.feature.call.domain.usecase

import com.matrixmessenger.feature.call.domain.repository.CallRepository
import javax.inject.Inject

class StartCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(roomId: String, isVideoCall: Boolean): Result<String> {
        return callRepository.startCall(roomId, isVideoCall)
    }
}
