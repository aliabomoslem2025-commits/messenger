package com.matrixmessenger.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "session_prefs")

data class SessionInfo(
    val userId: String,
    val deviceId: String,
    val homeserverUrl: String,
    val accessToken: String
)

@Singleton
class SessionPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val HOMESERVER_URL = stringPreferencesKey("homeserver_url")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
    }
    
    private val dataStore = context.sessionStore
    
    fun observeSessionInfo(): Flow<SessionInfo?> {
        return dataStore.data.map { preferences ->
            val userId = preferences[USER_ID] ?: return@map null
            val deviceId = preferences[DEVICE_ID] ?: return@map null
            val homeserverUrl = preferences[HOMESERVER_URL] ?: return@map null
            val accessToken = preferences[ACCESS_TOKEN] ?: return@map null
            
            SessionInfo(
                userId = userId,
                deviceId = deviceId,
                homeserverUrl = homeserverUrl,
                accessToken = accessToken
            )
        }
    }
    
    suspend fun saveSessionInfo(
        userId: String,
        deviceId: String,
        homeserverUrl: String,
        accessToken: String
    ) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = userId
            preferences[DEVICE_ID] = deviceId
            preferences[HOMESERVER_URL] = homeserverUrl
            preferences[ACCESS_TOKEN] = accessToken
        }
        Timber.d("Session info saved for user: $userId")
    }
    
    suspend fun getSessionInfo(): SessionInfo? {
        return dataStore.data.map { preferences ->
            val userId = preferences[USER_ID] ?: return@map null
            val deviceId = preferences[DEVICE_ID] ?: return@map null
            val homeserverUrl = preferences[HOMESERVER_URL] ?: return@map null
            val accessToken = preferences[ACCESS_TOKEN] ?: return@map null
            
            SessionInfo(
                userId = userId,
                deviceId = deviceId,
                homeserverUrl = homeserverUrl,
                accessToken = accessToken
            )
        }.first()
    }
    
    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(USER_ID)
            preferences.remove(DEVICE_ID)
            preferences.remove(HOMESERVER_URL)
            preferences.remove(ACCESS_TOKEN)
        }
        Timber.d("Session info cleared")
    }
}
