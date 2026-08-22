package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes

/**
 * Link Preview Component for Messages.
 */
@Composable
fun LinkPreview(
    url: String,
    title: String?,
    description: String?,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MatrixColors.BubbleIncoming.copy(alpha = 0.3f),
        shape = MatrixShapes.CardSmall,
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MatrixColors.AccentBlue.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(MatrixShapes.CardSmall),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall,
                color = MatrixColors.AccentBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
