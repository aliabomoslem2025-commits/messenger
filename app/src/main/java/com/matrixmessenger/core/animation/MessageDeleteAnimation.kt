package com.matrixmessenger.core.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import com.matrixmessenger.core.animation.engine.ParticleSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Cinematic destruction animation for message deletion.
 * Matches Telegram's "Thanos snap" effect.
 */
@Composable
fun MessageDeleteAnimation(
    trigger: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit
) {
    val alpha = remember { Animatable(1f) }
    val scale = remember { Animatable(1f) }
    val particleSystem = remember { ParticleSystem(maxParticles = 60) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(trigger) {
        if (trigger) {
            launch {
                particleSystem.emit(
                    position = Offset(size.width / 2f, size.height / 2f),
                    color = Color.Gray,
                    count = 45
                )
            }
            
            launch {
                // Phase 1: Freeze and slightly scale
                scale.animateTo(1.05f, tween(150))
                // Phase 2: Disintegrate
                alpha.animateTo(0f, tween(450))
                scale.animateTo(0.9f, tween(450))
            }
            
            launch {
                while (particleSystem.isRunning) {
                    particleSystem.update()
                    delay(16.milliseconds)
                }
                onFinished()
            }
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { 
            size = it.size 
        }
    ) {
        content(
            Modifier.graphicsLayer(
                alpha = alpha.value,
                scaleX = scale.value,
                scaleY = scale.value
            )
        )
        
        if (trigger) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                particleSystem.draw(this)
            }
        }
    }
}
