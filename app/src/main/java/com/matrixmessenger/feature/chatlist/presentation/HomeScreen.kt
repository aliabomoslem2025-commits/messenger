package com.matrixmessenger.feature.chatlist.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixIcons
import com.matrixmessenger.feature.chatlist.presentation.components.ChatFilterRow
import com.matrixmessenger.feature.chatlist.presentation.components.ChatListHeader
import com.matrixmessenger.feature.chatlist.presentation.components.ChatListItem

@Composable
fun HomeScreen(
    onRoomClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onNewChatClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Surface(
                color = MatrixColors.DarkSurface,
                tonalElevation = 4.dp
            ) {
                Column {
                    ChatListHeader(
                        onMenuClick = onProfileClick,
                        onSearchClick = { /* TODO: Implement Search */ }
                    )
                    ChatFilterRow(
                        filters = uiState.filters,
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = viewModel::onFilterSelected
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChatClick,
                containerColor = MatrixColors.Accent,
                contentColor = MatrixColors.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(
                    imageVector = MatrixIcons.Edit,
                    contentDescription = "New Chat"
                )
            }
        },
        containerColor = MatrixColors.DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MatrixColors.Accent
                    )
                }
                uiState.chats.isEmpty() -> {
                    EmptyChatList(modifier = Modifier.align(Alignment.Center))
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = uiState.chats,
                            key = { it.roomId }
                        ) { chat ->
                            ChatListItem(
                                chat = chat,
                                onClick = { onRoomClick(chat.roomId) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 88.dp),
                                thickness = 0.5.dp,
                                color = MatrixColors.DarkDivider
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No chats yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MatrixColors.DarkTextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start a conversation by clicking the button below",
            style = MaterialTheme.typography.bodyMedium,
            color = MatrixColors.DarkTextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
