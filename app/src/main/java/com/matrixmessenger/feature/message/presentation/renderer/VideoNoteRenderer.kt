package com.matrixmessenger.feature.message.presentation.renderer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.matrixmessenger.core.designsystem.tokens.MessageGroupPosition
import com.matrixmessenger.core.media.VideoNotePlaybackManager
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.feature.chat.presentation.components.VideoNoteContainer
import com.matrixmessenger.feature.chat.presentation.components.VideoNoteMetadataOverlay
import com.matrixmessenger.feature.chat.presentation.components.DownloadOverlay
import com.matrixmessenger.domain.model.VideoNoteMediaState
import javax.inject.Inject
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.net.Uri

class VideoNoteRenderer @Inject constructor(
    private val playbackManager: VideoNotePlaybackManager
) : MessageRenderer {

    @OptIn(UnstableApi::class)
    @Composable
    override fun Render(
        message: Message,
        isOutgoing: Boolean,
        groupPosition: MessageGroupPosition,
        onStatusClick: (Message) -> Unit
    ) {
        val playbackState by playbackManager.getPlaybackState(message.eventId).collectAsState(initial = VideoNotePlaybackManager.PlaybackInfo.Idle)
        
        // Placeholder for real media state
        val mediaState = remember(message.eventId) { VideoNoteMediaState.Ready(java.io.File("")) }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            VideoNoteContainer(
                size = 220.dp,
                progress = if (playbackState.durationMs > 0) playbackState.currentPositionMs.toFloat() / playbackState.durationMs else 0f,
                modifier = Modifier.clickable {
                    when (mediaState) {
                        is VideoNoteMediaState.Ready -> {
                            val mediaUrl = message.attachments.firstOrNull()?.url ?: ""
                            if (mediaUrl.isNotEmpty()) {
                                playbackManager.play(message.eventId, Uri.parse(mediaUrl))
                            }
                        }
                        else -> {}
                    }
                }
            ) {
                when (mediaState) {
                    is VideoNoteMediaState.Ready -> {
                        val mediaUrl = message.attachments.firstOrNull()?.url ?: ""
                        if (playbackState.messageId == message.eventId) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        playbackManager.attachTo(this, message.eventId)
                                    }
                                },
                                update = { view ->
                                    playbackManager.attachTo(view, message.eventId)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
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
                    }
                    else -> {
                        DownloadOverlay(
                            progress = 0f,
                            isDownloading = false,
                            onDownloadClick = {}
                        )
                    }
                }
                
                VideoNoteMetadataOverlay(
                    timestamp = message.timestamp,
                    status = message.deliveryStatus,
                    isOutgoing = isOutgoing
                )
            }
        }
    }
}
