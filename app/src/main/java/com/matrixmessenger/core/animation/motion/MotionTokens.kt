package com.matrixmessenger.core.animation.motion

import androidx.compose.animation.core.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Global Motion Tokens for Matrix Messenger.
 * Centralized specifications based on Telegram-level interactions.
 */
object MatrixMotion {

    /**
     * Fast interactions (120ms - 180ms)
     * Used for button press, icon change, reaction selection.
     */
    val Fast = tween<Float>(
        durationMillis = 150,
        easing = FastOutSlowInEasing
    )

    /**
     * Normal interactions (250ms - 350ms)
     * Used for message appearance, opening panels, expanding media.
     */
    val Normal = tween<Float>(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    /**
     * Emotional animations (500ms - 900ms)
     * Used for sticker effects, celebrations, special message effects.
     */
    val Emotional = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessLow
    )

    /**
     *Snappy spring for standard UI feedback.
     */
    val Snappy = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )
}

/**
 * Centralized easing curves for Matrix Messenger animations
 */
object MatrixEasing {
    val Default: Easing = FastOutSlowInEasing
    val Entrance: Easing = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 0.6f, 0.2f)
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Overshoot: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val Gentle: Easing = CubicBezierEasing(0.5f, 0f, 0.5f, 1f)
    val Sharp: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/**
 * Legacy/Compatible Spring specifications
 */
object MatrixSpringSpecs {
    val Emotional = MatrixMotion.Emotional
    val Soft = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessLow)
    val Medium = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
    val Stiff = spring<Float>(dampingRatio = 0.85f, stiffness = Spring.StiffnessHigh)
    val Bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)
}

/**
 * Legacy/Compatible Tween specifications
 */
object MatrixTweenSpecs {
    fun <T> fast(delay: Int = 0, easing: Easing = MatrixEasing.Default) = 
        tween<T>(durationMillis = 150, delayMillis = delay, easing = easing)
    
    fun <T> normal(delay: Int = 0, easing: Easing = MatrixEasing.Default) = 
        tween<T>(durationMillis = 300, delayMillis = delay, easing = easing)
    
    fun <T> slow(delay: Int = 0, easing: Easing = MatrixEasing.Default) = 
        tween<T>(durationMillis = 500, delayMillis = delay, easing = easing)
}
