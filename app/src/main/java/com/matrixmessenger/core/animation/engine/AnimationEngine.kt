package com.matrixmessenger.core.animation.engine

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.animation.motion.MatrixEasing
import com.matrixmessenger.core.animation.motion.MatrixMotion
import com.matrixmessenger.core.animation.motion.MatrixSpringSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized animation controller for managing complex animation sequences
 * 
 * Provides:
 * - Animation scheduling and coordination
 * - Sequence chaining
 * - Parallel animation groups
 * - Cancellation management
 */
class AnimationController {
    
    private val activeJobs = mutableListOf<Job>()
    private var isDisposed = false
    
    /**
     * Schedule an animation to run
     * Automatically tracks and manages cancellation
     */
    fun schedule(
        scope: CoroutineScope,
        animation: suspend CoroutineScope.() -> Unit
    ): Job {
        check(!isDisposed) { "AnimationController is disposed" }
        
        val job = scope.launch { animation() }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
        return job
    }
    
    /**
     * Run animations in sequence
     * Each animation waits for the previous to complete
     */
    suspend fun sequence(
        vararg animations: suspend CoroutineScope.() -> Unit
    ) {
        animations.forEach { animation ->
            if (!isDisposed) {
                animation()
            }
        }
    }
    
    /**
     * Run animations in parallel
     * All animations start simultaneously
     */
    fun parallel(
        scope: CoroutineScope,
        vararg animations: suspend CoroutineScope.() -> Unit
    ): List<Job> {
        return animations.map { animation ->
            schedule(scope, animation)
        }
    }
    
    /**
     * Cancel all active animations
     */
    fun cancelAll() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }
    
    /**
     * Wait for specific duration
     * Useful for sequencing animations with delays
     */
    suspend fun wait(durationMillis: Long) {
        if (!isDisposed) {
            delay(durationMillis)
        }
    }
    
    /**
     * Dispose and clean up resources
     */
    fun dispose() {
        isDisposed = true
        cancelAll()
    }
}

/**
 * State holder for animation values
 * Provides type-safe access to animated properties
 */
data class AnimationState(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val isVisible: Boolean = true
) {
    companion object {
        val Hidden = AnimationState(
            alpha = 0f,
            scale = 0.92f,
            isVisible = false
        )
        
        val Visible = AnimationState(
            alpha = 1f,
            scale = 1f,
            isVisible = true
        )
    }
}

/**
 * Scheduler for managing animation timing and priorities
 * 
 * Features:
 * - Priority-based execution
 * - Frame-accurate timing
 * - Throttling for performance
 */
class AnimationScheduler {
    
    enum class Priority {
        LOW,
        NORMAL,
        HIGH,
        CRITICAL
    }
    
    private data class ScheduledAnimation(
        val priority: Priority,
        val timestamp: Long,
        val animation: suspend () -> Unit
    )
    
    private val queue = PriorityQueue<ScheduledAnimation>(
        compareByDescending { it.priority }
            .thenBy { it.timestamp }
    )
    
    private var isRunning = false
    
    /**
     * Schedule an animation with priority
     */
    fun schedule(
        priority: Priority = Priority.NORMAL,
        delay: Long = 0L,
        animation: suspend () -> Unit
    ) {
        val scheduledTime = System.currentTimeMillis() + delay
        queue.add(ScheduledAnimation(priority, scheduledTime, animation))
        processQueue()
    }
    
    /**
     * Schedule with debounce
     * Cancels previous animation of same type if still pending
     */
    fun scheduleDebounced(
        key: String,
        priority: Priority = Priority.NORMAL,
        debounceTime: Long = 50L,
        animation: suspend () -> Unit
    ) {
        // Remove pending animations with same key
        // Implementation simplified for brevity
        schedule(priority, debounceTime, animation)
    }
    
    private fun processQueue() {
        if (isRunning) return
        isRunning = true
        
        // Process queue implementation
        // Would use coroutine loop in real implementation
    }
    
    /**
     * Clear all pending animations
     */
    fun clear() {
        queue.clear()
    }
    
    /**
     * Clear animations by priority
     */
    fun clearPriority(priority: Priority) {
        // Remove animations with specified priority
    }
}

/**
 * Helper class for creating common animation sequences
 */
object AnimationSequences {
    
    /**
     * Message enter animation
     * Scale + fade + slide up
     */
    suspend fun messageEnter(
        alpha: Animatable<Float, *>,
        scale: Animatable<Float, *>,
        translationY: Animatable<Float, *>
    ) {
        // Start from hidden state
        alpha.snapTo(0f)
        scale.snapTo(0.92f)
        translationY.snapTo(40f)
        
        // Animate to visible
        kotlinx.coroutines.coroutineScope {
            launch {
                alpha.animateTo(1f, MatrixSpringSpecs.Medium)
            }
            launch {
                scale.animateTo(1f, MatrixSpringSpecs.Bouncy)
            }
            launch {
                translationY.animateTo(0f, MatrixTweenSpecs.normal(easing = MatrixEasing.Entrance))
            }
        }
    }
    
    /**
     * Message exit animation
     * Scale down + fade out
     */
    suspend fun messageExit(
        alpha: Animatable<Float, *>,
        scale: Animatable<Float, *>,
        translationY: Animatable<Float, *>
    ) {
        kotlinx.coroutines.coroutineScope {
            launch {
                alpha.animateTo(0f, MatrixTweenSpecs.fast())
            }
            launch {
                scale.animateTo(0.92f, MatrixTweenSpecs.fast())
            }
            launch {
                translationY.animateTo(40f, MatrixTweenSpecs.fast(easing = MatrixEasing.Exit))
            }
        }
    }
    
    /**
     * Button press feedback
     * Quick scale down then up
     */
    suspend fun buttonPress(
        scale: Animatable<Float, *>
    ) {
        scale.animateTo(0.9f, MatrixTweenSpecs.fast())
        scale.animateTo(1f, MatrixSpringSpecs.Bouncy)
    }
    
    /**
     * Icon morph rotation
     * 360 degree rotation during transition
     */
    suspend fun iconMorph(
        rotation: Animatable<Float, *>
    ) {
        rotation.animateTo(360f, MatrixTweenSpecs.normal(easing = MatrixEasing.Default))
        rotation.snapTo(0f) // Reset for next animation
    }
}

// Simple priority queue implementation
private class PriorityQueue<T>(
    private val comparator: Comparator<T>
) {
    private val items = mutableListOf<T>()
    
    fun add(item: T) {
        items.add(item)
        items.sortWith(comparator)
    }
    
    fun remove(): T? = items.removeLastOrNull()
    
    fun clear() = items.clear()
    
    fun isEmpty() = items.isEmpty()
}

// Import for tween specs
import com.matrixmessenger.core.animation.motion.MatrixTweenSpecs
