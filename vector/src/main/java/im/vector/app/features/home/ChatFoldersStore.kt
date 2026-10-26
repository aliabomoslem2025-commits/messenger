/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import im.vector.app.R
import im.vector.app.features.home.room.list.home.header.HomeRoomFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local presentation preferences for Telegram-style folders. Room membership, unread
 * state and favourites still come from Matrix; this class persists only folder names,
 * order, visibility and the selected Matrix query category.
 */
data class ChatFolderDefinition(
        val id: String,
        val name: String,
        val filter: HomeRoomFilter,
        val enabled: Boolean = true
)

@Singleton
class ChatFoldersStore @Inject constructor(
        @ApplicationContext private val context: Context
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _folders = MutableStateFlow(readFolders())
    val foldersFlow: StateFlow<List<ChatFolderDefinition>> = _folders

    private val _separateChats = MutableStateFlow(preferences.getBoolean(KEY_SEPARATE_CHATS, true))
    val separateChatsFlow: StateFlow<Boolean> = _separateChats

    private val _selectedFolderId = MutableStateFlow(preferences.getString(KEY_SELECTED_FOLDER, "all").orEmpty())
    val selectedFolderIdFlow: StateFlow<String> = _selectedFolderId

    fun selectedFolderId(): String = _selectedFolderId.value

    fun setSelectedFolder(id: String) {
        _selectedFolderId.value = id
        preferences.edit().putString(KEY_SELECTED_FOLDER, id).apply()
    }

    fun visibleFolders(): List<ChatFolderDefinition> {
        return _folders.value.filter { folder ->
            folder.enabled && (
                    _separateChats.value ||
                            (folder.filter != HomeRoomFilter.PEOPlE && folder.filter != HomeRoomFilter.ROOMS)
                    )
        }
    }

    fun isSeparateChatsEnabled(): Boolean = _separateChats.value

    fun setSeparateChatsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SEPARATE_CHATS, enabled).apply()
        _separateChats.value = enabled
    }

    fun saveFolders(folders: List<ChatFolderDefinition>) {
        // Keep All Chats available as the first escape route to the full Matrix room list.
        val all = folders.firstOrNull { it.filter == HomeRoomFilter.ALL }
                ?: defaultFolders().first()
        val normalized = listOf(all.copy(enabled = true)) +
                folders.filterNot { it.id == all.id || it.filter == HomeRoomFilter.ALL }
        val json = JSONArray()
        normalized.forEach { folder ->
            json.put(JSONObject()
                    .put("id", folder.id)
                    .put("name", folder.name)
                    .put("filter", folder.filter.name)
                    .put("enabled", folder.enabled))
        }
        preferences.edit().putString(KEY_FOLDERS, json.toString()).apply()
        _folders.value = normalized
        if (normalized.none { it.id == _selectedFolderId.value && it.enabled }) {
            setSelectedFolder(normalized.firstOrNull { it.filter == HomeRoomFilter.ALL }?.id ?: "all")
        }
    }

    fun createFolder(name: String, filter: HomeRoomFilter): Boolean {
        val cleanName = name.trim().take(MAX_FOLDER_NAME_LENGTH)
        if (cleanName.isBlank()) return false
        saveFolders(_folders.value + ChatFolderDefinition(UUID.randomUUID().toString(), cleanName, filter))
        return true
    }

    fun renameFolder(id: String, name: String): Boolean {
        val cleanName = name.trim().take(MAX_FOLDER_NAME_LENGTH)
        if (cleanName.isBlank()) return false
        saveFolders(_folders.value.map { if (it.id == id && it.filter != HomeRoomFilter.ALL) it.copy(name = cleanName) else it })
        return true
    }

    fun setFolderEnabled(id: String, enabled: Boolean) {
        saveFolders(_folders.value.map { if (it.id == id && it.filter != HomeRoomFilter.ALL) it.copy(enabled = enabled) else it })
    }

    fun moveFolder(id: String, offset: Int) {
        val current = _folders.value.toMutableList()
        val oldIndex = current.indexOfFirst { it.id == id }
        if (oldIndex <= 0) return
        val newIndex = (oldIndex + offset).coerceIn(1, current.lastIndex)
        if (newIndex == oldIndex) return
        val item = current.removeAt(oldIndex)
        current.add(newIndex, item)
        saveFolders(current)
    }

    fun deleteFolder(id: String) {
        saveFolders(_folders.value.filterNot { it.id == id || it.filter == HomeRoomFilter.ALL && it.id == id })
    }

    fun restoreDefaults() {
        saveFolders(defaultFolders())
        setSeparateChatsEnabled(true)
    }

    private fun readFolders(): List<ChatFolderDefinition> {
        val stored = preferences.getString(KEY_FOLDERS, null)
        if (stored.isNullOrBlank()) return defaultFolders()
        return runCatching {
            val json = JSONArray(stored)
            val parsed = (0 until json.length()).mapNotNull { index ->
                val item = json.optJSONObject(index) ?: return@mapNotNull null
                val filter = runCatching { HomeRoomFilter.valueOf(item.optString("filter")) }.getOrNull()
                        ?: return@mapNotNull null
                val name = item.optString("name").trim().take(MAX_FOLDER_NAME_LENGTH)
                if (name.isBlank()) return@mapNotNull null
                ChatFolderDefinition(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = name,
                        filter = filter,
                        enabled = item.optBoolean("enabled", true)
                )
            }
            val uniqueIds = parsed.distinctBy { it.id }
            val all = uniqueIds.firstOrNull { it.filter == HomeRoomFilter.ALL }
                    ?: defaultFolders().first()
            listOf(all.copy(enabled = true)) + uniqueIds.filterNot { it.id == all.id || it.filter == HomeRoomFilter.ALL }
        }.getOrElse { defaultFolders() }
    }

    private fun defaultFolders(): List<ChatFolderDefinition> = listOf(
            ChatFolderDefinition("all", context.getString(R.string.telegram_filter_all_chats), HomeRoomFilter.ALL, true),
            ChatFolderDefinition("personal", context.getString(R.string.telegram_folder_personal), HomeRoomFilter.PEOPlE, true),
            ChatFolderDefinition("groups", context.getString(R.string.telegram_folder_groups), HomeRoomFilter.ROOMS, true),
            ChatFolderDefinition("unread", context.getString(R.string.telegram_filter_unread), HomeRoomFilter.UNREADS, true),
            ChatFolderDefinition("favorites", context.getString(R.string.telegram_filter_favourites), HomeRoomFilter.FAVOURITES, true)
    )

    companion object {
        private const val PREFS_NAME = "telegram_chat_folders"
        private const val KEY_FOLDERS = "folder_definitions"
        private const val KEY_SEPARATE_CHATS = "separate_chats"
        private const val KEY_SELECTED_FOLDER = "selected_folder"
        private const val MAX_FOLDER_NAME_LENGTH = 28
    }
}
