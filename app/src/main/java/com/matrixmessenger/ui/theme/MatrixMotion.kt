package com.matrixmessenger.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.DurationUnit
import androidx.compose.ui.unit.milliseconds

/**
 * Matrix Messenger Animation & Motion System
 * Centralized animation durations, easing curves, and transition specs
 * 
 * Reference: Telegram messenger - subtle, fast, responsive animations
 */
object MatrixMotion {
    
    // ========== Animation Durations (milliseconds) ==========
    val DurationInstant = 0.milliseconds
    val DurationFast = 150.milliseconds
    val DurationNormal = 250.milliseconds
    val DurationSlow = 400.milliseconds
    val DurationVerySlow = 600.milliseconds
    
    // ========== Easing Curves ==========
    val EasingDefault = FastOutSlowInEasing
    val EasingLinear = LinearEasing
    val EasingEntrance = FastOutSlowInEasing
    val EasingExit = FastOutSlowInEasing
    val EasingEmphasized = FastOutSlowInEasing
    
    // ========== Specific Animation Specs ==========
    
    // Message send animation
    val MessageSendDuration = DurationNormal
    val MessageSendEasing = EasingEntrance
    
    // Message appear animation (scale + fade)
    val MessageAppearDuration = DurationNormal
    val MessageAppearScaleStart = 0.92f
    val MessageAppearAlphaStart = 0f
    
    // Bubble morphing animation
    val BubbleMorphDuration = DurationFast
    
    // Send button transition (mic ↔ send)
    val SendButtonTransitionDuration = DurationFast
    val SendButtonRotationDegrees = 360f
    
    // Voice recording animation
    val VoiceRecordingPulseDuration = DurationSlow
    val VoiceRecordingWaveformUpdateInterval = 50.milliseconds
    
    // Typing indicator animation
    val TypingDotDuration = DurationNormal
    val TypingDotDelay = 80.milliseconds
    
    // Read receipt animation
    val ReadReceiptDuration = DurationFast
    
    // Context menu animation
    val ContextMenuEnterDuration = DurationNormal
    val ContextMenuExitDuration = DurationFast
    
    // Bottom sheet / panel animation
    val PanelSlideDuration = DurationNormal
    
    // Navigation transitions
    val NavTransitionDuration = DurationNormal
    val NavFadeDuration = DurationFast
    
    // Shared element transitions
    val SharedElementDuration = DurationSlow
    
    // Reaction animation
    val ReactionFlyDuration = DurationNormal
    val ReactionBounceDuration = DurationFast
    
    // Scroll to bottom animation
    val ScrollToBottomDuration = DurationNormal
    
    // Skeleton loading shimmer
    val ShimmerDuration = DurationVerySlow
    
    // Online indicator pulse
    val OnlinePulseDuration = DurationSlow
    
    // Unread badge scale
    val BadgeScaleDuration = DurationFast
    
    // Input bar resize (keyboard)
    val InputResizeDuration = DurationNormal
    
    // Emoji picker transition
    val EmojiPanelDuration = DurationNormal
    
    // Attachment panel slide
    val AttachmentPanelDuration = DurationNormal
    
    /**
     * Create tween animation spec for duration
     */
    fun <T> tweenSpec(
        duration: Int = DurationNormal.inWholeMilliseconds.toInt(),
        delay: Int = 0,
        easing: androidx.compose.animation.core.Easing = EasingDefault
    ): androidx.compose.animation.core.AnimationSpec<T> {
        @Suppress("UNCHECKED_CAST")
        return tween(
            durationMillis = duration,
            delayMillis = delay,
            easing = easing
        ) as androidx.compose.animation.core.AnimationSpec<T>
    }
    
    /**
     * Get message send animation spec
     */
    fun messageSendSpec(): androidx.compose.animation.core.AnimationSpec<Float> {
        return tween(
            durationMillis = MessageSendDuration.inWholeMilliseconds.toInt(),
            easing = MessageSendEasing
        )
    }
    
    /**
     * Get typing indicator animation spec
     */
    fun typingSpec(delay: Int = 0): androidx.compose.animation.core.AnimationSpec<Float> {
        return tween(
            durationMillis = TypingDotDuration.inWholeMilliseconds.toInt(),
            delayMillis = delay,
            easing = EasingDefault
        )
    }
}

/**
 * Convert milliseconds to Duration
 */
fun Int.ms(): androidx.compose.ui.unit.Duration {
    return this.milliseconds
}
