package com.matrixmessenger.feature.call.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrixmessenger.feature.call.domain.model.CallState
import com.matrixmessenger.feature.call.domain.model.CallType
import com.matrixmessenger.feature.call.domain.model.LocalMediaState
import com.matrixmessenger.feature.call.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallUiState(
    val callId: String? = null,
    val roomId: String? = null,
    val contactName: String = "Unknown",
    val contactAvatarUrl: String? = null,
    val callType: CallType = CallType.AUDIO,
    val callState: CallState = CallState.Idle,
    val mediaState: LocalMediaState = LocalMediaState(),
    val callDurationSeconds: Int = 0,
    val errorMessage: String? = null
)

sealed interface CallEvent {
    data object StartAudioCall : CallEvent
    data object StartVideoCall : CallEvent
    data object AcceptCall : CallEvent
    data object RejectCall : CallEvent
    data object EndCall : CallEvent
    data object ToggleMicrophone : CallEvent
    data object ToggleCamera : CallEvent
    data object SwitchCamera : CallEvent
    data object ToggleSpeaker : CallEvent
    data class CallStateChanged(val newState: CallState) : CallEvent
}

@HiltViewModel
class CallViewModel @Inject constructor(
    private val startCallUseCase: StartCallUseCase,
    private val answerCallUseCase: AnswerCallUseCase,
    private val rejectCallUseCase: RejectCallUseCase,
    private val endCallUseCase: EndCallUseCase,
    private val toggleMicrophoneUseCase: ToggleMicrophoneUseCase,
    private val toggleCameraUseCase: ToggleCameraUseCase,
    private val switchCameraUseCase: SwitchCameraUseCase,
    private val toggleSpeakerUseCase: ToggleSpeakerUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    fun onEvent(event: CallEvent) {
        when (event) {
            is CallEvent.StartAudioCall -> startCall(isVideo = false)
            is CallEvent.StartVideoCall -> startCall(isVideo = true)
            is CallEvent.AcceptCall -> acceptCall()
            is CallEvent.RejectCall -> rejectCall()
            is CallEvent.EndCall -> endCall()
            is CallEvent.ToggleMicrophone -> toggleMicrophone()
            is CallEvent.ToggleCamera -> toggleCamera()
            is CallEvent.SwitchCamera -> switchCamera()
            is CallEvent.ToggleSpeaker -> toggleSpeaker()
            is CallEvent.CallStateChanged -> updateCallState(event.newState)
        }
    }

    private fun startCall(isVideo: Boolean) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val roomId = currentState.roomId ?: return@launch
            
            // Update state to Dialing
            _uiState.value = currentState.copy(
                callType = if (isVideo) CallType.VIDEO else CallType.AUDIO,
                callState = CallState.Dialing
            )
            
            val result = startCallUseCase(roomId, isVideo)
            result.onSuccess { callId ->
                _uiState.value = _uiState.value.copy(callId = callId)
                // Transition to Connecting after signaling starts
                _uiState.value = _uiState.value.copy(callState = CallState.Connecting)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    callState = CallState.Failed(error.message ?: "Unknown error")
                )
            }
        }
    }

    private fun acceptCall() {
        viewModelScope.launch {
            val callId = _uiState.value.callId ?: return@launch
            
            _uiState.value = _uiState.value.copy(callState = CallState.Connecting)
            
            val result = answerCallUseCase(callId)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(callState = CallState.Connected)
                startCallTimer()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    callState = CallState.Failed(error.message ?: "Failed to answer")
                )
            }
        }
    }

    private fun rejectCall() {
        viewModelScope.launch {
            val callId = _uiState.value.callId ?: return@launch
            
            val result = rejectCallUseCase(callId)
            result.onSuccess {
                _uiState.value = CallUiState()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message
                )
            }
        }
    }

    private fun endCall() {
        viewModelScope.launch {
            val callId = _uiState.value.callId ?: return@launch
            
            val result = endCallUseCase(callId)
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    callState = CallState.Ended,
                    callId = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message
                )
            }
        }
    }

    private fun toggleMicrophone() {
        viewModelScope.launch {
            val callId = _uiState.value.callId ?: return@launch
            val currentState = _uiState.value
            val newMuteState = !currentState.mediaState.isMicrophoneMuted
            
            val result = toggleMicrophoneUseCase(callId, newMuteState)
            result.onSuccess {
                _uiState.value = currentState.copy(
                    mediaState = currentState.mediaState.copy(isMicrophoneMuted = newMuteState)
                )
            }
        }
    }

    private fun toggleCamera() {
        viewModelScope.launch {
            val callId = _uiState.value.callId ?: return@launch
            val currentState = _uiState.value
            val newEnableState = !currentState.mediaState.isCameraEnabled
            
            val result = toggleCameraUseCase(callId, newEnableState)
            result.onSuccess {
                _uiState.value = currentState.copy(
                    mediaState = currentState.mediaState.copy(isCameraEnabled = newEnableState)
                )
            }
        }
    }

    private fun switchCamera() {
        viewModelScope.launch {
            val callId = _uiState.value.callId ?: return@launch
            val currentState = _uiState.value
            val useFront = !currentState.mediaState.isUsingFrontCamera
            
            val result = switchCameraUseCase(callId, useFront)
            result.onSuccess {
                _uiState.value = currentState.copy(
                    mediaState = currentState.mediaState.copy(isUsingFrontCamera = useFront)
                )
            }
        }
    }

    private fun toggleSpeaker() {
        viewModelScope.launch {
            val callId = _uiState.value.callId ?: return@launch
            val currentState = _uiState.value
            val speakerOn = !currentState.mediaState.isSpeakerOn
            
            val result = toggleSpeakerUseCase(callId, speakerOn)
            result.onSuccess {
                _uiState.value = currentState.copy(
                    mediaState = currentState.mediaState.copy(isSpeakerOn = speakerOn)
                )
            }
        }
    }

    private fun updateCallState(newState: CallState) {
        _uiState.value = _uiState.value.copy(callState = newState)
        
        if (newState == CallState.Connected) {
            startCallTimer()
        }
    }

    private var timerJob: kotlinx.coroutines.Job? = null
    
    private fun startCallTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (_uiState.value.callState == CallState.Connected) {
                kotlinx.coroutines.delay(1000)
                seconds++
                _uiState.value = _uiState.value.copy(callDurationSeconds = seconds)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
