package com.matrixmessenger.core.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.matrixmessenger.core.animation.motion.MatrixMotion
import com.matrixmessenger.core.animation.motion.MatrixSpringSpecs
import kotlinx.coroutines.launch

enum class StickerEmotion {
    HAPPY, LAUGH, CRY, LOVE, ANGRY, SURPRISE
}

/**
 * High-performance sticker renderer supporting Lottie and Emotion-based motion.
 */
@Composable
fun MatrixSticker(
    modifier: Modifier = Modifier,
    lottieResId: Int? = null,
    emotion: StickerEmotion = StickerEmotion.HAPPY,
    isVisible: Boolean = true
) {
    val composition by rememberLottieComposition(
        spec = if (lottieResId != null) LottieCompositionSpec.RawRes(lottieResId) else LottieCompositionSpec.JsonString("")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = isVisible
    )

    StickerEmotionAnimation(
        emotion = emotion,
        isVisible = isVisible
    ) { animationModifier ->
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier
                .then(animationModifier)
                .size(160.dp)
        )
    }
}

/**
 * Specialized motion engine for animated stickers.
 */
@Composable
fun StickerEmotionAnimation(
    emotion: StickerEmotion,
    isVisible: Boolean,
    content: @Composable (Modifier) -> Unit
) {
    if (!isVisible) {
        content(Modifier)
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "StickerLoop")
    
    val modifier = when (emotion) {
        StickerEmotion.HAPPY -> {
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "HappyScale"
            )
            Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
        }
        StickerEmotion.LAUGH -> {
            val rotation by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "LaughRotate"
            )
            Modifier.graphicsLayer(rotationZ = rotation)
        }
        StickerEmotion.LOVE -> {
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "LovePulse"
            )
            Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
        }
        StickerEmotion.ANGRY -> {
            val translationX by infiniteTransition.animateFloat(
                initialValue = -2f,
                targetValue = 2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(50, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "AngryShake"
            )
            Modifier.graphicsLayer(translationX = translationX)
        }
        StickerEmotion.SURPRISE -> {
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(300, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "SurpriseZoom"
            )
            Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
        }
        else -> Modifier
    }

    content(modifier)
}

/**
 * Fly-in animation when a sticker is sent.
 */
@Composable
fun StickerSendAnimation(
    content: @Composable (Modifier) -> Unit
) {
    val scale = remember { Animatable(0.3f) }
    val alpha = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f, MatrixMotion.Fast) }
            launch {
                scale.animateTo(1.15f, MatrixMotion.Fast)
                scale.animateTo(1f, MatrixSpringSpecs.Bouncy)
            }
        }
    }
    
    content(
        Modifier.graphicsLayer(
            scaleX = scale.value,
            scaleY = scale.value,
            alpha = alpha.value
        )
    )
}
