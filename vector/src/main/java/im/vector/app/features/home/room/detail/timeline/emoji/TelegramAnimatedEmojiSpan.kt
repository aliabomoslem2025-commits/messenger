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
import android.util.Base64
import android.util.Base64InputStream
import android.widget.TextView
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import timber.log.Timber

/**
 * Renders a real Telegram TGS/Lottie payload inline in the Matrix timeline TextView.
 *
 * Asset payloads are Base64 text containing the original gzip-compressed TGS JSON. Decode and
 * parse off the UI thread; the resulting animation keeps its transparent canvas and plays once.
 */
internal class TelegramAnimatedEmojiSpan(
        context: Context,
        private val emoji: String,
        private val assetFileName: String,
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

    private fun loadComposition(viewAtRequest: TextView) {
        decodeExecutor.execute {
            val json = runCatching {
                appContext.assets.open(ASSET_DIRECTORY + assetFileName).use { asset ->
                    Base64InputStream(asset, Base64.DEFAULT).use { decodedBase64 ->
                        GZIPInputStream(decodedBase64).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                }
            }.getOrElse { failure ->
                mainHandler.post {
                    if (boundView === viewAtRequest) fail(viewAtRequest, failure)
                }
                return@execute
            }

            mainHandler.post {
                if (boundView !== viewAtRequest) return@post
                LottieCompositionFactory.fromJsonString(json, "telegram-emoji-" + assetFileName)
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
                            if (boundView === viewAtRequest) fail(viewAtRequest, failure)
                        }
            }
        }
    }

    private fun fail(textView: TextView, failure: Throwable) {
        failed = true
        Timber.w(failure, "Unable to load Telegram animated emoji asset for " + emoji)
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
        private const val ASSET_DIRECTORY = "telegram_emoji/"
        private const val EMOJI_SIZE_DP = 78
        private val decodeExecutor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "telegram-tgs-decode").apply { isDaemon = true }
        }
        private val mainHandler = Handler(Looper.getMainLooper())
    }
}
