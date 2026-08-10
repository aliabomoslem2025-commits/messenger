package com.matrixmessenger.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class AppPreferences(private val context: Context) {
    
    private val dataStore = context.dataStore
    
    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val SHOW_PREVIEW = booleanPreferencesKey("show_preview")
        val MEDIA_AUTO_DOWNLOAD_WIFI = booleanPreferencesKey("media_auto_download_wifi")
        val MEDIA_AUTO_DOWNLOAD_MOBILE = booleanPreferencesKey("media_auto_download_mobile")
        val SEND_READ_RECEIPTS = booleanPreferencesKey("send_read_receipts")
        val SHOW_TIMESTAMPS = booleanPreferencesKey("show_timestamps")
        val ENTER_SEND = booleanPreferencesKey("enter_send")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
    }
    
    val themeModeFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[THEME_MODE] ?: "system"
        }
    
    val notificationEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[NOTIFICATION_ENABLED] ?: true
        }
    
    val sendReadReceiptsFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[SEND_READ_RECEIPTS] ?: true
        }
    
    suspend fun setThemeMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }
    
    suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_ENABLED] = enabled
        }
    }
    
    suspend fun setSendReadReceipts(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SEND_READ_RECEIPTS] = enabled
        }
    }
    
    suspend fun setEnterSend(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[ENTER_SEND] = enabled
        }
    }
}
