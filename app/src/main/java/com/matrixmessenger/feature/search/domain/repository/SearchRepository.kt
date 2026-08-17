package com.matrixmessenger.feature.search.domain.repository

import com.matrixmessenger.feature.search.domain.model.SearchFilter
import com.matrixmessenger.feature.search.domain.model.SearchResults
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for search operations.
 */
interface SearchRepository {
    
    /**
     * Perform a global search across all rooms, users, and messages.
     * @param query The search query string.
     * @param filter Optional filter to narrow results by type.
     * @return Flow of aggregated search results.
     */
    fun search(query: String, filter: SearchFilter = SearchFilter.ALL): Flow<SearchResults>
    
    /**
     * Search for messages within a specific room.
     * @param roomId The Matrix room ID to search in.
     * @param query The search query string.
     * @return Flow of message search results.
     */
    fun searchMessagesInRoom(roomId: String, query: String): Flow<SearchResults>
    
    /**
     * Search for users by display name or user ID.
     * @param query The search query string.
     * @return Flow of user search results.
     */
    fun searchUsers(query: String): Flow<List<com.matrixmessenger.feature.search.domain.model.UserResult>>
    
    /**
     * Search for rooms by name or alias.
     * @param query The search query string.
     * @return Flow of room search results.
     */
    fun searchRooms(query: String): Flow<List<com.matrixmessenger.feature.search.domain.model.RoomResult>>
}
