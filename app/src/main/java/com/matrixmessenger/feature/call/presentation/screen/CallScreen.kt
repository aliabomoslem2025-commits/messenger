package com.matrixmessenger.feature.call.presentation.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrixmessenger.core.designsystem.components.MatrixAvatar
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.feature.call.domain.model.CallState
import com.matrixmessenger.feature.call.domain.model.CallType
import com.matrixmessenger.feature.call.presentation.viewModel.CallEvent
import com.matrixmessenger.feature.call.presentation.viewModel.CallViewModel
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    onDismiss: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val eglBase = remember { EglBase.create() }
    
    DisposableEffect(Unit) {
        onDispose {
            eglBase.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (uiState.callType == CallType.VIDEO) {
            VideoCallContent(uiState = uiState, eglBaseContext = eglBase.eglBaseContext)
        } else {
            AudioCallContent(uiState = uiState)
        }

        // Common Controls Overlay
        CallControls(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onEndCall = {
                viewModel.onEvent(CallEvent.EndCall)
                onDismiss()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
        
        // Timer/Status at the top
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = uiState.contactName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            val statusText = when (val state = uiState.callState) {
                CallState.Dialing -> "Dialing..."
                CallState.Connecting -> "Connecting..."
                CallState.Ringing -> "Ringing..."
                CallState.Connected -> formatDuration(uiState.callDurationSeconds)
                CallState.Ended -> "Call Ended"
                is CallState.Failed -> "Call Failed: ${state.reason}"
                else -> ""
            }
            
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun VideoCallContent(
    uiState: com.matrixmessenger.feature.call.presentation.viewModel.CallUiState,
    eglBaseContext: EglBase.Context
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Remote Video (Full Screen)
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(eglBaseContext, null)
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    setEnableHardwareScaler(true)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Local Video (PiP)
        if (uiState.mediaState.isCameraEnabled) {
            Box(
                modifier = Modifier
                    .size(120.dp, 160.dp)
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color.DarkGray)
            ) {
                AndroidView(
                    factory = { context ->
                        SurfaceViewRenderer(context).apply {
                            init(eglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setMirror(true)
                            setEnableHardwareScaler(true)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun AudioCallContent(uiState: com.matrixmessenger.feature.call.presentation.viewModel.CallUiState) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        MatrixAvatar(
            imageUrl = uiState.contactAvatarUrl,
            initials = uiState.contactName.take(1),
            size = 120.dp
        )
    }
}

@Composable
fun CallControls(
    uiState: com.matrixmessenger.feature.call.presentation.viewModel.CallUiState,
    onEvent: (CallEvent) -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute
        ControlButton(
            icon = if (uiState.mediaState.isMicrophoneMuted) Icons.Default.MicOff else Icons.Default.Mic,
            onClick = { onEvent(CallEvent.ToggleMicrophone) },
            isActive = !uiState.mediaState.isMicrophoneMuted
        )
        
        // End Call
        IconButton(
            onClick = onEndCall,
            modifier = Modifier
                .size(72.dp)
                .background(MatrixColors.Red, CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End Call",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        // Speaker
        ControlButton(
            icon = if (uiState.mediaState.isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
            onClick = { onEvent(CallEvent.ToggleSpeaker) },
            isActive = uiState.mediaState.isSpeakerOn
        )
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isActive: Boolean
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .background(
                if (isActive) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
