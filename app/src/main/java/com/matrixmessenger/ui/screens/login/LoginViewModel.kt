package com.matrixmessenger.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrixmessenger.data.local.AppPreferences
import com.matrixmessenger.domain.repository.AuthData
import com.matrixmessenger.domain.repository.MatrixRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val username: String = "",
    val password: String = "",
    val homeserverUrl: String = "https://matrix.org",
    val error: String? = null,
    val isLoggedIn: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val matrixRepository: MatrixRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    val authState: StateFlow<AuthData?> = run {
        val flow = MutableStateFlow<AuthData?>(null)
        viewModelScope.launch {
            matrixRepository.observeAuthState().collect { flow.value = it }
        }
        flow.asStateFlow()
    }

    fun updateUsername(username: String) {
        _uiState.value = _uiState.value.copy(username = username)
    }

    fun updatePassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun updateHomeserverUrl(url: String) {
        _uiState.value = _uiState.value.copy(homeserverUrl = url)
    }

    fun login() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val state = _uiState.value
                val result = matrixRepository.login(
                    username = state.username,
                    password = state.password,
                    homeserverUrl = state.homeserverUrl
                )

                result.fold(
                    onSuccess = { authData ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Login failed"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An unexpected error occurred"
                )
            }
        }
    }

    fun register() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val state = _uiState.value
            val result = matrixRepository.register(
                username = state.username,
                password = state.password,
                homeserverUrl = state.homeserverUrl
            )

            result.fold(
                onSuccess = { authData ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Registration failed"
                    )
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
