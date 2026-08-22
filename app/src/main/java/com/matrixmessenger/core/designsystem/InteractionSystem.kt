package com.matrixmessenger.core.designsystem

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import com.matrixmessenger.core.designsystem.tokens.MatrixMotion
import kotlin.math.roundToInt

/**
 * Haptic Feedback Helper
 */
@Composable
fun rememberHapticFeedback(): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

/**
 * Enhanced Swipe-to-Reply Modifier.
 */
@Composable
fun Modifier.swipeToReply(
    onReply: () -> Unit
): Modifier {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val haptic = rememberHapticFeedback()
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 
        MatrixMotion.SwipeToReplyThreshold.toPx() 
    }
    var triggered by remember { mutableStateOf(false) }

    return this
        .offset { IntOffset(offsetX.roundToInt(), 0) }
        .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                val newOffset = offsetX + delta
                // Only swipe right for reply (Telegram style)
                if (newOffset > 0) {
                    offsetX = newOffset * 0.5f // Resistance
                    if (offsetX >= threshold && !triggered) {
                        haptic()
                        triggered = true
                    }
                }
            },
            onDragStopped = {
                if (triggered) {
                    onReply()
                }
                offsetX = 0f
                triggered = false
            }
        )
}

/**
 * Multi-Selection State Holder.
 */
class SelectionState {
    var selectedIds by mutableStateOf(setOf<String>())
    var isInSelectionMode by mutableStateOf(false)

    fun toggle(id: String) {
        selectedIds = if (selectedIds.contains(id)) {
            selectedIds - id
        } else {
            selectedIds + id
        }
        if (selectedIds.isEmpty()) isInSelectionMode = false
    }

    fun clear() {
        selectedIds = emptySet()
        isInSelectionMode = false
    }
}

@Composable
fun rememberSelectionState() = remember { SelectionState() }
