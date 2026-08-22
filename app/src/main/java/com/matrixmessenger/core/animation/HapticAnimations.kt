package com.matrixmessenger.core.animation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Haptic feedback engine for messenger interactions.
 * Provides Telegram-like tactile feedback for sending, reacting, and deleting.
 */
class HapticFeedbackManager(context: Context) {
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /**
     * Light impact for typing or small toggles.
     */
    fun impactLight() {
        vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Medium impact for sending messages or selecting reactions.
     */
    fun impactMedium() {
        vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Heavy impact for destructive actions (Delete, Block).
     */
    fun impactHeavy() {
        vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Success pattern (double pulse).
     */
    fun success() {
        val pattern = longArrayOf(0, 10, 80, 10)
        val amplitudes = intArrayOf(0, 100, 0, 150)
        vibrate(VibrationEffect.createWaveform(pattern, amplitudes, -1))
    }

    private fun vibrate(effect: VibrationEffect) {
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(effect)
        }
    }
}

@Composable
fun rememberHapticManager(): HapticFeedbackManager {
    val context = LocalContext.current
    return remember(context) { HapticFeedbackManager(context) }
}
