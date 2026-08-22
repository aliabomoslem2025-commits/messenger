package com.matrixmessenger.feature.search.data.repository

import com.matrixmessenger.data.matrix.MatrixClientManager
import com.matrixmessenger.data.matrix.mapper.MessageMapper
import com.matrixmessenger.data.matrix.mapper.RoomMapper
import com.matrixmessenger.data.matrix.mapper.UserMapper
import com.matrixmessenger.feature.search.domain.model.*
import com.matrixmessenger.feature.search.domain.repository.SearchRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val matrixClientManager: MatrixClientManager,
    private val messageMapper: MessageMapper,
    private val roomMapper: RoomMapper,
    private val userMapper: UserMapper
) : SearchRepository {

    override fun search(query: String, filter: SearchFilter): Flow<SearchResults> = flow {
        if (query.isBlank()) {
            emit(SearchResults(query = query))
            return@flow
        }
        
        emit(SearchResults(query = query, isLoading = true))
        
        val users = searchUsersInternal(query)
        
        emit(SearchResults(
            query = query,
            users = users,
            isLoading = false
        ))
    }

    override fun searchMessagesInRoom(roomId: String, query: String): Flow<SearchResults> = flow {
        if (query.isBlank()) {
            emit(SearchResults(query = query))
            return@flow
        }
        
        emit(SearchResults(query = query, isLoading = true))
        
        val results = matrixClientManager.searchMessages(query, roomId).getOrNull() ?: emptyList()
        val currentUserId = matrixClientManager.getCurrentUserId() ?: ""
        
        val roomResult = matrixClientManager.getRoom(roomId)
        val matrixRoom = if (roomResult != null) {
             val isEncrypted = matrixClientManager.isRoomEncrypted(roomId)
             roomMapper.map(roomResult.roomSummary()!!, isEncrypted)
        } else {
            null
        }

        val messages = results.mapNotNull { event ->
            if (matrixRoom == null) return@mapNotNull null
            
            val sender = userMapper.mapToMatrixUser(
                userId = event.senderId ?: "",
                displayName = event.senderId,
                avatarUrl = null
            )
            
            MessageResult(
                score = 1.0f,
                message = messageMapper.mapToMatrixMessage(event.toTimelineEvent(), currentUserId),
                room = matrixRoom,
                sender = sender,
                highlightedText = (event.content?.get("body") as? String) ?: ""
            )
        }
        
        emit(SearchResults(
            query = query,
            messages = messages,
            isLoading = false
        ))
    }

    override fun searchUsers(query: String): Flow<List<UserResult>> = flow {
        emit(searchUsersInternal(query))
    }

    override fun searchRooms(query: String): Flow<List<RoomResult>> = flow {
        emit(emptyList())
    }
    
    private suspend fun searchUsersInternal(query: String): List<UserResult> {
        val results = matrixClientManager.searchUsers(query).getOrNull() ?: emptyList()
        return results.map { user ->
            UserResult(
                score = 1.0f,
                user = user
            )
        }
    }

    private fun org.matrix.android.sdk.api.session.events.model.Event.toTimelineEvent(): org.matrix.android.sdk.api.session.room.timeline.TimelineEvent {
        return org.matrix.android.sdk.api.session.room.timeline.TimelineEvent(
            root = this,
            localId = 0,
            eventId = this.eventId ?: "",
            displayIndex = 0,
            senderInfo = org.matrix.android.sdk.api.session.room.sender.SenderInfo(userId = this.senderId ?: "", displayName = null, isUniqueDisplayName = true, avatarUrl = null)
        )
    }
}
