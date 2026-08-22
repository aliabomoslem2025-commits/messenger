package com.matrixmessenger.core.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixmessenger.core.animation.motion.MatrixMotion
import com.matrixmessenger.core.animation.motion.MatrixSpringSpecs

/**
 * Animated reaction badge for messages.
 */
@Composable
fun MatrixReaction(
    emoji: String,
    count: Int,
    isAddedByMe: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.8f) }
    
    LaunchedEffect(isAddedByMe) {
        if (isAddedByMe) {
            scale.snapTo(1.2f)
            scale.animateTo(1f, MatrixSpringSpecs.Bouncy)
        } else {
            scale.animateTo(1f, MatrixMotion.Fast)
        }
    }

    val backgroundColor = if (isAddedByMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Row(
        modifier = modifier
            .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = emoji, fontSize = 14.sp)
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isAddedByMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Reaction selection animation for the bottom sheet/picker.
 */
@Composable
fun ReactionPickerItem(
    emoji: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.3f else 1f,
        animationSpec = MatrixMotion.Emotional,
        label = "ReactionPickScale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 24.sp)
    }
}
