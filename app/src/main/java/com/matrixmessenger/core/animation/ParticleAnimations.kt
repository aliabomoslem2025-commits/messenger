package com.matrixmessenger.core.animation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.matrixmessenger.core.animation.engine.ParticleSystem
import kotlinx.coroutines.delay

/**
 * Reusable particle effect Composable.
 */
@Composable
fun ParticleEffect(
    emitterPosition: Offset,
    color: Color,
    trigger: Any?,
    modifier: Modifier = Modifier,
    particleCount: Int = 30
) {
    val particleSystem = remember { ParticleSystem(maxParticles = 100) }

    LaunchedEffect(trigger) {
        if (trigger != null) {
            particleSystem.emit(emitterPosition, color, count = particleCount)
            while (particleSystem.isRunning) {
                particleSystem.update()
                delay(16)
            }
        }
    }

    if (particleSystem.isRunning) {
        Canvas(modifier = modifier.fillMaxSize()) {
            particleSystem.draw(this)
        }
    }
}
