package com.matrixmessenger.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class SessionPreferences(private val context: Context) {
    
    private val dataStore = context.dataStore
    
    companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val HOMESERVER_URL = stringPreferencesKey("homeserver_url")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_URL = stringPreferencesKey("avatar_url")
    }
    
    val isLoggedInFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            !preferences[ACCESS_TOKEN].isNullOrEmpty()
        }
    
    val accessTokenFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[ACCESS_TOKEN]
        }
    
    val userIdFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[USER_ID]
        }
    
    val homeserverUrlFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[HOMESERVER_URL]
        }
    
    suspend fun saveSession(
        accessToken: String,
        userId: String,
        deviceId: String?,
        homeserverUrl: String,
        displayName: String?,
        avatarUrl: String?
    ) {
        dataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = accessToken
            preferences[USER_ID] = userId
            preferences[DEVICE_ID] = deviceId ?: ""
            preferences[HOMESERVER_URL] = homeserverUrl
            preferences[DISPLAY_NAME] = displayName ?: ""
            preferences[AVATAR_URL] = avatarUrl ?: ""
        }
    }
    
    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
    
    suspend fun getAccessToken(): String? {
        return dataStore.data.first()[ACCESS_TOKEN]
    }
    
    suspend fun getUserId(): String? {
        return dataStore.data.first()[USER_ID]
    }
    
    suspend fun getHomeserverUrl(): String? {
        return dataStore.data.first()[HOMESERVER_URL]
    }
}
