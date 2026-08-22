package com.matrixmessenger.feature.chatlist.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrixmessenger.domain.model.Room
import com.matrixmessenger.domain.repository.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class HomeUiState(
    val chats: List<ChatListItemUiModel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRefreshing: Boolean = false,
    val filters: List<String> = listOf("All", "Personal", "Groups", "Channels", "Bots"),
    val selectedFilter: String = "All"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeRooms()
    }

    private fun observeRooms() {
        viewModelScope.launch {
            matrixRepository.observeRooms().collect { rooms ->
                val sortedRooms = rooms.sortedByDescending { it.timestamp?.time ?: 0 }
                _uiState.value = _uiState.value.copy(
                    chats = sortedRooms.map { it.toUiModel() },
                    isLoading = false
                )
            }
        }
    }

    private fun Room.toUiModel(): ChatListItemUiModel {
        val date = timestamp ?: Date()
        val formattedDate = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        
        return ChatListItemUiModel(
            roomId = roomId,
            name = getDisplayName(),
            avatarUrl = avatarUrl,
            initials = name?.take(1)?.uppercase() ?: "?",
            lastMessage = lastMessage?.body,
            timestamp = formattedDate,
            unreadCount = unreadCount,
            isOnline = unreadCount > 5, // Mock for demo if needed, but better use real presence if available
            isMuted = isMuted,
            isPinned = isPinned,
            senderName = if (isDirect) null else lastMessage?.senderDisplayName
        )
    }

    fun onFilterSelected(filter: String) {
        _uiState.value = _uiState.value.copy(selectedFilter = filter)
    }

    fun logout() {
        viewModelScope.launch {
            matrixRepository.logout()
        }
    }
}
