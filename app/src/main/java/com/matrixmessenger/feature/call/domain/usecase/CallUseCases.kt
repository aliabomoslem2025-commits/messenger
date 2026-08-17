package com.matrixmessenger.feature.call.domain.usecase

import com.matrixmessenger.feature.call.domain.repository.CallRepository
import javax.inject.Inject

class AnswerCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String): Result<Unit> {
        return callRepository.answerCall(callId)
    }
}

class RejectCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String): Result<Unit> {
        return callRepository.rejectCall(callId)
    }
}

class EndCallUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String): Result<Unit> {
        return callRepository.endCall(callId)
    }
}

class ToggleMicrophoneUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String, mute: Boolean): Result<Unit> {
        return callRepository.toggleMicrophone(callId, mute)
    }
}

class ToggleCameraUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String, enable: Boolean): Result<Unit> {
        return callRepository.toggleCamera(callId, enable)
    }
}

class SwitchCameraUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String, useFront: Boolean): Result<Unit> {
        return callRepository.switchCamera(callId, useFront)
    }
}

class ToggleSpeakerUseCase @Inject constructor(
    private val callRepository: CallRepository
) {
    suspend operator fun invoke(callId: String, speakerOn: Boolean): Result<Unit> {
        return callRepository.toggleSpeaker(callId, speakerOn)
    }
}
