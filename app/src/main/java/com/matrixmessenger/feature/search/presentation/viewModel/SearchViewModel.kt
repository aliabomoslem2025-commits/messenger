package com.matrixmessenger.feature.search.presentation.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrixmessenger.feature.search.domain.model.SearchFilter
import com.matrixmessenger.feature.search.domain.model.SearchResults
import com.matrixmessenger.feature.search.domain.usecase.SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: SearchResults = SearchResults(query = ""),
    val selectedFilter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val error: String? = null
)

sealed interface SearchEvent {
    data class UpdateQuery(val query: String) : SearchEvent
    data class SelectFilter(val filter: SearchFilter) : SearchEvent
    data object ClearSearch : SearchEvent
    data class OpenResult(val resultId: String) : SearchEvent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val queryChannel = Channel<String>(Channel.CONFLATED)

    init {
        // Observe search queries with debouncing
        viewModelScope.launch {
            queryChannel.receiveAsFlow()
                .debounce(300) // Wait 300ms after last keystroke
                .distinctUntilChanged()
                .onEach { query ->
                    performSearch(query)
                }
                .collect()
        }
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.UpdateQuery -> {
                _uiState.value = _uiState.value.copy(
                    query = event.query,
                    isSearching = event.query.isNotBlank()
                )
                queryChannel.trySend(event.query)
            }
            is SearchEvent.SelectFilter -> {
                _uiState.value = _uiState.value.copy(
                    selectedFilter = event.filter
                )
                // Re-search with new filter
                val currentQuery = _uiState.value.query
                if (currentQuery.isNotBlank()) {
                    performSearch(currentQuery)
                }
            }
            is SearchEvent.ClearSearch -> {
                _uiState.value = SearchUiState()
                queryChannel.trySend("")
            }
            is SearchEvent.OpenResult -> {
                // Handle navigation to selected result
                // This would trigger a navigation effect
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun performSearch(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                results = SearchResults(query = ""),
                isSearching = false
            )
            return
        }

        viewModelScope.launch {
            searchUseCase(query, _uiState.value.selectedFilter)
                .catch { e ->
                    _uiState.value = _uiState.value.copy(
                        error = e.message,
                        isSearching = false
                    )
                }
                .onEach { results ->
                    _uiState.value = _uiState.value.copy(
                        results = results,
                        isSearching = false,
                        error = null
                    )
                }
                .collect()
        }
    }
}
