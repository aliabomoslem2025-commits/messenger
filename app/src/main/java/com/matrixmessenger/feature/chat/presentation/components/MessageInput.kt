package com.matrixmessenger.feature.chat.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixmessenger.core.designsystem.components.ComposerState
import com.matrixmessenger.core.designsystem.components.MatrixIconButton
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixIcons
import com.matrixmessenger.core.animation.SendMicButtonMorph
import com.matrixmessenger.core.animation.rememberHapticManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun MessageInput(
    state: ComposerState,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onRecordingStart: (RecordingMode, Offset) -> Unit,
    onRecordingConfirm: (RecordingMode) -> Unit,
    onRecordingStop: (RecordingMode) -> Unit,
    onRecordingCancel: (RecordingMode) -> Unit,
    onAttachClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var recordingMode by remember { mutableStateOf(RecordingMode.VOICE) }
    var buttonOffset by remember { mutableStateOf(Offset.Zero) }
    val haptic = rememberHapticManager()
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MatrixColors.DarkSurface
    ) {
        Column {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MatrixColors.DarkDivider
            )
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                MatrixIconButton(
                    icon = Icons.Default.SentimentSatisfied,
                    onClick = { /* TODO */ },
                    tint = MatrixColors.DarkTextSecondary
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    val currentText = when (state) {
                        is ComposerState.Typing -> state.text
                        is ComposerState.Replying -> state.text
                        else -> ""
                    }

                    if (currentText.isEmpty() && state !is ComposerState.RecordingVoice) {
                        Text(
                            text = "Message",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = MatrixColors.DarkTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    
                    if (state is ComposerState.RecordingVoice) {
                        RecordingIndicator(
                            durationMs = state.durationMs,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
                        )
                    } else {
                        BasicTextField(
                            value = currentText,
                            onValueChange = onTextChange,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MatrixColors.DarkTextPrimary,
                                fontSize = 16.sp
                            ),
                            cursorBrush = SolidColor(MatrixColors.Accent),
                            maxLines = 6
                        )
                    }
                }

                if (state !is ComposerState.RecordingVoice) {
                    MatrixIconButton(
                        icon = MatrixIcons.Attach,
                        onClick = onAttachClick,
                        tint = MatrixColors.DarkTextSecondary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                val isTyping = when (state) {
                    is ComposerState.Typing -> state.text.isNotEmpty()
                    is ComposerState.Replying -> state.text.isNotEmpty()
                    else -> false
                }

                if (isTyping) {
                    IconButton(
                        onClick = onSendClick,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MatrixColors.Accent, CircleShape)
                    ) {
                        SendMicButtonMorph(
                            isTyping = true,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .onGloballyPositioned { coordinates ->
                                val position = coordinates.positionInWindow()
                                buttonOffset = Offset(
                                    position.x + coordinates.size.width / 2f,
                                    position.y + coordinates.size.height / 2f
                                )
                            }
                            .pointerInput(recordingMode) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown()
                                        var isLongPress = false
                                        
                                        onRecordingStart(recordingMode, buttonOffset)

                                        val longPressTimer = scope.launch {
                                            delay(400)
                                            isLongPress = true
                                            haptic.impactLight()
                                            onRecordingConfirm(recordingMode)
                                        }

                                        val up = waitForUpOrCancellation()
                                        longPressTimer.cancel()

                                        if (up != null) {
                                            if (isLongPress) {
                                                haptic.impactMedium()
                                                onRecordingStop(recordingMode)
                                            } else {
                                                haptic.impactLight()
                                                onRecordingCancel(recordingMode) // Cancel the "start" that was just Down
                                                recordingMode = if (recordingMode == RecordingMode.VOICE) {
                                                    RecordingMode.VIDEO
                                                } else {
                                                    RecordingMode.VOICE
                                                }
                                            }
                                        } else {
                                            // Cancelled
                                            onRecordingCancel(recordingMode)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SendMicButtonMorph(
                            isTyping = false,
                            modifier = Modifier.size(24.dp),
                            recordingMode = recordingMode
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingIndicator(
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "Recording")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Blink"
        )
        
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MatrixColors.Red.copy(alpha = alpha), CircleShape)
        )
        
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.bodyMedium,
            color = MatrixColors.DarkTextPrimary
        )
        
        Text(
            text = "Recording...",
            style = MaterialTheme.typography.bodyMedium,
            color = MatrixColors.DarkTextSecondary
        )
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return "%02d:%02d".format(minutes, seconds)
}
