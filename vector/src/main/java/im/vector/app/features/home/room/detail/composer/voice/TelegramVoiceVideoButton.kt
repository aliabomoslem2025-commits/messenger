/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.composer.voice

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible
import im.vector.app.R

/**
 * Telegram-style two-state capture control.
 *
 * Voice/video capture mode is represented by two overlapping vector icons. The state
 * transition crossfades and scales the incoming icon rather than rotating a static bitmap,
 * and the touch listener on this parent keeps the existing press-and-hold gesture intact.
 */
class TelegramVoiceVideoButton @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val voiceIcon = createIcon(R.drawable.ic_microphone)
    private val videoIcon = createIcon(R.drawable.ic_telegram_video_note)
    private var videoMode = false

    init {
        clipChildren = false
        clipToPadding = false
        addView(voiceIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        addView(videoIcon, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER))
        videoIcon.isVisible = false
    }

    private fun createIcon(resource: Int) = ImageView(context).apply {
        setImageResource(resource)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setCaptureMode(video: Boolean, animated: Boolean) {
        val incoming = if (video) videoIcon else voiceIcon
        val outgoing = if (video) voiceIcon else videoIcon
        videoMode = video

        incoming.animate().cancel()
        outgoing.animate().cancel()

        if (!animated) {
            incoming.isVisible = true
            incoming.alpha = 1f
            incoming.scaleX = 1f
            incoming.scaleY = 1f
            outgoing.isVisible = false
            outgoing.alpha = 0f
            outgoing.scaleX = 1f
            outgoing.scaleY = 1f
            return
        }

        incoming.isVisible = true
        incoming.alpha = 0f
        incoming.scaleX = 0.78f
        incoming.scaleY = 0.78f
        incoming.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(170L)
                .start()

        if (outgoing.isVisible) {
            outgoing.animate()
                    .alpha(0f)
                    .scaleX(0.82f)
                    .scaleY(0.82f)
                    .setDuration(135L)
                    .withEndAction {
                        outgoing.isVisible = false
                        outgoing.alpha = 0f
                        outgoing.scaleX = 1f
                        outgoing.scaleY = 1f
                    }
                    .start()
        }
    }

    fun isVideoMode(): Boolean = videoMode
}
