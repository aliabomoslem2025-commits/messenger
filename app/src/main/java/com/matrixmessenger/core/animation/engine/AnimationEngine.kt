package com.matrixmessenger.core.animation.engine

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.matrixmessenger.core.animation.motion.MatrixEasing
import com.matrixmessenger.core.animation.motion.MatrixMotion
import com.matrixmessenger.core.animation.motion.MatrixSpringSpecs
import com.matrixmessenger.core.animation.motion.MatrixTweenSpecs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Centralized animation controller for managing complex animation sequences.
 * Handles scheduling, parallel execution, and lifecycle cleanup.
 */
class AnimationController {
    
    private val activeJobs = mutableListOf<Job>()
    private var isDisposed = false
    
    fun schedule(
        scope: CoroutineScope,
        animation: suspend CoroutineScope.() -> Unit
    ): Job {
        check(!isDisposed) { "AnimationController is disposed" }
        
        val job = scope.launch { animation.invoke(this) }
        activeJobs.add(job)
        job.invokeOnCompletion { activeJobs.remove(job) }
        return job
    }
    
    suspend fun sequence(
        vararg animations: suspend CoroutineScope.() -> Unit
    ) = kotlinx.coroutines.coroutineScope {
        animations.forEach { animation ->
            if (!isDisposed) {
                animation()
            }
        }
    }
    
    fun parallel(
        scope: CoroutineScope,
        vararg animations: suspend CoroutineScope.() -> Unit
    ): List<Job> {
        return animations.map { animation ->
            schedule(scope, animation)
        }
    }
    
    fun cancelAll() {
        activeJobs.forEach { it.cancel() }
        activeJobs.clear()
    }
    
    suspend fun wait(durationMillis: Long) {
        if (!isDisposed) {
            delay(durationMillis)
        }
    }
    
    fun dispose() {
        isDisposed = true
        cancelAll()
    }
}

/**
 * Helper class for creating common animation sequences matching Telegram motion.
 */
object AnimationSequences {
    
    /**
     * Message enter animation (Telegram style)
     * scale 0.85 -> 1.0, alpha 0 -> 1, translationY 30dp -> 0
     */
    suspend fun messageEnter(
        alpha: Animatable<Float, *>,
        scale: Animatable<Float, *>,
        translationY: Animatable<Float, *>
    ) {
        alpha.snapTo(0f)
        scale.snapTo(0.85f)
        translationY.snapTo(30f)
        
        kotlinx.coroutines.coroutineScope {
            launch {
                alpha.animateTo(1f, MatrixMotion.Normal)
            }
            launch {
                scale.animateTo(1f, MatrixMotion.Emotional)
            }
            launch {
                translationY.animateTo(0f, MatrixSpringSpecs.Medium)
            }
        }
    }

    /**
     * Outgoing message send animation
     * scale: 0.92 -> 1.05 -> 1.0
     */
    suspend fun messageSend(
        scale: Animatable<Float, *>,
        alpha: Animatable<Float, *>,
        translationY: Animatable<Float, *>
    ) {
        alpha.snapTo(0f)
        scale.snapTo(0.92f)
        translationY.snapTo(20f)
        
        kotlinx.coroutines.coroutineScope {
            launch {
                alpha.animateTo(1f, MatrixMotion.Fast)
            }
            launch {
                // Telegram-style overshoot
                scale.animateTo(1.05f, tween(150, easing = MatrixEasing.Entrance))
                scale.animateTo(1f, MatrixSpringSpecs.Bouncy)
            }
            launch {
                translationY.animateTo(0f, MatrixSpringSpecs.Medium)
            }
        }
    }
    
    /**
     * Button press feedback
     */
    suspend fun buttonPress(
        scale: Animatable<Float, *>
    ) {
        scale.animateTo(0.92f, MatrixMotion.Fast)
        scale.animateTo(1f, MatrixSpringSpecs.Bouncy)
    }

    /**
     * Status icon morphing
     */
    suspend fun statusIconMorph(
        rotation: Animatable<Float, *>,
        scale: Animatable<Float, *>
    ) {
        kotlinx.coroutines.coroutineScope {
            launch { rotation.animateTo(360f, MatrixMotion.Normal) }
            launch { 
                scale.animateTo(1.2f, MatrixMotion.Fast)
                scale.animateTo(1f, MatrixSpringSpecs.Bouncy)
            }
        }
    }
}
