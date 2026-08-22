package com.matrixmessenger.core.animation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.matrixmessenger.core.animation.motion.MatrixMotion

/**
 * Shared element-like transitions for media expanding/collapsing.
 */
@Composable
fun MediaTransition(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = MatrixMotion.Normal) + scaleIn(initialScale = 0.8f, animationSpec = MatrixMotion.Emotional),
        exit = fadeOut(animationSpec = MatrixMotion.Fast) + scaleOut(targetScale = 0.8f, animationSpec = MatrixMotion.Fast),
        modifier = modifier
    ) {
        content(Modifier.fillMaxSize())
    }
}

/**
 * Screen transition Spec for navigation.
 */
object MatrixTransitions {
    val ScreenEnter = fadeIn(animationSpec = MatrixMotion.Normal) + slideInHorizontally(
        initialOffsetX = { it / 2 },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )
    
    val ScreenExit = fadeOut(animationSpec = MatrixMotion.Fast) + slideOutHorizontally(
        targetOffsetX = { -it / 2 },
        animationSpec = tween(150, easing = FastOutSlowInEasing)
    )
}
