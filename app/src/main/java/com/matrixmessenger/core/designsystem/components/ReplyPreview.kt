package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.tokens.MatrixColors

/**
 * A small preview of a message being replied to, displayed inside a message bubble.
 */
@Composable
fun ReplyPreview(
    senderName: String,
    body: String,
    modifier: Modifier = Modifier,
    senderColor: Color = MatrixColors.AccentBlue
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(Color.White.copy(alpha = 0.1f))
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vertical accent bar
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(senderColor)
        )

        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelMedium,
                color = senderColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
