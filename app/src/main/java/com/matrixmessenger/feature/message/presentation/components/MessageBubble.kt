package com.matrixmessenger.feature.message.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixmessenger.core.animation.MessageAppearAnimation
import com.matrixmessenger.core.animation.MessageSendAnimation
import com.matrixmessenger.core.designsystem.tokens.MatrixColors
import com.matrixmessenger.core.designsystem.tokens.MatrixDimens
import com.matrixmessenger.core.designsystem.tokens.MatrixIcons
import com.matrixmessenger.core.designsystem.tokens.MatrixShapes
import com.matrixmessenger.domain.model.DeliveryStatus
import com.matrixmessenger.domain.model.Message
import com.matrixmessenger.core.designsystem.tokens.MessageGroupPosition
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    isOutgoing: Boolean,
    groupPosition: MessageGroupPosition,
    modifier: Modifier = Modifier,
    onStatusClick: (Message) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val bubbleShape = MatrixShapes.getBubbleShape(
        isOutgoing = isOutgoing,
        isFirst = groupPosition == MessageGroupPosition.First || groupPosition == MessageGroupPosition.Single,
        isLast = groupPosition == MessageGroupPosition.Last || groupPosition == MessageGroupPosition.Single
    )
    
    val containerColor = if (isOutgoing) MatrixColors.DarkBubbleOutgoing else MatrixColors.DarkBubbleIncoming
    
    val bubbleContent = @Composable {
        Surface(
            shape = bubbleShape,
            color = containerColor,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .widthIn(max = 280.dp)
            ) {
                content()
                
                MessageMetadata(
                    timestamp = message.timestamp,
                    status = message.deliveryStatus,
                    isOutgoing = isOutgoing,
                    isEdited = message.isEdited,
                    onStatusClick = { onStatusClick(message) },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MatrixDimens.ChatHorizontalPadding,
                vertical = if (groupPosition == MessageGroupPosition.Middle) 1.dp else 2.dp
            ),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        if (isOutgoing && message.deliveryStatus == DeliveryStatus.SENDING) {
            MessageSendAnimation { animModifier ->
                Box(modifier = animModifier) { bubbleContent() }
            }
        } else {
            MessageAppearAnimation { animModifier ->
                Box(modifier = animModifier) { bubbleContent() }
            }
        }
    }
}

@Composable
fun MessageMetadata(
    timestamp: Date,
    status: DeliveryStatus,
    isOutgoing: Boolean,
    isEdited: Boolean,
    onStatusClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEdited) {
            Text(
                text = "edited",
                style = MaterialTheme.typography.labelSmall,
                color = MatrixColors.DarkTextSecondary.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MatrixColors.DarkTextSecondary.copy(alpha = 0.7f)
        )
        
        if (isOutgoing) {
            Spacer(modifier = Modifier.width(4.dp))
            MessageStatusIcon(
                status = status,
                onClick = onStatusClick
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MessageStatusIcon(
    status: DeliveryStatus,
    onClick: () -> Unit
) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
        },
        label = "StatusIcon"
    ) { targetStatus ->
        when (targetStatus) {
            DeliveryStatus.SENDING -> {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Sending",
                    modifier = Modifier.size(12.dp),
                    tint = MatrixColors.DarkTextSecondary.copy(alpha = 0.7f)
                )
            }
            DeliveryStatus.SENT -> {
                Icon(
                    imageVector = MatrixIcons.Done,
                    contentDescription = "Sent",
                    modifier = Modifier.size(14.dp),
                    tint = MatrixColors.DarkTextSecondary.copy(alpha = 0.7f)
                )
            }
            DeliveryStatus.DELIVERED, DeliveryStatus.READ -> {
                Icon(
                    imageVector = MatrixIcons.DoneAll,
                    contentDescription = "Read",
                    modifier = Modifier.size(14.dp),
                    tint = if (targetStatus == DeliveryStatus.READ) MatrixColors.Blue else MatrixColors.DarkTextSecondary.copy(alpha = 0.7f)
                )
            }
            DeliveryStatus.FAILED -> {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(MatrixColors.Red, CircleShape)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "!",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
