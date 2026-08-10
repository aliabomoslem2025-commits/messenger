package com.matrixmessenger.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrixmessenger.domain.model.Room
import com.matrixmessenger.domain.repository.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val rooms: List<Room> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadRooms()
        observeRooms()
    }

    private fun loadRooms() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Rooms will be loaded via observeRooms
        }
    }

    private fun observeRooms() {
        viewModelScope.launch {
            matrixRepository.observeRooms().collect { rooms ->
                _uiState.value = _uiState.value.copy(
                    rooms = rooms.sortedByDescending { it.timestamp?.time ?: 0 },
                    isLoading = false
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            // Trigger a sync refresh if needed
            _uiState.value = _uiState.value.copy(isRefreshing = false)
        }
    }

    fun logout() {
        viewModelScope.launch {
            matrixRepository.logout()
        }
    }
}
