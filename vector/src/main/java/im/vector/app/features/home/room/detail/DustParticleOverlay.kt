/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Renders a short-lived dust disintegration of a real timeline row snapshot.
 *
 * The fragment owns the Matrix delete transaction; this view only renders the
 * captured pixels. Sampling is bounded to keep large media rows inexpensive.
 */
internal class DustParticleOverlay(context: Context) : View(context) {

    private data class Particle(
            val x: Float,
            val y: Float,
            val color: Int,
            val velocityX: Float,
            val velocityY: Float,
            val radius: Float
    )

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particles = ArrayList<Particle>()
    private var sourceView: View? = null
    private var animation: ValueAnimator? = null
    private var completion: (() -> Unit)? = null
    private var originX = 0f
    private var originY = 0f
    private var snapshot: Bitmap? = null

    fun playFrom(source: View, onFinished: () -> Unit) {
        dispose()
        if (!isAttachedToWindow || width <= 0 || height <= 0 ||
                !source.isAttachedToWindow || source.width <= 0 || source.height <= 0) {
            onFinished()
            return
        }

        val captured = try {
            Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also {
                source.draw(Canvas(it))
            }
        } catch (_: RuntimeException) {
            onFinished()
            return
        }

        val sourceLocation = IntArray(2)
        val overlayLocation = IntArray(2)
        source.getLocationOnScreen(sourceLocation)
        getLocationOnScreen(overlayLocation)
        originX = (sourceLocation[0] - overlayLocation[0]).toFloat()
        originY = (sourceLocation[1] - overlayLocation[1]).toFloat()
        snapshot = captured
        sourceView = source
        completion = onFinished
        createParticles(captured)
        source.alpha = 0f
        visibility = VISIBLE

        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 760L
            interpolator = DecelerateInterpolator(0.72f)
            addUpdateListener { invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    finish(notify = true)
                }

                override fun onAnimationCancel(animator: Animator) {
                    finish(notify = false)
                }
            })
            start()
        }
    }

    private fun createParticles(bitmap: Bitmap) {
        particles.clear()
        val maxParticles = 4_800
        val stride = maxOf(2, ceil(sqrt(bitmap.width.toDouble() * bitmap.height / maxParticles)).toInt())
        val random = Random(bitmap.width * 31 + bitmap.height * 17)
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f
        val colors = IntArray(stride * stride)

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val sampleWidth = minOf(stride, bitmap.width - x)
                val sampleHeight = minOf(stride, bitmap.height - y)
                bitmap.getPixels(colors, 0, stride, x, y, sampleWidth, sampleHeight)
                for (row in 0 until sampleHeight) {
                    for (column in 0 until sampleWidth) {
                        val color = colors[row * stride + column]
                        if ((color ushr 24) < 36) continue
                        val px = x + column + random.nextFloat() * 0.8f
                        val py = y + row + random.nextFloat() * 0.8f
                        val dx = px - centerX
                        val dy = py - centerY
                        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        val radial = 42f + random.nextFloat() * 92f
                        val velocityX = (dx / length * radial + (random.nextFloat() - 0.5f) * 74f) * density
                        val velocityY = (dy / length * radial - random.nextFloat() * 62f) * density
                        val radius = (0.55f + random.nextFloat() * 1.15f) * density
                        particles.add(Particle(px, py, color, velocityX, velocityY, radius))
                    }
                }
                x += stride
            }
            y += stride
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val progress = animation?.animatedValue as? Float ?: return
        val elapsed = progress * 0.76f
        val fade = (1f - progress).coerceAtLeast(0f)
        particles.forEach { particle ->
            val alpha = ((particle.color ushr 24) * fade * fade).toInt().coerceIn(0, 255)
            if (alpha == 0) return@forEach
            paint.color = (alpha shl 24) or (particle.color and 0x00FFFFFF)
            val x = originX + particle.x + particle.velocityX * elapsed
            val y = originY + particle.y + particle.velocityY * elapsed +
                    0.5f * 115f * density * elapsed * elapsed
            canvas.drawCircle(x, y, particle.radius * (1f - progress * 0.35f), paint)
        }
    }

    private fun finish(notify: Boolean) {
        animation?.removeAllUpdateListeners()
        animation?.removeAllListeners()
        animation = null
        sourceView?.alpha = 1f
        sourceView = null
        snapshot?.takeUnless { it.isRecycled }?.recycle()
        snapshot = null
        particles.clear()
        visibility = GONE
        val callback = completion
        completion = null
        if (notify) callback?.invoke()
    }

    fun dispose() {
        animation?.cancel()
        if (animation == null) finish(notify = false)
    }

    override fun onDetachedFromWindow() {
        dispose()
        super.onDetachedFromWindow()
    }
}
