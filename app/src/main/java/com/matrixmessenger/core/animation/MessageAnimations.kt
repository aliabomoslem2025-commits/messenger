package com.matrixmessenger.core.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.animation.engine.AnimationSequences
import com.matrixmessenger.core.animation.motion.MatrixMotion
import com.matrixmessenger.core.animation.motion.MatrixTweenSpecs

/**
 * High-level message animations for conversational UI.
 */

@Composable
fun MessageAppearAnimation(
    content: @Composable (Modifier) -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.85f) }
    val translationY = remember { Animatable(30f) }

    LaunchedEffect(Unit) {
        AnimationSequences.messageEnter(alpha, scale, translationY)
    }

    content(
        Modifier.graphicsLayer(
            alpha = alpha.value,
            scaleX = scale.value,
            scaleY = scale.value,
            translationY = translationY.value
        )
    )
}

@Composable
fun MessageSendAnimation(
    content: @Composable (Modifier) -> Unit
) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }
    val translationY = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        AnimationSequences.messageSend(scale, alpha, translationY)
    }

    content(
        Modifier.graphicsLayer(
            alpha = alpha.value,
            scaleX = scale.value,
            scaleY = scale.value,
            translationY = translationY.value
        )
    )
}

@Composable
fun MessageEditAnimation(
    text: String,
    content: @Composable (String) -> Unit
) {
    // Bubble height animation is usually handled by animateContentSize() on the bubble surface
    // Here we focus on text transition and highlight
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (fadeIn(animationSpec = MatrixMotion.Fast) + scaleIn(initialScale = 0.98f)) togetherWith
            fadeOut(animationSpec = MatrixMotion.Fast)
        },
        label = "MessageEdit"
    ) { targetText ->
        content(targetText)
    }
}
