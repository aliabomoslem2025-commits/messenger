package com.matrixmessenger.feature.chat.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrixmessenger.core.designsystem.components.ComposerState
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.domain.model.VideoNoteMediaState
import com.matrixmessenger.domain.repository.MatrixRepository
import com.matrixmessenger.feature.voice.data.recorder.AudioRecorder
import com.matrixmessenger.feature.chat.presentation.VideoNoteController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject
import timber.log.Timber

data class ChatUiState(
    val roomId: String = "",
    val title: String? = null,
    val status: String? = null,
    val avatarUrl: String? = null,
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val isPaginating: Boolean = false,
    val error: String? = null,
    val composerState: ComposerState = ComposerState.Idle,
    val currentUserId: String? = null,
    val selectedMessageForActions: Message? = null,
    val videoNoteMediaStates: Map<String, VideoNoteMediaState> = emptyMap()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
    private val audioRecorder: AudioRecorder,
    val videoNoteController: VideoNoteController,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val roomId: String = savedStateHandle["roomId"] ?: ""
    private var recordingJob: Job? = null

    private val _uiState = MutableStateFlow(ChatUiState(roomId = roomId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        observeRoom()
        observeMessages()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val userId = matrixRepository.getCurrentUserId()
            _uiState.value = _uiState.value.copy(currentUserId = userId)
        }
    }

    private fun observeRoom() {
        viewModelScope.launch {
            matrixRepository.getRoom(roomId).onSuccess { room ->
                _uiState.value = _uiState.value.copy(
                    title = room.getDisplayName(),
                    status = if (room.isDirect) "online" else "${room.memberCount} members",
                    avatarUrl = room.avatarUrl
                )
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun observeMessages() {
        viewModelScope.launch {
            savedStateHandle.getStateFlow("roomId", roomId)
                .flatMapLatest { id ->
                    if (id.isBlank()) {
                        flowOf(emptyList<Message>())
                    } else {
                        _uiState.value = _uiState.value.copy(messages = emptyList(), isLoading = true)
                        matrixRepository.observeMessages(id)
                    }
                }
                .collect { messages ->
                    val currentRoomId = savedStateHandle.get<String>("roomId") ?: roomId
                    val filteredMessages = messages.filter { it.roomId == currentRoomId }
                    _uiState.value = _uiState.value.copy(
                        messages = filteredMessages,
                        isLoading = false
                    )
                }
        }
    }

    fun markAsRead() {
        viewModelScope.launch {
            matrixRepository.markRoomAsRead(roomId)
        }
    }

    fun loadMore() {
        if (_uiState.value.isPaginating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPaginating = true)
            matrixRepository.loadMoreMessages(roomId)
            _uiState.value = _uiState.value.copy(isPaginating = false)
        }
    }

    fun updateMessageInput(input: String) {
        val currentState = _uiState.value.composerState
        _uiState.value = _uiState.value.copy(
            composerState = when (currentState) {
                is ComposerState.Replying -> currentState.copy(text = input)
                else -> if (input.isEmpty()) ComposerState.Idle else ComposerState.Typing(input)
            }
        )
    }

    fun sendMessage() {
        viewModelScope.launch {
            val state = _uiState.value
            val currentText = when (state.composerState) {
                is ComposerState.Typing -> state.composerState.text
                is ComposerState.Replying -> state.composerState.text
                else -> null
            }
            if (currentText.isNullOrBlank()) return@launch
            executeSendMessage(currentText, (state.composerState as? ComposerState.Replying)?.replyToEventId)
        }
    }

    private suspend fun executeSendMessage(text: String, replyToEventId: String?) {
        _uiState.value = _uiState.value.copy(composerState = ComposerState.Sending)
        matrixRepository.sendMessage(roomId = roomId, body = text, replyToEventId = replyToEventId)
            .fold(
                onSuccess = { _uiState.value = _uiState.value.copy(composerState = ComposerState.Idle) },
                onFailure = { error -> _uiState.value = _uiState.value.copy(composerState = ComposerState.Failed(error.message ?: "Failed")) }
            )
    }

    fun setReplyingTo(message: Message?) {
        val currentText = when (val s = _uiState.value.composerState) {
            is ComposerState.Typing -> s.text
            is ComposerState.Replying -> s.text
            else -> ""
        }
        _uiState.value = _uiState.value.copy(
            composerState = if (message == null) {
                if (currentText.isEmpty()) ComposerState.Idle else ComposerState.Typing(currentText)
            } else {
                ComposerState.Replying(currentText, message.eventId)
            }
        )
    }

    fun toggleReaction(eventId: String, key: String) {
        viewModelScope.launch {
            val message = _uiState.value.messages.find { it.eventId == eventId } ?: return@launch
            val reaction = message.reactions.find { it.key == key }
            if (reaction?.isAddedByMe == true) {
                matrixRepository.removeReaction(roomId, eventId, key)
            } else {
                matrixRepository.sendReaction(roomId, eventId, key)
            }
        }
    }

    fun retryMessage(message: Message) {
        viewModelScope.launch {
            val localId = message.localId ?: return@launch
            matrixRepository.resendMessage(roomId, localId)
            _uiState.value = _uiState.value.copy(selectedMessageForActions = null)
        }
    }

    fun cancelMessage(message: Message) {
        viewModelScope.launch {
            val localId = message.localId ?: return@launch
            matrixRepository.cancelSend(roomId, localId)
            _uiState.value = _uiState.value.copy(selectedMessageForActions = null)
        }
    }

    fun selectMessage(message: Message?) {
        _uiState.value = _uiState.value.copy(selectedMessageForActions = message)
    }

    fun startRecording(cacheDir: File) {
        viewModelScope.launch {
            val file = File(cacheDir, "voice_${UUID.randomUUID()}.m4a")
            audioRecorder.prepare(file)
                .onSuccess {
                    audioRecorder.start().onSuccess {
                        _uiState.value = _uiState.value.copy(composerState = ComposerState.RecordingVoice(0, 0f))
                        startRecordingTicker()
                    }.onFailure {
                        Timber.e(it, "Failed to start recording")
                    }
                }
                .onFailure {
                    Timber.e(it, "Failed to prepare recorder")
                }
        }
    }

    fun stopAndSendRecording() {
        viewModelScope.launch {
            val currentState = _uiState.value.composerState
            val durationMs = if (currentState is ComposerState.RecordingVoice) currentState.durationMs else 0L

            val path = audioRecorder.stop() ?: return@launch
            val file = File(path)
            if (file.exists()) {
                _uiState.value = _uiState.value.copy(composerState = ComposerState.Sending)
                matrixRepository.sendAudioMessage(roomId, file, durationMs, null).onSuccess {
                    _uiState.value = _uiState.value.copy(composerState = ComposerState.Idle)
                }.onFailure {
                    _uiState.value = _uiState.value.copy(composerState = ComposerState.Idle)
                }
            }
        }
    }

    fun cancelRecording() {
        if (_uiState.value.composerState is ComposerState.RecordingVoice) {
            audioRecorder.cancel()
        }
        _uiState.value = _uiState.value.copy(composerState = ComposerState.Idle)
    }

    private fun startRecordingTicker() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            while (true) {
                val currentState = _uiState.value.composerState
                if (currentState is ComposerState.RecordingVoice) {
                    _uiState.value = _uiState.value.copy(
                        composerState = ComposerState.RecordingVoice(
                            audioRecorder.getDurationMillis(),
                            audioRecorder.getNormalizedAmplitude()
                        )
                    )
                } else {
                    break
                }
                delay(100)
            }
        }
    }

    fun downloadVideoNote(message: Message) {
        val eventId = message.eventId
        if (_uiState.value.videoNoteMediaStates[eventId] is VideoNoteMediaState.Downloading) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(videoNoteMediaStates = it.videoNoteMediaStates + (eventId to VideoNoteMediaState.Requested)) }
            
            // Integrate with Matrix media loader
            // For now, simulating the process
            _uiState.update { it.copy(videoNoteMediaStates = it.videoNoteMediaStates + (eventId to VideoNoteMediaState.Downloading(0.1f))) }
            delay(500)
            _uiState.update { it.copy(videoNoteMediaStates = it.videoNoteMediaStates + (eventId to VideoNoteMediaState.Downloading(0.5f))) }
            delay(500)
            _uiState.update { it.copy(videoNoteMediaStates = it.videoNoteMediaStates + (eventId to VideoNoteMediaState.Decrypting)) }
            delay(300)
            _uiState.update { it.copy(videoNoteMediaStates = it.videoNoteMediaStates + (eventId to VideoNoteMediaState.Ready(File("")))) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.cancel()
    }
}
