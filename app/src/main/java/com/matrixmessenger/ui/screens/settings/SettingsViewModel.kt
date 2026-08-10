package com.matrixmessenger.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class SettingsUiState(
    val isDarkTheme: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val mediaAutoDownload: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    // Settings implementation would go here
}
