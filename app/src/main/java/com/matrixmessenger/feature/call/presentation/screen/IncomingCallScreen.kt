package com.matrixmessenger.feature.call.presentation.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matrixmessenger.core.designsystem.components.MatrixAvatar
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.feature.call.presentation.viewModel.CallEvent
import com.matrixmessenger.feature.call.presentation.viewModel.CallViewModel

@Composable
fun IncomingCallScreen(
    onAccept: () -> Unit,
    onReject: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    val infiniteTransition = rememberInfiniteTransition(label = "IncomingPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MatrixColors.DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Pulse background for avatar
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .scale(scale)
                        .background(MatrixColors.Accent.copy(alpha = 0.2f), CircleShape)
                )
                
                MatrixAvatar(
                    imageUrl = uiState.contactAvatarUrl,
                    initials = uiState.contactName.take(1),
                    size = 120.dp
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = uiState.contactName,
                style = MaterialTheme.typography.headlineMedium,
                color = MatrixColors.DarkTextPrimary,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Incoming Matrix Call...",
                style = MaterialTheme.typography.bodyLarge,
                color = MatrixColors.DarkTextSecondary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Decline Button
            CallActionButton(
                icon = Icons.Default.CallEnd,
                label = "Decline",
                color = MatrixColors.Red,
                onClick = {
                    viewModel.onEvent(CallEvent.RejectCall)
                    onReject()
                }
            )
            
            // Accept Button
            CallActionButton(
                icon = Icons.Default.Call,
                label = "Accept",
                color = MatrixColors.Green,
                onClick = {
                    viewModel.onEvent(CallEvent.AcceptCall)
                    onAccept()
                }
            )
        }
    }
}

@Composable
private fun CallActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .background(color, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MatrixColors.DarkTextPrimary
        )
    }
}
