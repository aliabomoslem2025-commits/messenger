package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes

/**
 * Media Renderer for Images and Videos.
 */
@Composable
fun MediaRenderer(
    url: String,
    type: MediaType,
    caption: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(MatrixShapes.CardMedium)
        ) {
            AsyncImage(
                model = url,
                contentDescription = caption ?: "Media",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop
            )
            
            if (type == MediaType.Video) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = MatrixShapes.Avatar,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.padding(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        
        if (!caption.isNullOrBlank()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

enum class MediaType {
    Image, Video
}
