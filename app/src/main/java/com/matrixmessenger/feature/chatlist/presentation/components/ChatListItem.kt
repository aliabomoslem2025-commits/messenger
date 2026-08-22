package com.matrixmessenger.feature.chatlist.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.designsystem.components.MatrixAvatar
import com.matrixmessenger.core.designsystem.components.MatrixBadge
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixTypography
import com.matrixmessenger.feature.chatlist.presentation.ChatListItemUiModel

@Composable
fun ChatListItem(
    chat: ChatListItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val backgroundColor = if (isSelected) MatrixColors.DarkSurface else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MatrixDimens.ListItemHeight)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = MatrixDimens.ChatHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MatrixAvatar(
            imageUrl = chat.avatarUrl,
            initials = chat.initials,
            isOnline = chat.isOnline,
            size = MatrixDimens.AvatarMedium
        )

        Spacer(modifier = Modifier.width(MatrixDimens.SpacingMedium))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chat.name,
                    style = MatrixTypography.ChatItemTitle,
                    color = MatrixColors.DarkTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = chat.timestamp,
                    style = MatrixTypography.ChatItemMeta,
                    color = MatrixColors.DarkTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(MatrixDimens.SpacingXXSmall))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val snippetText = if (chat.draftText != null) {
                    "Draft: ${chat.draftText}"
                } else {
                    val prefix = if (chat.senderName != null) "${chat.senderName}: " else ""
                    "$prefix${chat.lastMessage ?: ""}"
                }
                
                val snippetColor = if (chat.draftText != null) MatrixColors.Red else MatrixColors.DarkTextSecondary

                Text(
                    text = snippetText,
                    style = MatrixTypography.ChatItemSnippet,
                    color = snippetColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (chat.unreadCount > 0) {
                    MatrixBadge(
                        count = chat.unreadCount,
                        isMuted = chat.isMuted
                    )
                }
            }
        }
    }
}
