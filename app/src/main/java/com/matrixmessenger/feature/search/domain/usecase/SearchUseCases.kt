package com.matrixmessenger.feature.search.domain.usecase

import com.matrixmessenger.feature.search.domain.model.SearchFilter
import com.matrixmessenger.feature.search.domain.model.SearchResults
import com.matrixmessenger.feature.search.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    operator fun invoke(
        query: String,
        filter: SearchFilter = SearchFilter.ALL
    ): Flow<SearchResults> {
        return searchRepository.search(query, filter)
    }
}

class SearchMessagesInRoomUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    operator fun invoke(
        roomId: String,
        query: String
    ): Flow<SearchResults> {
        return searchRepository.searchMessagesInRoom(roomId, query)
    }
}
