package com.matrixmessenger.core.animation.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.random.Random

/**
 * High-performance GPU-accelerated particle engine using Compose Canvas
 */
class ParticleSystem(
    private val maxParticles: Int = 100
) {
    private val particles = mutableStateListOf<Particle>()
    var isRunning by mutableStateOf(false)
        private set

    fun emit(
        position: Offset,
        color: Color,
        count: Int = 20,
        velocityRange: ClosedFloatingPointRange<Float> = 2f..8f
    ) {
        isRunning = true
        val random = Random(System.currentTimeMillis())
        repeat(count.coerceAtMost(maxParticles)) {
            val angle = random.nextFloat() * 2 * Math.PI
            val speed = velocityRange.start + random.nextFloat() * (velocityRange.endInclusive - velocityRange.start)
            
            particles.add(
                Particle(
                    position = position,
                    velocity = Offset(
                        (speed * Math.cos(angle)).toFloat(),
                        (speed * Math.sin(angle)).toFloat()
                    ),
                    color = color,
                    size = 4f + random.nextFloat() * 6f,
                    life = 1f,
                    decay = 0.01f + random.nextFloat() * 0.03f
                )
            )
        }
    }

    fun update() {
        if (particles.isEmpty()) {
            isRunning = false
            return
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.position += particle.velocity
            particle.velocity = Offset(particle.velocity.x * 0.98f, particle.velocity.y + 0.1f) // Gravity
            particle.life -= particle.decay
            
            if (particle.life <= 0) {
                iterator.remove()
            }
        }
    }

    fun draw(drawScope: DrawScope) {
        particles.forEach { particle ->
            drawScope.drawCircle(
                color = particle.color.copy(alpha = particle.life),
                radius = particle.size,
                center = particle.position
            )
        }
    }
}

private class Particle(
    var position: Offset,
    var velocity: Offset,
    val color: Color,
    val size: Float,
    var life: Float,
    val decay: Float
)
