package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.tokens.MatrixColors

/**
 * Voice Message Renderer.
 */
@Composable
fun VoiceRenderer(
    duration: String,
    modifier: Modifier = Modifier,
    onPlayClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlayClick) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Voice",
                tint = MatrixColors.AccentBlue
            )
        }
        
        // Waveform Placeholder
        Box(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .padding(horizontal = 8.dp)
        ) {
            // Draw waveform logic here
            LinearProgressIndicator(
                progress = { 0.3f },
                modifier = Modifier.align(Alignment.Center),
                color = MatrixColors.AccentBlue,
                trackColor = MatrixColors.AccentBlue.copy(alpha = 0.2f)
            )
        }
        
        Text(
            text = duration,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
