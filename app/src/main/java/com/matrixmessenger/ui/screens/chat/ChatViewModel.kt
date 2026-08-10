package com.matrixmessenger.ui.screens.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.domain.repository.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val messageInput: String = "",
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
    val replyingTo: Message? = null
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val roomId: String = savedStateHandle["roomId"] ?: ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        if (roomId.isNotBlank()) {
            observeMessages()
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            matrixRepository.observeMessages(roomId).collect { messages ->
                _uiState.value = _uiState.value.copy(
                    messages = messages.sortedBy { it.timestamp },
                    isLoading = false
                )
            }
        }
    }

    fun updateMessageInput(input: String) {
        _uiState.value = _uiState.value.copy(messageInput = input)
    }

    fun sendMessage() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.messageInput.isBlank() || state.isSending) return@launch

            _uiState.value = state.copy(isSending = true)

            val result = matrixRepository.sendMessage(
                roomId = roomId,
                body = state.messageInput,
                replyToEventId = state.replyingTo?.eventId
            )

            result.fold(
                onSuccess = {
                    _uiState.value = state.copy(
                        messageInput = "",
                        isSending = false,
                        replyingTo = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = state.copy(
                        isSending = false,
                        error = error.message
                    )
                }
            )
        }
    }

    fun sendImage(imageFile: File, caption: String = "") {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSending = true)

            val result = matrixRepository.sendImageMessage(
                roomId = roomId,
                imageFile = imageFile,
                caption = caption.ifBlank { null }
            )

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isSending = false)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSending = false,
                        error = error.message
                    )
                }
            )
        }
    }

    fun setReplyingTo(message: Message?) {
        _uiState.value = _uiState.value.copy(replyingTo = message)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun markAsRead() {
        viewModelScope.launch {
            matrixRepository.markRoomAsRead(roomId)
        }
    }
}
