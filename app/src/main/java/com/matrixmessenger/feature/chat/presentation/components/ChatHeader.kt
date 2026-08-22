package com.matrixmessenger.feature.chat.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.components.MatrixAvatar
import com.matrixmessenger.core.designsystem.components.MatrixIconButton
import com.matrixmessenger.core.designsystem.components.MatrixTopBar
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixIcons
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography
import com.matrixmessenger.core.animation.OnlineStatusPulse

@Composable
fun ChatHeader(
    title: String,
    subtitle: String,
    avatarUrl: String?,
    onBackClick: () -> Unit,
    onCallClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MatrixTopBar(
        title = title,
        subtitle = subtitle,
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MatrixIconButton(
                    icon = MatrixIcons.Back,
                    onClick = onBackClick,
                    tint = MatrixColors.DarkTextSecondary
                )
            }
        },
        actions = {
            if (subtitle == "online") {
                OnlineStatusPulse(
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            MatrixIconButton(
                icon = MatrixIcons.Call,
                onClick = onCallClick,
                tint = MatrixColors.DarkTextSecondary
            )
            MatrixIconButton(
                icon = MatrixIcons.MoreVert,
                onClick = { /* TODO */ },
                tint = MatrixColors.DarkTextSecondary
            )
            
            Spacer(modifier = Modifier.width(MatrixDimens.SpacingSmall))
            
            MatrixAvatar(
                imageUrl = avatarUrl,
                initials = title.take(1).uppercase(),
                size = MatrixDimens.AvatarSmall,
                modifier = Modifier
                    .padding(end = MatrixDimens.SpacingSmall)
                    .clickable(onClick = onProfileClick)
            )
        },
        modifier = modifier
    )
}
