package com.matrixmessenger.feature.chat.presentation.components

import androidx.camera.view.PreviewView
import androidx.compose.animation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.feature.chat.presentation.VideoNoteState
import com.matrixmessenger.feature.voice.data.recorder.VideoNoteRecorder
import kotlin.math.min

@Composable
fun VideoNoteOverlay(
    state: VideoNoteState,
    recorder: VideoNoteRecorder,
    buttonOffset: Offset,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    
    val targetSize = min(480f, configuration.screenWidthDp.toFloat() - 32f).dp
    
    val isVisible = state !is VideoNoteState.Idle
    
    val expansionProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "Expansion"
    )

    if (expansionProgress > 0.01f) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f * expansionProgress))
        ) {
            val centerX = constraints.maxWidth / 2f
            val centerY = constraints.maxHeight / 2f
            
            val currentX = buttonOffset.x + (centerX - buttonOffset.x) * expansionProgress
            val currentY = buttonOffset.y + (centerY - buttonOffset.y) * expansionProgress
            
            val progress = (state as? VideoNoteState.Recording)?.progress ?: 0f
            val durationMs = (state as? VideoNoteState.Recording)?.durationMs ?: 0L
            
            val animatedSize = targetSize * expansionProgress
            
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (currentX - animatedSize.toPx() / 2f).toInt(),
                            (currentY - animatedSize.toPx() / 2f).toInt()
                        )
                    }
                    .size(animatedSize)
                    .graphicsLayer {
                        alpha = expansionProgress
                    }
            ) {
                VideoNoteContainer(
                    size = animatedSize,
                    progress = progress,
                    showProgress = state is VideoNoteState.Recording,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (isVisible) {
                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    recorder.startPreview(lifecycleOwner, this)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            onRelease = {
                                recorder.stopPreview()
                            }
                        )
                    }
                }
            }

            if (expansionProgress > 0.8f) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = (targetSize + 80.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state is VideoNoteState.Recording) {
                        Text(
                            text = formatDuration(durationMs),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color.White,
                                fontSize = 28.sp
                            )
                        )
                    } else if (state is VideoNoteState.StopRequested || state is VideoNoteState.Finalizing || 
                               state is VideoNoteState.Sending) {
                        val message = when (state) {
                            VideoNoteState.StopRequested, VideoNoteState.Finalizing -> "Processing..."
                            else -> "Uploading..."
                        }
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return "%02d:%02d".format(minutes, seconds)
}
