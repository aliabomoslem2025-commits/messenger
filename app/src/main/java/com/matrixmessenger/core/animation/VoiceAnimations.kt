package com.matrixmessenger.core.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.animation.motion.MatrixMotion
import kotlin.math.sin

/**
 * Animated voice waveform for recording and playback.
 */
@Composable
fun VoiceWaveform(
    amplitudes: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Canvas(modifier = modifier.fillMaxWidth().height(32.dp)) {
        val barWidth = 3.dp.toPx()
        val spacing = 2.dp.toPx()
        val totalBarWidth = barWidth + spacing
        
        amplitudes.forEachIndexed { index, amplitude ->
            val x = index * totalBarWidth
            val height = amplitude * size.height
            val color = if (index / amplitudes.size.toFloat() <= progress) activeColor else inactiveColor
            
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}

/**
 * Pulse animation for the record button.
 */
@Composable
fun RecordPulse(
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    if (!isActive) return
    
    val infiniteTransition = rememberInfiniteTransition(label = "RecordPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
    )
}
