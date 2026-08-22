package com.matrixmessenger.core.designsystem.tokens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Messenger Motion System
 * Standardized transitions and physics-based animations.
 */
object MessengerMotion {
    
    // Physics
    val SpringBouncy = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)
    val SpringSnappy = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    val SpringSmooth = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    
    // Transitions
    val MessageEnter = fadeIn(tween(300)) + slideInVertically { it / 2 }
    val MessageExit = fadeOut(tween(200)) + slideOutVertically { it / 2 }
    
    // Shared specs
    fun <T> fast(): TweenSpec<T> = tween(150, easing = FastOutSlowInEasing)
    fun <T> normal(): TweenSpec<T> = tween(300, easing = FastOutSlowInEasing)
    fun <T> emphasized(): TweenSpec<T> = tween(500, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
}

/**
 * Legacy support for MatrixMotion
 */
object MatrixMotion {
    
    val SpringBouncy = MessengerMotion.SpringBouncy
    val SpringSnappy = MessengerMotion.SpringSnappy
    val SpringSmooth = MessengerMotion.SpringSmooth

    val DpSpringBouncy = spring<Dp>(
        dampingRatio = 0.6f,
        stiffness = Spring.StiffnessLow
    )

    val DurationFast = 150.milliseconds
    val DurationNormal = 250.milliseconds
    val DurationSlow = 400.milliseconds
    
    val EasingDefault = FastOutSlowInEasing
    val EasingEmphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    
    fun <T> fastTween(): TweenSpec<T> = tween(DurationFast.inWholeMilliseconds.toInt(), easing = EasingDefault)
    fun <T> normalTween(): TweenSpec<T> = tween(DurationNormal.inWholeMilliseconds.toInt(), easing = EasingDefault)
    fun <T> emphasizedTween(): TweenSpec<T> = tween(DurationSlow.inWholeMilliseconds.toInt(), easing = EasingEmphasized)

    val MessageAppearScaleStart = 0.85f
    val MessageAppearAlphaStart = 0f
    
    val SwipeToReplyThreshold = 60.dp
}

fun Int.ms(): Duration = this.milliseconds
