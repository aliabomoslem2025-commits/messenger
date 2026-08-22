package com.matrixmessenger.ui.screens.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrixmessenger.feature.message.presentation.components.MessageBubble
import com.matrixmessenger.feature.message.presentation.components.MessageContent
import com.matrixmessenger.core.designsystem.tokens.MessageGroupPosition
import com.matrixmessenger.domain.model.*
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceValidationScreen(
    onBackClick: () -> Unit
) {
    val messages = remember { generateMockMessages(10000) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Stress Test (10k)") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        /* Back Icon */
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            reverseLayout = true,
            contentPadding = PaddingValues(16.dp)
        ) {
            items(
                items = messages,
                key = { it.eventId }
            ) { message ->
                MessageBubble(
                    message = message,
                    isOutgoing = message.senderId == "me",
                    groupPosition = MessageGroupPosition.Single
                ) {
                    MessageContent(message = message)
                }
            }
        }
    }
}

fun generateMockMessages(count: Int): List<Message> {
    return List(count) { i ->
        val type = when (i % 5) {
            0 -> MessageType.TEXT
            1 -> MessageType.IMAGE
            2 -> MessageType.AUDIO
            3 -> MessageType.FILE
            else -> MessageType.VIDEO
        }
        
        MatrixMessage(
            eventId = "event_$i",
            roomId = "room_1",
            senderId = if (i % 2 == 0) "me" else "other",
            senderDisplayName = "User $i",
            senderAvatarUrl = null,
            body = "Stress test message #$i. Content: ${"Long ".repeat(i % 10)} text for testing.",
            formattedBody = null,
            messageType = type,
            timestamp = Date(),
            reactions = if (i % 3 == 0) listOf(Reaction("👍", i % 5, i % 2 == 0)) else emptyList(),
            attachments = if (type != MessageType.TEXT) listOf(
                Attachment(
                    url = "https://example.com/media_$i",
                    mimeType = "image/jpeg",
                    size = 1024L * i,
                    fileName = "file_$i.jpg",
                    duration = if (type == MessageType.AUDIO) 120L else null
                )
            ) else emptyList()
        )
    }
}
