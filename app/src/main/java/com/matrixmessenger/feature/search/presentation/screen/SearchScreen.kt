package com.matrixmessenger.feature.search.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.MatrixColors
import com.matrixmessenger.core.designsystem.MatrixDimens
import com.matrixmessenger.core.designsystem.MatrixTypography
import com.matrixmessenger.feature.search.domain.model.SearchFilter
import com.matrixmessenger.feature.search.domain.model.SearchResult
import com.matrixmessenger.feature.search.domain.model.SearchResults
import com.matrixmessenger.feature.search.presentation.viewModel.SearchEvent
import com.matrixmessenger.feature.search.presentation.viewModel.SearchUiState

@Composable
fun SearchScreen(
    state: SearchUiState,
    onEvent: (SearchEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MatrixColors.Background.Primary)
    ) {
        // Search Bar
        SearchBar(
            query = state.query,
            onQueryChange = { onEvent(SearchEvent.UpdateQuery(it)) },
            onClear = { onEvent(SearchEvent.ClearSearch) },
            onNavigateBack = onNavigateBack,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Filter Chips
        FilterRow(
            selectedFilter = state.selectedFilter,
            onFilterSelected = { onEvent(SearchEvent.SelectFilter(it)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        // Results
        when {
            state.isSearching -> {
                SearchLoadingState(modifier = Modifier.weight(1f))
            }
            state.error != null -> {
                SearchErrorState(
                    error = state.error,
                    onRetry = { /* Retry logic */ },
                    modifier = Modifier.weight(1f)
                )
            }
            state.results.isEmpty && state.query.isNotBlank() -> {
                SearchEmptyState(
                    query = state.query,
                    modifier = Modifier.weight(1f)
                )
            }
            else -> {
                SearchResultsList(
                    results = state.results,
                    onResultClick = { resultId -> onEvent(SearchEvent.OpenResult(resultId)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MatrixColors.Surface.Primary
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Back button
            IconButton(onClick = onNavigateBack) {
                Text("←", color = MatrixColors.Text.Secondary)
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Search input
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search",
                        color = MatrixColors.Text.Tertiary,
                        style = MatrixTypography.Body.Medium
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MatrixTypography.Body.Medium.copy(color = MatrixColors.Text.Primary),
                modifier = Modifier.weight(1f)
            )
            
            // Clear button
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Text("✕", color = MatrixColors.Text.Secondary)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selectedFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        SearchFilter.values().forEach { filter ->
            FilterChip(
                label = filter.name.lowercase().capitalize(),
                isSelected = filter == selectedFilter,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MatrixColors.Accent else MatrixColors.Surface.Secondary,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MatrixTypography.Label.Medium,
            color = if (isSelected) Color.White else MatrixColors.Text.Secondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SearchResultsList(
    results: SearchResults,
    onResultClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Users section
        if (results.users.isNotEmpty()) {
            item {
                SectionHeader(title = "People", count = results.users.size)
            }
            items(results.users, key = { it.user.id }) { result ->
                UserResultItem(
                    user = result.user,
                    onClick = { onResultClick(result.user.id) }
                )
            }
        }
        
        // Rooms section
        if (results.rooms.isNotEmpty()) {
            item {
                SectionHeader(title = "Groups & Channels", count = results.rooms.size)
            }
            items(results.rooms, key = { it.room.id }) { result ->
                RoomResultItem(
                    room = result.room,
                    onClick = { onResultClick(result.room.id) }
                )
            }
        }
        
        // Messages section
        if (results.messages.isNotEmpty()) {
            item {
                SectionHeader(title = "Messages", count = results.messages.size)
            }
            items(results.messages, key = { it.message.id }) { result ->
                MessageResultItem(
                    message = result.message,
                    sender = result.sender,
                    roomName = result.room.name ?: "Unknown Room",
                    onClick = { onResultClick(result.message.id) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        text = "$title ($count)",
        style = MatrixTypography.Label.Medium,
        color = MatrixColors.Text.Tertiary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun UserResultItem(user: com.matrixmessenger.core.model.MatrixUser, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MatrixColors.Surface.Secondary)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = user.displayName ?: user.id,
            style = MatrixTypography.Body.Medium,
            color = MatrixColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RoomResultItem(room: com.matrixmessenger.core.model.MatrixRoom, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Room avatar placeholder
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MatrixColors.Surface.Secondary)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = room.name ?: room.alias ?: "Unknown Room",
                style = MatrixTypography.Body.Medium,
                color = MatrixColors.Text.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            room.topic?.let { topic ->
                Text(
                    text = topic,
                    style = MatrixTypography.Body.Small,
                    color = MatrixColors.Text.Tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MessageResultItem(
    message: com.matrixmessenger.core.model.MatrixMessage,
    sender: com.matrixmessenger.core.model.MatrixUser,
    roomName: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = message.body,
            style = MatrixTypography.Body.Medium,
            color = MatrixColors.Text.Primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row {
            Text(
                text = sender.displayName ?: "Unknown",
                style = MatrixTypography.Label.Small,
                color = MatrixColors.Text.Tertiary
            )
            Text(
                text = " • $roomName",
                style = MatrixTypography.Label.Small,
                color = MatrixColors.Text.Tertiary
            )
        }
    }
}

@Composable
private fun SearchLoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MatrixColors.Accent
        )
    }
}

@Composable
private fun SearchEmptyState(query: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No results found",
                style = MatrixTypography.Headline.Small,
                color = MatrixColors.Text.Primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No matches for \"$query\"",
                style = MatrixTypography.Body.Medium,
                color = MatrixColors.Text.Tertiary
            )
        }
    }
}

@Composable
private fun SearchErrorState(error: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Search failed",
                style = MatrixTypography.Headline.Small,
                color = MatrixColors.Error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MatrixTypography.Body.Medium,
                color = MatrixColors.Text.Tertiary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
