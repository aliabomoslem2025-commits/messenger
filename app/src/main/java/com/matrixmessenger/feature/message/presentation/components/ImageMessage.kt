package com.matrixmessenger.feature.message.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography

@Composable
fun ImageMessage(
    url: String,
    caption: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        AsyncImage(
            model = url,
            contentDescription = caption,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp)
                .clip(MatrixShapes.Card),
            contentScale = ContentScale.Crop
        )
        
        if (!caption.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(MatrixDimens.SpacingXSmall))
            Text(
                text = caption,
                style = MatrixTypography.MessageBody,
                color = MatrixColors.DarkTextPrimary
            )
        }
    }
}
