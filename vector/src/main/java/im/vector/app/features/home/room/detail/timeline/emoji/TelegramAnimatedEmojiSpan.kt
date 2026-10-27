/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */
package im.vector.app.features.home.room.detail.timeline.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.style.ReplacementSpan
import android.widget.TextView
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * Draws Telegram's transparent animated-sticker representation of a standalone emoji inside
 * the Matrix timeline. All animations listed in TelegramEmojiText are supported; assets are
 * downloaded lazily from a pinned source and cached by TelegramEmojiText.
 *
 * An emoji plays once on the first display of a new message, stays on its final frame in old
 * history, and replays when tapped. A recycled view releases its callback to avoid leaking rows.
 */
internal class TelegramAnimatedEmojiSpan(
        context: Context,
        private val emoji: String,
        private val asset: TelegramEmojiText.AnimationAsset,
        private val onLoadFailure: (TextView) -> Unit,
) : ReplacementSpan(), Drawable.Callback {

    private val appContext = context.applicationContext
    private val drawable = LottieDrawable()
    private val sizePx = (context.resources.displayMetrics.density * EMOJI_SIZE_DP).toInt().coerceAtLeast(1)
    private var boundView: TextView? = null
    private var loadStarted = false
    private var compositionLoaded = false
    private var failed = false
    private var playWhenLoaded = false

    fun attach(textView: TextView, autoPlay: Boolean) {
        boundView = textView
        drawable.callback = this
        playWhenLoaded = autoPlay
        drawable.bounds = Rect(0, 0, sizePx, sizePx)
        if (failed) {
            onLoadFailure(textView)
            return
        }
        if (compositionLoaded) {
            if (autoPlay) replay() else textView.invalidate()
        } else if (!loadStarted) {
            loadStarted = true
            loadComposition(textView)
        }
    }

    fun replay() {
        if (failed) return
        val textView = boundView ?: return
        if (!compositionLoaded) {
            playWhenLoaded = true
            if (!loadStarted) {
                loadStarted = true
                loadComposition(textView)
            }
            return
        }
        drawable.cancelAnimation()
        drawable.repeatCount = 0
        drawable.progress = 0f
        drawable.playAnimation()
        textView.invalidate()
    }

    fun detach() {
        drawable.cancelAnimation()
        drawable.callback = null
        boundView = null
        playWhenLoaded = false
    }

    private fun loadComposition(viewAtRequest: TextView, startCandidateIndex: Int = 0) {
        decodeExecutor.execute {
            var json: String? = null
            var selectedPath: String? = null
            var selectedIndex = startCandidateIndex
            var lastFailure: Throwable? = null

            for (index in startCandidateIndex until asset.candidatePaths.size) {
                val path = asset.candidatePaths[index]
                try {
                    json = TelegramEmojiText.loadAnimationJson(appContext, path)
                    selectedPath = path
                    selectedIndex = index
                    break
                } catch (failure: Throwable) {
                    lastFailure = failure
                    Timber.w(failure, "Unable to load Telegram emoji asset $path")
                }
            }

            val readyJson = json
            val readyPath = selectedPath
            val readyIndex = selectedIndex
            if (readyJson == null || readyPath == null) {
                mainHandler.post {
                    if (boundView === viewAtRequest) {
                        fail(viewAtRequest, lastFailure ?: IllegalStateException("No animation asset was available for $emoji"))
                    }
                }
                return@execute
            }

            mainHandler.post {
                if (boundView !== viewAtRequest) return@post
                LottieCompositionFactory.fromJsonString(readyJson, "telegram-emoji-" + readyPath)
                        .addListener { composition ->
                            if (boundView !== viewAtRequest) return@addListener
                            drawable.composition = composition
                            drawable.repeatCount = 0
                            drawable.progress = 0f
                            drawable.bounds = Rect(0, 0, sizePx, sizePx)
                            compositionLoaded = true
                            viewAtRequest.invalidate()
                            if (playWhenLoaded) replay()
                        }
                        .addFailureListener { failure ->
                            if (boundView !== viewAtRequest) return@addFailureListener
                            if (readyIndex + 1 < asset.candidatePaths.size) {
                                loadComposition(viewAtRequest, readyIndex + 1)
                            } else {
                                fail(viewAtRequest, failure)
                            }
                        }
            }
        }
    }

    private fun fail(textView: TextView, failure: Throwable) {
        failed = true
        Timber.w(failure, "Unable to render Telegram animated emoji $emoji")
        drawable.cancelAnimation()
        drawable.callback = null
        onLoadFailure(textView)
    }

    override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?,
    ): Int {
        if (fm != null) {
            fm.ascent = -sizePx
            fm.descent = 0
            fm.top = fm.ascent
            fm.bottom = fm.descent
            fm.leading = 0
        }
        drawable.bounds = Rect(0, 0, sizePx, sizePx)
        return sizePx
    }

    override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint,
    ) {
        if (!compositionLoaded || failed) return
        canvas.save()
        canvas.translate(x, y - sizePx.toFloat())
        drawable.bounds = Rect(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        canvas.restore()
    }

    override fun invalidateDrawable(who: Drawable) {
        boundView?.invalidate()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, timeMillis: Long) {
        val target = boundView ?: return
        target.postDelayed(what, (timeMillis - SystemClock.uptimeMillis()).coerceAtLeast(0L))
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        boundView?.removeCallbacks(what)
    }

    companion object {
        private const val EMOJI_SIZE_DP = 78
        private val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "telegram-tgs-decode").apply { isDaemon = true }
        }
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
