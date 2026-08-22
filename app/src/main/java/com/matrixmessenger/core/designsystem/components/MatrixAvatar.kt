package com.matrixmessenger.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes

@Composable
fun MatrixAvatar(
    imageUrl: String?,
    initials: String,
    size: Dp = MatrixDimens.AvatarMedium,
    isOnline: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(size)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MatrixShapes.Avatar)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MatrixShapes.Avatar)
                    .background(MatrixColors.DarkSurface)
            ) {
                Text(
                    text = initials,
                    color = MatrixColors.DarkTextPrimary,
                    fontSize = (size.value * 0.4).sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.25f)
                    .align(Alignment.BottomEnd)
                    .background(MatrixColors.Online, CircleShape)
                    .border(1.5.dp, MatrixColors.DarkBackground, CircleShape)
            )
        }
    }
}
