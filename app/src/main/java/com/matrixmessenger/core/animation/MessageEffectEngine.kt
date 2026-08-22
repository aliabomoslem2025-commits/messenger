package com.matrixmessenger.core.animation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.matrixmessenger.core.animation.engine.ParticleSystem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Engine for global message effects (Love, Fire, Confetti).
 */
@Composable
fun MessageEffectEngine(
    effect: MessageEffectType,
    anchor: Offset,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {}
) {
    if (effect == MessageEffectType.NONE) return

    val particleSystem = remember { ParticleSystem(maxParticles = 100) }
    
    LaunchedEffect(effect) {
        val color = when (effect) {
            MessageEffectType.LOVE -> Color.Red
            MessageEffectType.FIRE -> Color(0xFFFF5722)
            MessageEffectType.CONFETTI -> Color.Blue
            MessageEffectType.LAUGH -> Color.Yellow
            MessageEffectType.STAR -> Color.Yellow
            else -> Color.White
        }
        
        launch {
            particleSystem.emit(anchor, color, count = 50)
        }
        
        launch {
            while (particleSystem.isRunning) {
                particleSystem.update()
                delay(16.milliseconds)
            }
            onFinished()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            particleSystem.draw(this)
        }
    }
}
