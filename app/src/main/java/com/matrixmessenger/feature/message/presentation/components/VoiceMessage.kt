package com.matrixmessenger.feature.message.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.animation.VoiceWaveform
import com.matrixmessenger.core.designsystem.components.MatrixIconButton
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography

@Composable
fun VoiceMessage(
    duration: String,
    amplitudes: List<Float> = emptyList(),
    progress: Float = 0f,
    isPlaying: Boolean = false,
    onTogglePlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MatrixIconButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            onClick = onTogglePlay,
            tint = MatrixColors.Accent
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Use the animated voice waveform
        VoiceWaveform(
            amplitudes = if (amplitudes.isEmpty()) generateMockAmplitudes() else amplitudes,
            progress = progress,
            modifier = Modifier.weight(1f),
            activeColor = MatrixColors.Accent,
            inactiveColor = MatrixColors.DarkTextSecondary.copy(alpha = 0.3f)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Text(
            text = duration,
            style = MatrixTypography.MessageMeta,
            color = MatrixColors.DarkTextSecondary
        )
    }
}

private fun generateMockAmplitudes(): List<Float> {
    return List(25) { (0.2f..0.8f).random() }
}

private fun ClosedRange<Float>.random() = 
    (Math.random() * (endInclusive - start) + start).toFloat()
