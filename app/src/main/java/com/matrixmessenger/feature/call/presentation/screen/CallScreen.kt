package com.matrixmessenger.feature.call.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.MatrixColors
import com.matrixmessenger.core.designsystem.MatrixDimens
import com.matrixmessenger.core.designsystem.MatrixTypography
import com.matrixmessenger.feature.call.domain.model.CallState
import com.matrixmessenger.feature.call.domain.model.CallType
import com.matrixmessenger.feature.call.presentation.viewModel.CallEvent
import com.matrixmessenger.feature.call.presentation.viewModel.CallUiState

@Composable
fun CallScreen(
    state: CallUiState,
    onEvent: (CallEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "call_pulse")
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MatrixColors.Background.Primary),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp)
        ) {
            // Top section: Contact info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Avatar with pulse animation when ringing
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .then(
                            if (state.callState == CallState.Ringing || state.callState == CallState.Dialing) {
                                Modifier.scale(pulseScale)
                            } else {
                                Modifier
                            }
                        )
                ) {
                    // Pulse ring
                    if (state.callState == CallState.Ringing || state.callState == CallState.Dialing) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(MatrixColors.Accent.copy(alpha = 0.3f))
                        )
                    }
                    
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MatrixColors.SurfaceSecondary)
                    ) {
                        // Avatar image or placeholder
                        Text(
                            text = state.contactName.firstOrNull()?.toString() ?: "?",
                            style = MatrixTypography.Headline.Large,
                            color = MatrixColors.Text.Primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Contact name
                Text(
                    text = state.contactName,
                    style = MatrixTypography.Headline.Medium,
                    color = MatrixColors.Text.Primary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Call state indicator
                val stateText = when (state.callState) {
                    is CallState.Idle -> ""
                    is CallState.Dialing -> "Calling..."
                    is CallState.Ringing -> "Incoming call..."
                    is CallState.Connecting -> "Connecting..."
                    is CallState.Connected -> formatDuration(state.callDurationSeconds)
                    is CallState.Reconnecting -> "Reconnecting..."
                    is CallState.Ended -> "Call ended"
                    is CallState.Failed -> state.callState.reason
                }
                
                Text(
                    text = stateText,
                    style = MatrixTypography.Body.Medium,
                    color = MatrixColors.Text.Secondary,
                    textAlign = TextAlign.Center
                )
                
                // Call type badge
                if (state.callState == CallState.Connected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MatrixColors.SurfaceSecondary
                    ) {
                        Text(
                            text = if (state.callType == CallType.VIDEO) "Video Call" else "Audio Call",
                            style = MatrixTypography.Label.Small,
                            color = MatrixColors.Text.Secondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // Bottom section: Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Media controls row (only when connected)
                if (state.callState == CallState.Connected || state.callState == CallState.Connecting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        // Microphone toggle
                        CallControlButton(
                            icon = if (state.mediaState.isMicrophoneMuted) "Mic Off" else "Mic",
                            isActive = !state.mediaState.isMicrophoneMuted,
                            onClick = { onEvent(CallEvent.ToggleMicrophone) }
                        )
                        
                        // Camera toggle (video calls only)
                        if (state.callType == CallType.VIDEO) {
                            CallControlButton(
                                icon = if (!state.mediaState.isCameraEnabled) "Camera Off" else "Camera",
                                isActive = state.mediaState.isCameraEnabled,
                                onClick = { onEvent(CallEvent.ToggleCamera) }
                            )
                            
                            CallControlButton(
                                icon = "Switch Camera",
                                isActive = true,
                                onClick = { onEvent(CallEvent.SwitchCamera) }
                            )
                        }
                        
                        // Speaker toggle
                        CallControlButton(
                            icon = if (state.mediaState.isSpeakerOn) "Speaker On" else "Speaker Off",
                            isActive = state.mediaState.isSpeakerOn,
                            onClick = { /* TODO: Implement speaker toggle */ }
                        )
                    }
                }
                
                // End call button
                CallEndButton(
                    onClick = {
                        when (state.callState) {
                            is CallState.Ringing -> onEvent(CallEvent.RejectCall)
                            else -> onEvent(CallEvent.EndCall)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        color = if (isActive) MatrixColors.SurfaceSecondary else MatrixColors.Error,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = icon.take(1),
                style = MatrixTypography.Body.Large,
                color = Color.White
            )
        }
    }
}

@Composable
private fun CallEndButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(72.dp),
        shape = CircleShape,
        color = MatrixColors.Error,
        onClick = onClick
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "📞",
                style = MatrixTypography.Headline.Small,
                color = Color.White
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
