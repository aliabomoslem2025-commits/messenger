package com.matrixmessenger.core.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matrixmessenger.core.animation.motion.MatrixSpringSpecs

/**
 * Animated large emoji for single-character messages.
 * Each supported emoji has a unique Telegram-style loop.
 */
@Composable
fun AnimatedLargeEmoji(
    emoji: String,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.5f) }
    val infiniteTransition = rememberInfiniteTransition(label = "EmojiLoop")
    
    LaunchedEffect(emoji) {
        scale.animateTo(1f, MatrixSpringSpecs.Emotional)
    }

    val animationModifier = when (emoji) {
        "❤️" -> {
            val pulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "HeartPulse"
            )
            Modifier.graphicsLayer(scaleX = pulse, scaleY = pulse)
        }
        "😂" -> {
            val shake by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "LaughShake"
            )
            Modifier.graphicsLayer(rotationZ = shake)
        }
        "🔥" -> {
            val offset by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -10f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "FireRise"
            )
            Modifier.graphicsLayer(translationY = offset)
        }
        "👍" -> {
            val bounce by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "ThumbsUpBounce"
            )
            Modifier.graphicsLayer(scaleX = bounce, scaleY = bounce)
        }
        else -> Modifier
    }

    Text(
        text = emoji,
        fontSize = 72.sp,
        modifier = modifier
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value
            )
            .then(animationModifier)
    )
}
