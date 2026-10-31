/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.composer.voice

import android.content.Context
import android.util.AttributeSet
import com.airbnb.lottie.LottieAnimationView
import im.vector.app.R

/**
 * Uses Telegram's original 60-frame voice/video icon composition.
 *
 * Frames 0..30 morph the video camera into the microphone; frames 30..60
 * morph the microphone back into the video camera. Keeping the same frame
 * ranges and endpoints makes the two mode transitions reversible.
 */
class TelegramVoiceVideoButton @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : LottieAnimationView(context, attrs, defStyleAttr) {

    private var videoMode = false

    init {
        setAnimation(R.raw.telegram_voice_and_video)
        repeatCount = 0
        setMinAndMaxFrame(0, VOICE_TO_VIDEO_FRAME)
        progress = 1f
    }

    fun setCaptureMode(video: Boolean, animated: Boolean) {
        if (videoMode == video) return
        videoMode = video
        cancelAnimation()
        repeatCount = 0
        if (video) {
            setMinAndMaxFrame(VOICE_TO_VIDEO_FRAME, ANIMATION_END_FRAME)
        } else {
            setMinAndMaxFrame(ANIMATION_START_FRAME, VOICE_TO_VIDEO_FRAME)
        }
        if (animated) {
            progress = 0f
            playAnimation()
        } else {
            progress = 1f
        }
    }

    companion object {
        private const val ANIMATION_START_FRAME = 0
        private const val VOICE_TO_VIDEO_FRAME = 30
        private const val ANIMATION_END_FRAME = 60
    }
}
