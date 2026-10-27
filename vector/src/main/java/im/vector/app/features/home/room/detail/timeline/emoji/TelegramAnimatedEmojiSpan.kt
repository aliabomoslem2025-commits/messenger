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
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.GZIPInputStream
import timber.log.Timber

/**
 * Renders Telegram TGS animated emoji in the Matrix timeline as a transparent inline sticker.
 *
 * The manifest is bundled, while each original TGS payload is fetched on first use and stored in
 * the app's private cache. The first three legacy assets remain bundled as an offline bootstrap.
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
            val json = runCatching { loadAnimationJson(assetFileName) }.getOrElse { failure ->
                mainHandler.post {
                    if (boundView === viewAtRequest) fail(viewAtRequest, failure)
                }
                return@execute
            }

            mainHandler.post {
                if (boundView !== viewAtRequest) return@post
                val cachedComposition = compositions[assetFileName]
                if (cachedComposition != null) {
                    applyComposition(cachedComposition, viewAtRequest)
                    return@post
                }
                LottieCompositionFactory.fromJsonString(json, "telegram-emoji-" + assetFileName)
                        .addListener { composition ->
                            if (boundView !== viewAtRequest) return@addListener
                            compositions.putIfAbsent(assetFileName, composition)
                            applyComposition(compositions[assetFileName] ?: composition, viewAtRequest)
                        }
                        .addFailureListener { failure ->
                            if (boundView === viewAtRequest) fail(viewAtRequest, failure)
                        }
            }
        }
    }

    private fun applyComposition(composition: LottieComposition, target: TextView) {
        if (boundView !== target) return
        drawable.composition = composition
        drawable.repeatCount = 0
        drawable.progress = 0f
        drawable.bounds = Rect(0, 0, sizePx, sizePx)
        compositionLoaded = true
        target.invalidate()
        if (playWhenLoaded) replay()
    }

    /**
     * Prefer the legacy bundled copy for the three bootstrap emojis, then use the disk cache,
     * and finally download the official-set TGS asset from a pinned public source path.
     */
    private fun loadAnimationJson(fileName: String): String {
        require(isSafeTgsName(fileName)) { "Invalid Telegram TGS file name" }

        val bundledBase64 = runCatching {
            appContext.assets.open(LEGACY_ASSET_DIRECTORY + fileName + ".b64")
        }.getOrNull()
        if (bundledBase64 != null) {
            return bundledBase64.use { encoded ->
                Base64InputStream(encoded, Base64.DEFAULT).use { decoded ->
                    GZIPInputStream(decoded).use { gzip -> readUtf8Bounded(gzip) }
                }
            }
        }

        return synchronized(fileName.intern()) {
            val cacheDirectory = File(appContext.cacheDir, CACHE_DIRECTORY).apply {
                if (!exists() && !mkdirs()) throw IOException("Cannot create TGS cache directory")
            }
            val cacheFile = File(cacheDirectory, fileName)
            if (cacheFile.isFile && cacheFile.length() in 3..MAX_COMPRESSED_TGS_BYTES.toLong()) {
                val cachedJson = runCatching {
                    FileInputStream(cacheFile).use { raw ->
                        GZIPInputStream(raw).use { gzip -> readUtf8Bounded(gzip) }
                    }
                }.getOrNull()
                if (cachedJson != null) return@synchronized cachedJson
                cacheFile.delete()
            }

            downloadTgsToCache(fileName, cacheFile)
            FileInputStream(cacheFile).use { raw ->
                GZIPInputStream(raw).use { gzip -> readUtf8Bounded(gzip) }
            }
        }
    }

    private fun downloadTgsToCache(fileName: String, destination: File) {
        val url = URL(TGS_BASE_URL + fileName)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "TeleMatrix-AnimatedEmoji/1.0")
            setRequestProperty("Accept", "application/octet-stream")
        }
        val temporary = File(destination.parentFile, "$fileName.part")
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("TGS server returned HTTP $status for $fileName")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_COMPRESSED_TGS_BYTES) {
                throw IOException("TGS asset exceeds size limit: $fileName")
            }
            var totalBytes = 0L
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_COMPRESSED_TGS_BYTES) {
                            throw IOException("TGS download exceeded size limit: $fileName")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (totalBytes < 3L) throw IOException("Empty TGS payload for $fileName")
            FileInputStream(temporary).use { input ->
                if (input.read() != GZIP_MAGIC_1 || input.read() != GZIP_MAGIC_2) {
                    throw IOException("Downloaded asset is not gzip-compressed TGS: $fileName")
                }
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("Cannot replace cached TGS file: $fileName")
            }
            if (!temporary.renameTo(destination)) {
                FileInputStream(temporary).use { input ->
                    FileOutputStream(destination).use { output -> input.copyTo(output) }
                }
                temporary.delete()
            }
            trimCache(destination.parentFile)
        } finally {
            connection.disconnect()
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun trimCache(cacheDirectory: File?) {
        if (cacheDirectory == null) return
        val files = cacheDirectory.listFiles()?.filter { it.isFile && it.extension == "tgs" } ?: return
        var size = files.sumOf { it.length() }
        if (size <= MAX_DISK_CACHE_BYTES) return
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (size <= MAX_DISK_CACHE_BYTES) return
            val length = file.length()
            if (file.delete()) size -= length
        }
    }

    private fun readUtf8Bounded(input: java.io.InputStream): String {
        val builder = StringBuilder()
        val reader = input.bufferedReader(Charsets.UTF_8)
        val buffer = CharArray(8 * 1024)
        var countTotal = 0
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            countTotal += count
            if (countTotal > MAX_DECOMPRESSED_JSON_CHARS) {
                throw IOException("Decompressed TGS JSON exceeds size limit")
            }
            builder.append(buffer, 0, count)
        }
        return builder.toString()
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

    private fun isSafeTgsName(fileName: String): Boolean {
        return fileName.endsWith(".tgs") && fileName.isNotBlank() &&
                fileName.all { it.isLetterOrDigit() || it == '_' || it == '+' || it == '.' || it == '-' }
    }

    companion object {
        private const val LEGACY_ASSET_DIRECTORY = "telegram_emoji/"
        private const val CACHE_DIRECTORY = "telegram_tgs"
        private const val TGS_BASE_URL = "https://raw.githubusercontent.com/saeedtahmtan/telemoji/main/tgs/animated/"
        private const val EMOJI_SIZE_DP = 78
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_COMPRESSED_TGS_BYTES = 512 * 1024
        private const val MAX_DECOMPRESSED_JSON_CHARS = 4 * 1024 * 1024
        private const val MAX_DISK_CACHE_BYTES = 24L * 1024 * 1024
        private const val GZIP_MAGIC_1 = 0x1f
        private const val GZIP_MAGIC_2 = 0x8b

        private val decodeExecutor = Executors.newFixedThreadPool(3) { runnable ->
            Thread(runnable, "telegram-tgs-decode").apply { isDaemon = true }
        }
        private val mainHandler = Handler(Looper.getMainLooper())
        private val compositions = ConcurrentHashMap<String, LottieComposition>()
    }
}
