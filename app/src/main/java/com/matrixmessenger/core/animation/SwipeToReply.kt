package com.matrixmessenger.core.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.animation.motion.MatrixSpringSpecs
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Telegram-style swipe-to-reply interaction with resistance and haptics.
 */
@Composable
fun Modifier.swipeToReply(
    onReplyTriggered: () -> Unit
): Modifier {
    val haptic = rememberHapticManager()
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val threshold = with(density) { 70.dp.toPx() }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    return this
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                scope.launch {
                    val newOffset = (offsetX.value + delta).coerceIn(0f, threshold * 1.4f)
                    offsetX.snapTo(newOffset)
                    
                    if (newOffset >= threshold && !hasTriggeredHaptic) {
                        haptic.impactLight()
                        hasTriggeredHaptic = true
                    } else if (newOffset < threshold) {
                        hasTriggeredHaptic = false
                    }
                }
            },
            onDragStopped = {
                if (offsetX.value >= threshold) {
                    onReplyTriggered()
                    haptic.impactMedium()
                }
                scope.launch {
                    offsetX.animateTo(0f, MatrixSpringSpecs.Medium)
                    hasTriggeredHaptic = false
                }
            }
        )
}
