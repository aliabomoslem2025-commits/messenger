package com.matrixmessenger.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.markAsRead()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { /* Call */ }) {
                        Icon(Icons.Default.Call, contentDescription = "Call")
                    }
                    IconButton(onClick = { /* More options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        },
        bottomBar = {
            MessageInputBar(
                messageInput = uiState.messageInput,
                onMessageChange = viewModel::updateMessageInput,
                onSendMessage = viewModel::sendMessage,
                isSending = uiState.isSending,
                replyingTo = uiState.replyingTo,
                onCancelReply = { viewModel.setReplyingTo(null) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true,
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(uiState.messages.reversed(), key = { it.eventId }) { message ->
                            MessageItem(
                                message = message,
                                onReply = { viewModel.setReplyingTo(message) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: com.matrixmessenger.domain.model.Message,
    onReply: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Avatar
            AsyncImage(
                model = message.senderAvatarUrl,
                contentDescription = message.senderDisplayName,
                modifier = Modifier
                    .size(40.dp)
                    .padding(end = 8.dp)
            )

            Column {
                // Sender name
                Text(
                    text = message.senderDisplayName ?: message.senderId,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Message bubble
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        if (message.replyToEventId != null) {
                            Surface(
                                modifier = Modifier.padding(bottom = 4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Reply",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }

                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        // Timestamp
                        Text(
                            text = formatTime(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageInputBar(
    messageInput: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isSending: Boolean,
    replyingTo: com.matrixmessenger.domain.model.Message?,
    onCancelReply: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Reply preview
            if (replyingTo != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyingTo.senderDisplayName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replyTo.body,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = onCancelReply) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = messageInput,
                    onValueChange = onMessageChange,
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = onSendMessage,
                    enabled = messageInput.isNotBlank() && !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }
}

private fun formatTime(date: java.util.Date): String {
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
}
