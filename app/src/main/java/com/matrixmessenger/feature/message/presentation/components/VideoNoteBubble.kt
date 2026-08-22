package com.matrixmessenger.feature.message.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.domain.model.Message
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@OptIn(UnstableApi::class)
@Composable
fun VideoNoteBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isPlayerReady by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0f) }
    
    val mediaUrl = message.attachments.firstOrNull()?.url ?: ""

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        isPlayerReady = true
                    }
                }
            })
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                val duration = exoPlayer.duration
                if (duration > 0) {
                    playbackProgress = exoPlayer.currentPosition.toFloat() / duration
                }
                kotlinx.coroutines.delay(16)
            }
        }
    }

    LaunchedEffect(mediaUrl, isPlaying) {
        if (isPlaying && mediaUrl.isNotEmpty()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(mediaUrl))
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    com.matrixmessenger.feature.chat.presentation.components.VideoNoteContainer(
        size = 200.dp,
        progress = playbackProgress,
        modifier = modifier.clickable { isPlaying = !isPlaying }
    ) {
        if (!isPlaying || !isPlayerReady) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        if (isPlaying) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
