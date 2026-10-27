/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */
package im.vector.app.features.home.room.detail.timeline.emoji

import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.style.ReplacementSpan
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import timber.log.Timber

/**
 * Draws Telegram's transparent animated-sticker representation of a standalone emoji inside
 * the Matrix timeline. On Android 9+ it prefers Telegram's RLottie-rendered animated WebP for
 * fidelity; the original TGS-derived Lottie JSON is the compatibility fallback.
 *
 * An emoji plays once on a new message, rests on its final frame in old history, and replays
 * when tapped. The source catalog includes all 599 animation records and assets are cached
 * in app-private storage after first use. RecyclerView/Epoxy recycling releases drawable callbacks.
 */
internal class TelegramAnimatedEmojiSpan(
        context: Context,
        private val emoji: String,
        private val asset: TelegramEmojiText.AnimationAsset,
        private val onLoadFailure: (TextView) -> Unit,
) : ReplacementSpan(), Drawable.Callback {

    private val appContext = context.applicationContext
    private val lottieDrawable = LottieDrawable()
    private var activeDrawable: Drawable = lottieDrawable
    private val sizePx = (context.resources.displayMetrics.density * EMOJI_SIZE_DP).toInt().coerceAtLeast(1)
    private var boundView: TextView? = null
    private var loadStarted = false
    private var compositionLoaded = false
    private var failed = false
    private var playWhenLoaded = false

    fun attach(textView: TextView, autoPlay: Boolean) {
        boundView = textView
        activeDrawable.callback = this
        playWhenLoaded = autoPlay
        activeDrawable.bounds = Rect(0, 0, sizePx, sizePx)
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

        when (val renderer = activeDrawable) {
            is LottieDrawable -> {
                renderer.cancelAnimation()
                renderer.repeatCount = 0
                renderer.progress = 0f
                renderer.playAnimation()
            }
            is Animatable -> {
                renderer.stop()
                renderer.start()
            }
        }
        textView.invalidate()
    }

    fun detach() {
        when (val renderer = activeDrawable) {
            is LottieDrawable -> renderer.cancelAnimation()
            is Animatable -> renderer.stop()
        }
        activeDrawable.callback = null
        lottieDrawable.callback = null
        boundView = null
        playWhenLoaded = false
    }

    /**
     * Prefer the image rendered from Telegram's TGS source by rlottie. If the platform cannot
     * decode an animated WebP or the CDN is unavailable, load the Lottie JSON asset instead.
     */
    private fun loadComposition(viewAtRequest: TextView) {
        decodeExecutor.execute {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && tryLoadAnimatedWebp(viewAtRequest)) {
                return@execute
            }
            loadLottieComposition(viewAtRequest, 0)
        }
    }

    private fun tryLoadAnimatedWebp(viewAtRequest: TextView): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        for (path in asset.candidatePaths) {
            try {
                val webpFile = TelegramEmojiText.loadAnimationWebpFile(appContext, path)
                val renderer = decodeAnimatedWebp(webpFile)
                mainHandler.post {
                    if (boundView !== viewAtRequest) {
                        (renderer as? Animatable)?.stop()
                        return@post
                    }
                    lottieDrawable.cancelAnimation()
                    lottieDrawable.callback = null
                    activeDrawable = renderer
                    activeDrawable.callback = this
                    activeDrawable.bounds = Rect(0, 0, sizePx, sizePx)
                    compositionLoaded = true
                    viewAtRequest.invalidate()
                    if (playWhenLoaded) replay()
                }
                return true
            } catch (failure: Exception) {
                Timber.w(failure, "Animated WebP unavailable for Telegram emoji $emoji ($path); trying fallback")
            }
        }
        return false
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeAnimatedWebp(file: File): Drawable {
        val decoded = ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
        if (decoded !is AnimatedImageDrawable) {
            throw IOException("Telegram emoji WebP is not animated")
        }
        decoded.repeatCount = 0
        return decoded
    }

    private fun loadLottieComposition(viewAtRequest: TextView, startCandidateIndex: Int) {
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
                } catch (failure: Exception) {
                    lastFailure = failure
                    Timber.w(failure, "Unable to load Telegram emoji Lottie asset $path")
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
                activeDrawable.callback = null
                activeDrawable = lottieDrawable
                lottieDrawable.callback = this
                LottieCompositionFactory.fromJsonString(readyJson, "telegram-emoji-" + readyPath)
                        .addListener { composition ->
                            if (boundView !== viewAtRequest) return@addListener
                            lottieDrawable.composition = composition
                            lottieDrawable.repeatCount = 0
                            lottieDrawable.progress = 0f
                            lottieDrawable.bounds = Rect(0, 0, sizePx, sizePx)
                            activeDrawable = lottieDrawable
                            activeDrawable.callback = this
                            compositionLoaded = true
                            viewAtRequest.invalidate()
                            if (playWhenLoaded) replay()
                        }
                        .addFailureListener { failure ->
                            if (boundView !== viewAtRequest) return@addFailureListener
                            if (readyIndex + 1 < asset.candidatePaths.size) {
                                loadLottieComposition(viewAtRequest, readyIndex + 1)
                            } else {
                                fail(viewAtRequest, failure)
                            }
                        }
            }
        }
    }

    private fun fail(textView: TextView, failure: Throwable) {
        failed = true
        compositionLoaded = false
        when (val renderer = activeDrawable) {
            is LottieDrawable -> renderer.cancelAnimation()
            is Animatable -> renderer.stop()
        }
        activeDrawable.callback = null
        Timber.w(failure, "Unable to render Telegram animated emoji $emoji")
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
        activeDrawable.bounds = Rect(0, 0, sizePx, sizePx)
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
        if (failed) return
        if (!compositionLoaded) {
            // Keep the system Unicode emoji visible while the remote animation is being
            // fetched or when a first-time asset cannot be reached offline.
            canvas.drawText(text.subSequence(start, end).toString(), x, y.toFloat(), paint)
            return
        }
        canvas.save()
        canvas.translate(x, y - sizePx.toFloat())
        activeDrawable.bounds = Rect(0, 0, sizePx, sizePx)
        activeDrawable.draw(canvas)
        canvas.restore()
    }

    override fun invalidateDrawable(who: Drawable) {
        if (who === activeDrawable) boundView?.invalidate()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, timeMillis: Long) {
        if (who !== activeDrawable) return
        val target = boundView ?: return
        target.postDelayed(what, (timeMillis - SystemClock.uptimeMillis()).coerceAtLeast(0L))
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        if (who === activeDrawable) boundView?.removeCallbacks(what)
    }

    companion object {
        private const val EMOJI_SIZE_DP = 78
        private val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "telegram-tgs-decode").apply { isDaemon = true }
        }
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
