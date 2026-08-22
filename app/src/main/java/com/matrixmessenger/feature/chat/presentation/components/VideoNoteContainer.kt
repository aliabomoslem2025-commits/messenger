package com.matrixmessenger.feature.chat.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixIcons
import com.matrixmessenger.domain.model.DeliveryStatus
import com.matrixmessenger.feature.message.presentation.components.MessageStatusIcon
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun VideoNoteContainer(
    size: Dp,
    progress: Float,
    modifier: Modifier = Modifier,
    showProgress: Boolean = true,
    borderColor: Color = Color.White.copy(alpha = 0.5f),
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                if (showProgress) {
                    val strokeWidth = 4.dp.toPx()
                    // Track
                    drawArc(
                        color = Color.White.copy(alpha = 0.2f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth)
                    )
                    // Progress
                    drawArc(
                        color = MatrixColors.Accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            .padding(4.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun VideoNoteMetadataOverlay(
    timestamp: Date,
    status: DeliveryStatus,
    isOutgoing: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                    startY = 150f // Adjust based on size
                )
            ),
        contentAlignment = Alignment.BottomEnd
    ) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White,
                    fontSize = 10.sp
                )
            )
            if (isOutgoing) {
                Spacer(modifier = Modifier.width(4.dp))
                MessageStatusIcon(
                    status = status,
                    onClick = {} // Handle clicks if needed
                )
            }
        }
    }
}

@Composable
fun DownloadOverlay(
    progress: Float,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        VideoNoteContainer(
            size = 60.dp,
            progress = progress,
            showProgress = isDownloading,
            borderColor = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            // Telegram-style morphing icon could be implemented here
            // For now, a simple download/progress indicator
        }
    }
}
