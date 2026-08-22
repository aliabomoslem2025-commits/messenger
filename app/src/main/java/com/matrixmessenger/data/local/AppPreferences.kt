package com.matrixmessenger.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.matrixmessenger.domain.model.PresenceState
import com.matrixmessenger.domain.repository.AuthData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val HOMESERVER_URL = stringPreferencesKey("homeserver_url")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val MEDIA_AUTO_DOWNLOAD = booleanPreferencesKey("media_auto_download")
        val LAST_SYNC_TOKEN = stringPreferencesKey("last_sync_token")
    }
    
    private val dataStore = context.dataStore
    
    val authState: Flow<AuthData?> = dataStore.data.map { preferences ->
        val accessToken = preferences[ACCESS_TOKEN] ?: return@map null
        val userId = preferences[USER_ID] ?: return@map null
        val deviceId = preferences[DEVICE_ID] ?: return@map null
        val homeserverUrl = preferences[HOMESERVER_URL] ?: return@map null
        
        AuthData(
            userId = userId,
            accessToken = accessToken,
            deviceId = deviceId,
            homeserverUrl = homeserverUrl,
            refreshToken = preferences[REFRESH_TOKEN]
        )
    }
    
    val isDarkTheme: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[IS_DARK_THEME] ?: false
    }
    
    suspend fun saveAuthData(authData: AuthData) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = authData.accessToken
            preferences[USER_ID] = authData.userId
            preferences[DEVICE_ID] = authData.deviceId
            preferences[HOMESERVER_URL] = authData.homeserverUrl
            authData.refreshToken?.let { preferences[REFRESH_TOKEN] = it }
        }
        Timber.d("Auth data saved for user: ${authData.userId}")
    }
    
    suspend fun saveHomeserverUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[HOMESERVER_URL] = url
        }
    }
    
    suspend fun clearAuthData() {
        dataStore.edit { preferences ->
            preferences.remove(ACCESS_TOKEN)
            preferences.remove(USER_ID)
            preferences.remove(DEVICE_ID)
            preferences.remove(HOMESERVER_URL)
            preferences.remove(REFRESH_TOKEN)
            preferences.remove(DISPLAY_NAME)
            preferences.remove(AVATAR_URL)
            preferences.remove(LAST_SYNC_TOKEN)
        }
        Timber.d("Auth data cleared")
    }
    
    suspend fun setDarkTheme(isDark: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_DARK_THEME] = isDark
        }
    }
    
    suspend fun setDisplayName(name: String) {
        dataStore.edit { preferences ->
            preferences[DISPLAY_NAME] = name
        }
    }
    
    suspend fun setAvatarUrl(url: String) {
        dataStore.edit { preferences ->
            preferences[AVATAR_URL] = url
        }
    }
    
    suspend fun setNotificationEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATION_ENABLED] = enabled
        }
    }
    
    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED] = enabled
        }
    }
    
    suspend fun setMediaAutoDownload(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[MEDIA_AUTO_DOWNLOAD] = enabled
        }
    }
    
    suspend fun saveSyncToken(token: String) {
        dataStore.edit { preferences ->
            preferences[LAST_SYNC_TOKEN] = token
        }
    }
    
    suspend fun getSyncToken(): String? {
        return dataStore.data.map { preferences ->
            preferences[LAST_SYNC_TOKEN]
        }.firstOrNull()
    }
    
    fun getCacheDir(): File {
        return context.cacheDir.resolve("matrix_cache").apply { mkdirs() }
    }
    
    fun getMediaCacheDir(): File {
        return context.cacheDir.resolve("matrix_media").apply { mkdirs() }
    }
}
