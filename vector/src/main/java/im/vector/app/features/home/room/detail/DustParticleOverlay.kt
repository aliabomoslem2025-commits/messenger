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
import android.util.Log

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
    private var sourceOriginalAlpha = 1f
    private var animation: ValueAnimator? = null
    private var completion: (() -> Unit)? = null
    private var originX = 0f
    private var originY = 0f
    private var snapshot: Bitmap? = null

    fun playFrom(source: View, onFinished: () -> Unit) {
        dispose()
        Log.i("DustDeleteOverlay", "playFrom attached=$isAttachedToWindow overlay=${width}x$height sourceAttached=${source.isAttachedToWindow} source=${source.width}x${source.height}")
        if (!isAttachedToWindow || width <= 0 || height <= 0 ||
                !source.isAttachedToWindow || source.width <= 0 || source.height <= 0) {
            Log.e("DustDeleteOverlay", "rejected invalid view bounds")
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
        } catch (_: OutOfMemoryError) {
            // Large media rows must never crash low-memory emulators during snapshot capture.
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
        sourceOriginalAlpha = source.alpha
        completion = onFinished
        createParticles(captured)
        Log.i("DustDeleteOverlay", "captured bitmap=${captured.width}x${captured.height} particles=${particles.size} origin=($originX,$originY)")
        if (particles.isEmpty()) {
            Log.e("DustDeleteOverlay", "snapshot produced no visible particles")
            finish(notify = true)
            return
        }
        source.alpha = 0f
        visibility = VISIBLE
        postInvalidateOnAnimation()
        Log.i("DustDeleteOverlay", "visible=${visibility == VISIBLE} attached=$isAttachedToWindow")

        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 760L
            interpolator = DecelerateInterpolator(0.72f)
            addUpdateListener { postInvalidateOnAnimation() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: Animator) {
                    Log.i("DustDeleteOverlay", "animator started duration=$duration")
                }

                override fun onAnimationEnd(animator: Animator) {
                    Log.i("DustDeleteOverlay", "animator ended")
                    finish(notify = true)
                }

                override fun onAnimationCancel(animator: Animator) {
                    Log.w("DustDeleteOverlay", "animator cancelled")
                    // Preserve the delete intent if the animation is interrupted.
                    finish(notify = true)
                }
            })
            start()
        }
    }

    private fun createParticles(bitmap: Bitmap) {
        particles.clear()

        // One particle per sampled cell, not one particle per source pixel.
        // The old implementation sampled every pixel despite computing a stride,
        // which could allocate millions of particles for a tall media bubble and
        // stall or OOM low-memory emulators.
        val maxParticles = 4_200
        val stride = maxOf(
                2,
                ceil(sqrt(bitmap.width.toDouble() * bitmap.height / maxParticles)).toInt()
        )
        val random = Random(bitmap.width * 31 + bitmap.height * 17)
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height / 2f

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val sampleX = (x + stride / 2).coerceAtMost(bitmap.width - 1)
                val sampleY = (y + stride / 2).coerceAtMost(bitmap.height - 1)
                val color = bitmap.getPixel(sampleX, sampleY)
                if ((color ushr 24) >= 36) {
                    val px = sampleX + (random.nextFloat() - 0.5f) * stride * 0.55f
                    val py = sampleY + (random.nextFloat() - 0.5f) * stride * 0.55f
                    val dx = px - centerX
                    val dy = py - centerY
                    val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                    val radial = 48f + random.nextFloat() * 112f
                    val tangent = (random.nextFloat() - 0.5f) * 86f
                    val velocityX = (
                            dx / length * radial - dy / length * tangent +
                                    (random.nextFloat() - 0.5f) * 36f
                            ) * density
                    val velocityY = (
                            dy / length * radial + dx / length * tangent -
                                    18f - random.nextFloat() * 54f
                            ) * density
                    val radius = (0.65f + random.nextFloat() * 1.35f) * density
                    particles.add(Particle(px, py, color, velocityX, velocityY, radius))
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
        sourceView?.alpha = sourceOriginalAlpha
        sourceView = null
        sourceOriginalAlpha = 1f
        snapshot?.takeUnless { it.isRecycled }?.recycle()
        snapshot = null
        particles.clear()
        // INVISIBLE preserves measured bounds; GONE would collapse the overlay to 0x0.
        visibility = View.INVISIBLE
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
