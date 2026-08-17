package com.matrixmessenger.feature.search.domain.model

import com.matrixmessenger.core.model.MatrixRoom
import com.matrixmessenger.core.model.MatrixUser
import com.matrixmessenger.core.model.MatrixMessage

/**
 * Unified search result model that can represent different types of results.
 */
sealed interface SearchResult {
    val score: Float
}

data class UserResult(
    override val score: Float,
    val user: MatrixUser
) : SearchResult

data class RoomResult(
    override val score: Float,
    val room: MatrixRoom
) : SearchResult

data class MessageResult(
    override val score: Float,
    val message: MatrixMessage,
    val room: MatrixRoom,
    val sender: MatrixUser,
    val highlightedText: String // The matched portion with highlights
) : SearchResult

/**
 * Aggregated search results grouped by type.
 */
data class SearchResults(
    val query: String,
    val users: List<UserResult> = emptyList(),
    val rooms: List<RoomResult> = emptyList(),
    val messages: List<MessageResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val isEmpty: Boolean = users.isEmpty() && rooms.isEmpty() && messages.isEmpty()
    val hasResults: Boolean = !isEmpty
}

/**
 * Filter options for search.
 */
enum class SearchFilter {
    ALL,
    PEOPLE,
    GROUPS,
    CHANNELS,
    MESSAGES
}
