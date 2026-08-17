package com.matrixmessenger.core.animation.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Centralized easing curves for Matrix Messenger animations
 * 
 * Analyzed from Telegram Android motion patterns:
 * - Heavy use of cubic bezier with overshoot
 * - Entrance: Fast out, slow in (decelerate)
 * - Exit: Slow out, fast in (accelerate)
 */
object MatrixEasing {
    
    /**
     * Default easing for most animations
     * Fast start, slow end - natural deceleration
     */
    val Default: Easing = FastOutSlowInEasing
    
    /**
     * Entrance animation easing
     * Elements entering the screen should decelerate
     */
    val Entrance: Easing = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1f)
    
    /**
     * Exit animation easing
     * Elements leaving should accelerate
     */
    val Exit: Easing = CubicBezierEasing(0.4f, 0f, 0.6f, 0.2f)
    
    /**
     * Emphasized easing for important transitions
     * More pronounced acceleration/deceleration
     */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    
    /**
     * Linear easing for continuous animations
     * Use for: progress bars, infinite loops
     */
    val Linear: Easing = Easing { it }
    
    /**
     * Overshoot easing for bouncy, playful animations
     * Goes slightly beyond target then settles
     */
    val Overshoot: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    
    /**
     * Gentle easing for subtle background animations
     * Very smooth, minimal acceleration
     */
    val Gentle: Easing = CubicBezierEasing(0.5f, 0f, 0.5f, 1f)
    
    /**
     * Sharp easing for quick UI feedback
     * Immediate response, fast settle
     */
    val Sharp: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    
    /**
     * Spring-like easing simulated with cubic bezier
     * For when spring animation is too heavy
     */
    val SpringLike: Easing = CubicBezierEasing(0.25f, 0.8f, 0.25f, 1.2f)
}

/**
 * Spring specifications for physics-based animations
 * 
 * Telegram uses springs extensively for:
 * - Message appear/disappear
 * - Button press feedback
 * - Pull-to-refresh
 * - Sticker emotions
 */
object MatrixSpringSpecs {
    
    /**
     * Soft spring for gentle, emotional animations
     * Low stiffness, moderate damping
     * Use for: sticker emotions, love reactions
     */
    val Soft = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessLow
    )
    
    /**
     * Medium spring for standard UI interactions
     * Balanced stiffness and damping
     * Use for: message bubbles, button feedback
     */
    val Medium = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMedium
    )
    
    /**
     * Stiff spring for quick, snappy feedback
     * High stiffness, low overshoot
     * Use for: toggle switches, quick transitions
     */
    val Stiff = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessHigh
    )
    
    /**
     * Bouncy spring for playful animations
     * Low damping creates noticeable overshoot
     * Use for: reactions, celebrations, stickers
     */
    val Bouncy = spring<Float>(
        dampingRatio = 0.5f,
        stiffness = Spring.StiffnessLow
    )
    
    /**
     * Very bouncy spring for exaggerated effects
     * Maximum overshoot, slow settle
     * Use for: surprise stickers, big celebrations
     */
    val VeryBouncy = spring<Float>(
        dampingRatio = 0.4f,
        stiffness = Spring.StiffnessLow
    )
    
    /**
     * No bounce spring - critically damped
     * Fastest settle without overshoot
     * Use for: professional, serious UI elements
     */
    val NoBounce = spring<Float>(
        dampingRatio = 1f,
        stiffness = Spring.StiffnessMedium
    )
    
    /**
     * Custom spring builder for fine-tuned control
     */
    fun custom(
        dampingRatio: Float = 0.75f,
        stiffness: Float = Spring.StiffnessMedium,
        visibilityThreshold: Float? = null
    ): androidx.compose.animation.core.AnimationSpec<Float> {
        return spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
            visibilityThreshold = visibilityThreshold
        )
    }
}

/**
 * Tween specifications for timed animations
 * 
 * All durations are centralized in MatrixMotion
 * This object provides pre-configured tween specs
 */
object MatrixTweenSpecs {
    
    /**
     * Instant transition - no animation
     */
    fun <T> instant(): androidx.compose.animation.core.AnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        return androidx.compose.animation.core.tween(
            durationMillis = 0
        ) as androidx.compose.animation.core.AnimationSpec<T>
    }
    
    /**
     * Fast tween for quick feedback
     * Duration: 150ms
     */
    fun <T> fast(
        delay: Int = 0,
        easing: Easing = MatrixEasing.Default
    ): androidx.compose.animation.core.AnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        return androidx.compose.animation.core.tween(
            durationMillis = 150,
            delayMillis = delay,
            easing = easing
        ) as androidx.compose.animation.core.AnimationSpec<T>
    }
    
    /**
     * Normal tween for standard transitions
     * Duration: 250ms
     */
    fun <T> normal(
        delay: Int = 0,
        easing: Easing = MatrixEasing.Default
    ): androidx.compose.animation.core.AnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        return androidx.compose.animation.core.tween(
            durationMillis = 250,
            delayMillis = delay,
            easing = easing
        ) as androidx.compose.animation.core.AnimationSpec<T>
    }
    
    /**
     * Slow tween for emotional or emphasized animations
     * Duration: 400ms
     */
    fun <T> slow(
        delay: Int = 0,
        easing: Easing = MatrixEasing.Default
    ): androidx.compose.animation.core.AnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        return androidx.compose.animation.core.tween(
            durationMillis = 400,
            delayMillis = delay,
            easing = easing
        ) as androidx.compose.animation.core.AnimationSpec<T>
    }
    
    /**
     * Very slow tween for dramatic effects
     * Duration: 600ms
     */
    fun <T> verySlow(
        delay: Int = 0,
        easing: Easing = MatrixEasing.Gentle
    ): androidx.compose.animation.core.AnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        return androidx.compose.animation.core.tween(
            durationMillis = 600,
            delayMillis = delay,
            easing = easing
        ) as androidx.compose.animation.core.AnimationSpec<T>
    }
    
    /**
     * Custom tween with explicit duration
     */
    fun <T> custom(
        duration: Int,
        delay: Int = 0,
        easing: Easing = MatrixEasing.Default
    ): androidx.compose.animation.core.AnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        return androidx.compose.animation.core.tween(
            durationMillis = duration,
            delayMillis = delay,
            easing = easing
        ) as androidx.compose.animation.core.AnimationSpec<T>
    }
}
