package com.matrixmessenger.feature.search.data.repository

import com.matrixmessenger.feature.search.domain.model.*
import com.matrixmessenger.feature.search.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SearchRepository.
 * Integrates with Matrix SDK for server-side search and local database for cached results.
 */
@Singleton
class SearchRepositoryImpl @Inject constructor() : SearchRepository {

    private val _searchResults = MutableStateFlow(SearchResults(query = ""))
    
    override fun search(query: String, filter: SearchFilter): Flow<SearchResults> {
        // In production, this would:
        // 1. Query Matrix server for full-text search (if enabled)
        // 2. Query local Room database for cached messages/users/rooms
        // 3. Merge and sort results by relevance score
        // 4. Return as Flow for reactive UI updates
        
        return performSearch(query, filter)
    }

    override fun searchMessagesInRoom(roomId: String, query: String): Flow<SearchResults> {
        // Search only within a specific room's message history
        return performRoomSearch(roomId, query)
    }

    override fun searchUsers(query: String): Flow<List<UserResult>> {
        val results = MutableStateFlow<List<UserResult>>(emptyList())
        // Would query Matrix directory or local user cache
        return results
    }

    override fun searchRooms(query: String): Flow<List<RoomResult>> {
        val results = MutableStateFlow<List<RoomResult>>(emptyList())
        // Would query joined rooms and public room directory
        return results
    }
    
    private fun performSearch(query: String, filter: SearchFilter): Flow<SearchResults> {
        val resultsFlow = MutableStateFlow(SearchResults(query = query, isLoading = true))
        
        // Simulate async search operation
        // In production: Use CoroutineDispatchers.IO for database/network operations
        kotlinx.coroutines.GlobalScope.launchWhenStarted {
            // Simulate network/database delay
            delay(300)
            
            if (query.isBlank()) {
                resultsFlow.value = SearchResults(query = query, isLoading = false)
                return@launchWhenStarted
            }
            
            // Mock results - Replace with actual Matrix SDK calls
            val mockUsers = listOf<UserResult>()
            val mockRooms = listOf<RoomResult>()
            val mockMessages = listOf<MessageResult>()
            
            resultsFlow.value = SearchResults(
                query = query,
                users = when (filter) {
                    SearchFilter.ALL, SearchFilter.PEOPLE -> mockUsers
                    else -> emptyList()
                },
                rooms = when (filter) {
                    SearchFilter.ALL, SearchFilter.GROUPS, SearchFilter.CHANNELS -> mockRooms
                    else -> emptyList()
                },
                messages = when (filter) {
                    SearchFilter.ALL, SearchFilter.MESSAGES -> mockMessages
                    else -> emptyList()
                },
                isLoading = false
            )
        }
        
        return resultsFlow.asStateFlow()
    }
    
    private fun performRoomSearch(roomId: String, query: String): Flow<SearchResults> {
        val resultsFlow = MutableStateFlow(SearchResults(query = query, isLoading = true))
        
        kotlinx.coroutines.GlobalScope.launchWhenStarted {
            delay(200)
            
            if (query.isBlank()) {
                resultsFlow.value = SearchResults(query = query, isLoading = false)
                return@launchWhenStarted
            }
            
            // Mock room-specific message search
            resultsFlow.value = SearchResults(
                query = query,
                messages = emptyList(),
                isLoading = false
            )
        }
        
        return resultsFlow.asStateFlow()
    }
}

// Helper to launch coroutines in repository scope
private inline fun kotlinx.coroutines.GlobalScope.launchWhenStarted(
    crossinline block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit
) = this.launch(block = block)
