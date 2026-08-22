package com.matrixmessenger.core.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.matrixmessenger.feature.chat.presentation.components.RecordingMode

/**
 * Breathing dot typing indicator.
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "TypingDot")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "DotAlpha"
            )
            
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
    }
}

/**
 * Morphing animation for Send/Mic/Camera button.
 */
@Composable
fun SendMicButtonMorph(
    isTyping: Boolean,
    modifier: Modifier = Modifier,
    recordingMode: RecordingMode = RecordingMode.VOICE
) {
    val targetState = if (isTyping) "send" else if (recordingMode == RecordingMode.VOICE) "mic" else "video"
    
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            (scaleIn(animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )) + fadeIn()).togetherWith(
                scaleOut(animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )) + fadeOut()
            )
        },
        label = "SendMicMorph"
    ) { state ->
        val icon = when (state) {
            "send" -> Icons.AutoMirrored.Filled.Send
            "mic" -> Icons.Default.Mic
            "video" -> Icons.Default.Videocam
            else -> Icons.Default.Mic
        }
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = modifier.size(24.dp),
            tint = if (state == "send") Color.White else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Subtle breathing pulse for online status.
 */
@Composable
fun OnlineStatusPulse(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4CD964)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OnlinePulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
    )
}
