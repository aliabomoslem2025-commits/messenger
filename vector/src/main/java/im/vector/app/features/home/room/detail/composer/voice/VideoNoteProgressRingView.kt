/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.composer.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withSave

/**
 * Thin circular recording progress used around the round-video preview.
 * Drawing is independent of the preview Surface, so it remains crisp while the
 * camera feed is rendered by TextureView underneath.
 */
class VideoNoteProgressRingView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0x66FFFFFF
        strokeCap = Paint.Cap.ROUND
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFFFFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val arcBounds = RectF()

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = progressPaint.strokeWidth / 2f + 1f * density
        arcBounds.set(inset, inset, width - inset, height - inset)
        if (arcBounds.width() <= 0f || arcBounds.height() <= 0f) return
        canvas.withSave {
            drawArc(arcBounds, -90f, 360f, false, trackPaint)
            if (progress > 0f) {
                drawArc(arcBounds, -90f, progress * 360f, false, progressPaint)
            }
        }
    }
}
