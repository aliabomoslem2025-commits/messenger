package com.matrixmessenger.feature.chat.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrixmessenger.core.designsystem.components.*
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixIcons
import com.matrixmessenger.domain.model.DeliveryStatus
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.feature.chat.presentation.components.ChatHeader
import com.matrixmessenger.feature.chat.presentation.components.MessageInput
import com.matrixmessenger.feature.message.presentation.renderer.MessageRenderer
import com.matrixmessenger.core.designsystem.tokens.MessageGroupPosition
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import com.matrixmessenger.feature.chat.presentation.components.RecordingMode
import com.matrixmessenger.feature.chat.presentation.components.VideoNoteOverlay
import com.matrixmessenger.feature.chat.presentation.VideoNoteState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import android.Manifest
import com.google.accompanist.permissions.isGranted

val LocalMessageRenderer = staticCompositionLocalOf<MessageRenderer> {
    error("No MessageRenderer provided")
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ChatScreen(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCallClick: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    messageRenderer: MessageRenderer
) {
    CompositionLocalProvider(LocalMessageRenderer provides messageRenderer) {
        ChatScreenContent(
            onBackClick = onBackClick,
            onProfileClick = onProfileClick,
            onCallClick = onCallClick,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreenContent(
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onCallClick: () -> Unit,
    viewModel: ChatViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videoNoteState by viewModel.videoNoteController.state.collectAsStateWithLifecycle()
    val messageRenderer = LocalMessageRenderer.current
    val context = LocalContext.current
    val listState = rememberLazyListState()

    var videoButtonOffset by remember { mutableStateOf(Offset.Zero) }

    val audioPermissionState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.selectedMessageForActions) {
        showBottomSheet = uiState.selectedMessageForActions != null
    }

    if (showBottomSheet && uiState.selectedMessageForActions != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectMessage(null) },
            sheetState = sheetState,
            containerColor = MatrixColors.DarkSurface,
            contentColor = MatrixColors.DarkTextPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Retry Sending") },
                    leadingContent = { Icon(MatrixIcons.Send, contentDescription = null, tint = MatrixColors.Accent) },
                    modifier = Modifier.clickable { 
                        viewModel.retryMessage(uiState.selectedMessageForActions!!)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
                ListItem(
                    headlineContent = { Text("Cancel Sending", color = MatrixColors.Red) },
                    leadingContent = { Icon(MatrixIcons.Delete, contentDescription = null, tint = MatrixColors.Red) },
                    modifier = Modifier.clickable { 
                        viewModel.cancelMessage(uiState.selectedMessageForActions!!)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.markAsRead()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            ChatHeader(
                title = uiState.title ?: "Chat",
                subtitle = uiState.status ?: "offline",
                avatarUrl = uiState.avatarUrl,
                onBackClick = onBackClick,
                onCallClick = onCallClick,
                onProfileClick = onProfileClick
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MatrixColors.DarkSurface
            ) {
                MessageInput(
                    state = uiState.composerState,
                    onTextChange = viewModel::updateMessageInput,
                    onSendClick = viewModel::sendMessage,
                    onRecordingStart = { mode, offset ->
                        videoButtonOffset = offset
                        if (mode == RecordingMode.VIDEO) {
                            if (audioPermissionState.status.isGranted && cameraPermissionState.status.isGranted) {
                                viewModel.videoNoteController.onPointerDown(uiState.roomId, context.cacheDir)
                            } else {
                                if (!audioPermissionState.status.isGranted) audioPermissionState.launchPermissionRequest()
                                if (!cameraPermissionState.status.isGranted) cameraPermissionState.launchPermissionRequest()
                            }
                        } else {
                            if (audioPermissionState.status.isGranted) {
                                viewModel.startRecording(context.cacheDir)
                            } else {
                                audioPermissionState.launchPermissionRequest()
                            }
                        }
                    },
                    onRecordingConfirm = { mode ->
                        if (mode == RecordingMode.VIDEO) {
                            viewModel.videoNoteController.startPreparation()
                        }
                    },
                    onRecordingStop = { mode ->
                        if (mode == RecordingMode.VOICE) {
                            viewModel.stopAndSendRecording()
                        } else {
                            viewModel.videoNoteController.onPointerUp()
                        }
                    },
                    onRecordingCancel = { mode ->
                        if (mode == RecordingMode.VOICE) {
                            viewModel.cancelRecording()
                        } else {
                            viewModel.videoNoteController.cancelRecording()
                        }
                    },
                    onAttachClick = { /* TODO */ },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = 20.dp)
                )
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MatrixColors.DarkBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MatrixColors.Accent
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 16.dp)
                ) {
                    itemsIndexed(
                        items = uiState.messages,
                        key = { _, message -> message.eventId }
                    ) { index, message ->
                        val groupPosition = getMessageGroupPosition(index, uiState.messages)
                        messageRenderer.Render(
                            message = message,
                            isOutgoing = message.senderId == uiState.currentUserId,
                            groupPosition = groupPosition,
                            onStatusClick = {
                                if (it.deliveryStatus == DeliveryStatus.FAILED) {
                                    viewModel.selectMessage(it)
                                }
                            }
                        )
                    }
                }

                // Visibility detection for Video Note playback
                val visibleItems by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo } }
                LaunchedEffect(visibleItems) {
                    // Telegram logic: pause if currently playing item is no longer visible
                    // This is a simplified version
                }
            }
        }

        VideoNoteOverlay(
            state = videoNoteState,
            recorder = viewModel.videoNoteController.recorder,
            buttonOffset = videoButtonOffset
        )
    }
}

private fun getMessageGroupPosition(index: Int, messages: List<Message>): MessageGroupPosition {
    if (messages.size <= 1) return MessageGroupPosition.Single
    
    val current = messages[index]
    val prev = if (index < messages.size - 1) messages[index + 1] else null
    val next = if (index > 0) messages[index - 1] else null
    
    val isSameSenderAsPrev = prev?.senderId == current.senderId
    val isSameSenderAsNext = next?.senderId == current.senderId
    
    return when {
        !isSameSenderAsPrev && !isSameSenderAsNext -> MessageGroupPosition.Single
        !isSameSenderAsPrev && isSameSenderAsNext -> MessageGroupPosition.First
        isSameSenderAsPrev && isSameSenderAsNext -> MessageGroupPosition.Middle
        isSameSenderAsPrev && !isSameSenderAsNext -> MessageGroupPosition.Last
        else -> MessageGroupPosition.Single
    }
}
