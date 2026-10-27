/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */
package im.vector.app.features.home.room.detail.timeline.emoji

import android.content.Context
import android.util.Base64
import android.util.Base64InputStream
import android.util.Log
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.net.ssl.HttpsURLConnection

/**
 * Catalog and loader for Telegram's supported animated-message emoji.
 *
 * The catalog contains every animation record in the pinned source snapshot (599 assets,
 * including alternate variants). Assets are fetched lazily from that immutable revision and
 * cached in app-private storage, so an emoji that has been viewed once can replay offline.
 */
object TelegramEmojiText {
    internal data class AnimationAsset(
            val emoji: String,
            val candidatePaths: List<String>,
    )

    private const val TAG = "TelegramEmojiText"
    private const val MANIFEST_FILE = "telegram_animated_emoji_manifest.json"
    private const val CACHE_DIRECTORY = "telegram_emoji_cache"
    private const val SOURCE_REVISION = "4f45fca016c71de9d2a9e15c07ae1fe3d8327a28"
    private const val REMOTE_BASE_URL = "https://raw.githubusercontent.com/saeedtahmtan/telemoji/$SOURCE_REVISION/"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_COMPRESSED_BYTES = 512 * 1024
    private const val MAX_JSON_BYTES = 2 * 1024 * 1024
    private const val MAX_WEBP_BYTES = 8 * 1024 * 1024

    private val manifestLock = Any()

    @Volatile
    private var cachedAnimations: Map<String, List<String>>? = null

    private val bundledAssetsByPath = mapOf(
            "lottie/animated/U+1F600_1.json" to "U+1F600_1.tgs.b64",
            "lottie/animated/U+1F602_1.json" to "U+1F602_1.tgs.b64",
            "lottie/animated/U+1F923_1.json" to "U+1F923_1.tgs.b64",
    )

    internal fun animationAsset(context: Context, rawEmoji: String): AnimationAsset? {
        val key = normalizeEmoji(rawEmoji)
        if (key.isEmpty()) return null
        val candidates = loadAnimationCatalog(context)[key] ?: return null
        return AnimationAsset(rawEmoji, candidates)
    }

    internal fun isBundledAsset(path: String): String? = bundledAssetsByPath[path]

    /**
     * Loads TGS-backed Bodymovin JSON from a small built-in fast path, persistent cache,
     * or the pinned HTTPS source. Source paths come only from the bundled manifest.
     */
    internal fun loadAnimationJson(context: Context, path: String): String {
        require(isAllowedAssetPath(path)) { "Unsupported Telegram emoji asset path" }

        bundledAssetsByPath[path]?.let { bundledName ->
            val json = context.applicationContext.assets.open("telegram_emoji/$bundledName").use { asset ->
                Base64InputStream(asset, Base64.DEFAULT).use { base64 ->
                    GZIPInputStream(base64).use { gzip -> readUtf8Limited(gzip, MAX_JSON_BYTES) }
                }
            }
            return validateLottieJson(json)
        }

        val appContext = context.applicationContext
        val cacheFile = cacheFile(appContext, path)
        if (cacheFile.isFile) {
            val cached = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
            if (cached != null && isValidLottieJson(cached)) return cached
            runCatching { cacheFile.delete() }
        }

        val connection = URL(REMOTE_BASE_URL + path).openConnection() as HttpsURLConnection
        val json: String
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/json, application/octet-stream")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "Messenger-Telegram-Emoji/1.0")

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("Emoji asset request failed with HTTP $status")
            }
            val length = connection.contentLength
            if (length > MAX_COMPRESSED_BYTES) {
                throw IOException("Emoji asset exceeds compressed size limit")
            }
            val payload = connection.inputStream.use { readBytesLimited(it, MAX_COMPRESSED_BYTES) }
            json = decodePayload(payload)
        } finally {
            connection.disconnect()
        }

        // Cache JSON atomically in app-private storage; incomplete downloads are never reused.
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val temporary = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            temporary.writeText(json, Charsets.UTF_8)
            if (!temporary.renameTo(cacheFile)) {
                temporary.copyTo(cacheFile, overwrite = true)
                temporary.delete()
            }
        }.onFailure { Log.w(TAG, "Unable to cache emoji asset $path", it) }

        return json
    }

    /**
     * Downloads the RLottie-rendered animated WebP variant where available. Modern Android
     * decodes animated WebP natively; this is closer to Telegram's RLottie output than a
     * generic Lottie renderer. The Lottie JSON path remains the compatibility fallback.
     */
    internal fun loadAnimationWebpFile(context: Context, lottiePath: String): File {
        require(isAllowedAssetPath(lottiePath)) { "Unsupported Telegram emoji asset path" }
        val webpPath = lottiePath
                .replaceFirst("lottie/animated/", "webp/animated/")
                .removeSuffix(".json") + ".webp"
        val appContext = context.applicationContext
        val cachedFile = cacheFile(appContext, webpPath, "webp")
        if (cachedFile.isFile && cachedFile.length() in 12..MAX_WEBP_BYTES.toLong()) {
            return cachedFile
        }
        runCatching { cachedFile.delete() }

        val connection = URL(
                "https://media.githubusercontent.com/media/saeedtahmtan/telemoji/$SOURCE_REVISION/$webpPath"
        ).openConnection() as HttpsURLConnection
        val payload: ByteArray
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "image/webp, application/octet-stream")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", "Messenger-Telegram-Emoji/1.0")

            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) {
                throw IOException("Emoji WebP request failed with HTTP $status")
            }
            if (connection.contentLength > MAX_WEBP_BYTES) {
                throw IOException("Emoji WebP exceeds size limit")
            }
            payload = connection.inputStream.use { readBytesLimited(it, MAX_WEBP_BYTES) }
        } finally {
            connection.disconnect()
        }
        if (!isWebpPayload(payload)) throw IOException("Invalid Telegram emoji WebP payload")

        cachedFile.parentFile?.mkdirs()
        val temporary = File(cachedFile.parentFile, cachedFile.name + ".tmp")
        temporary.writeBytes(payload)
        if (!temporary.renameTo(cachedFile)) {
            temporary.copyTo(cachedFile, overwrite = true)
            temporary.delete()
        }
        return cachedFile
    }

    private fun isWebpPayload(bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        return bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() &&
                bytes[11] == 'P'.code.toByte()
    }

    /**
     * Returns true only when the trimmed body contains one emoji grapheme. Handles variation
     * selectors, skin tones, paired regional indicators, keycaps and ZWJ emoji sequences.
     */
    fun isSingleEmoji(rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return false

        val points = ArrayList<Int>()
        var index = 0
        while (index < text.length) {
            val point = Character.codePointAt(text, index)
            points.add(point)
            index += Character.charCount(point)
        }
        if (points.isEmpty()) return false

        // Keycap emoji (for example 1️⃣, #️⃣ and *️⃣).
        if (points.last() == KEYCAP) {
            val visible = points.filterNot { isVariationSelector(it) }
            return visible.size == 2 &&
                    (visible[0] == '#'.code || visible[0] == '*'.code || visible[0] in '0'.code..'9'.code) &&
                    visible[1] == KEYCAP
        }

        var emojiClusters = 0
        var regionalIndicatorCount = 0
        var expectsJoinedEmoji = false
        for (point in points) {
            when {
                point == ZERO_WIDTH_JOINER -> {
                    if (emojiClusters == 0 || expectsJoinedEmoji) return false
                    expectsJoinedEmoji = true
                }
                isVariationSelector(point) || isSkinToneModifier(point) ||
                        point == KEYCAP || point in TAG_CHARACTERS -> Unit
                point in REGIONAL_INDICATORS -> {
                    if (regionalIndicatorCount == 0) emojiClusters++
                    regionalIndicatorCount++
                    if (regionalIndicatorCount == 2) regionalIndicatorCount = 0
                    expectsJoinedEmoji = false
                }
                isEmojiBase(point) -> {
                    if (!expectsJoinedEmoji) emojiClusters++
                    expectsJoinedEmoji = false
                    regionalIndicatorCount = 0
                }
                else -> return false
            }
        }
        return emojiClusters == 1 && !expectsJoinedEmoji
    }

    private fun loadAnimationCatalog(context: Context): Map<String, List<String>> {
        cachedAnimations?.let { return it }
        return synchronized(manifestLock) {
            cachedAnimations?.let { return@synchronized it }
            val catalog = runCatching {
                val root = context.applicationContext.assets.open(MANIFEST_FILE).bufferedReader(Charsets.UTF_8).use {
                    JSONObject(it.readText())
                }
                val rows = root.getJSONArray("animations")
                val pathsByEmoji = LinkedHashMap<String, MutableList<Pair<Int, String>>>()
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val emoji = normalizeEmoji(row.getString("emoji"))
                    val path = row.getString("path")
                    val variant = row.optInt("variant", 1)
                    if (emoji.isEmpty() || !isAllowedAssetPath(path)) continue
                    pathsByEmoji.getOrPut(emoji) { mutableListOf() }.add(variant to path)
                }
                pathsByEmoji.mapValues { (_, variants) ->
                    variants.sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
                            .map { it.second }
                            .distinct()
                }
            }.getOrElse { failure ->
                Log.e(TAG, "Unable to read Telegram animated emoji catalog", failure)
                emptyMap()
            }
            if (catalog.isEmpty()) {
                Log.w(TAG, "Telegram animated emoji catalog is empty; Unicode emoji fallback will be used")
            } else {
                Log.i(TAG, "Loaded $ANIMATION_RECORD_COUNT animation records for ${catalog.size} normalized emoji keys")
            }
            cachedAnimations = catalog
            catalog
        }
    }

    private fun normalizeEmoji(text: String): String {
        // Telegram maps emoji presentations with or without VS-16 to the same sticker.
        return text.trim().replace("\uFE0E", "").replace("\uFE0F", "")
    }

    private fun isAllowedAssetPath(path: String): Boolean {
        return path.startsWith("lottie/animated/") &&
                path.endsWith(".json") &&
                !path.contains("..") &&
                path.all { it.isLetterOrDigit() || it in "/_+.-" }
    }

    private fun cacheFile(context: Context, path: String, extension: String = "json"): File {
        val digest = MessageDigest.getInstance("SHA-256")
                .digest((SOURCE_REVISION + "/" + path).toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return File(File(context.filesDir, CACHE_DIRECTORY), "$digest.$extension")
    }

    private fun decodePayload(payload: ByteArray): String {
        val decodedBytes = if (payload.size >= 2 &&
                payload[0] == GZIP_MAGIC_1 && payload[1] == 0x8b.toByte()) {
            GZIPInputStream(ByteArrayInputStream(payload)).use { readBytesLimited(it, MAX_JSON_BYTES) }
        } else {
            payload
        }
        if (decodedBytes.size > MAX_JSON_BYTES) throw IOException("Emoji JSON exceeds size limit")
        return validateLottieJson(String(decodedBytes, Charsets.UTF_8))
    }

    private fun validateLottieJson(rawJson: String): String {
        val json = rawJson.trim()
        if (json.length > MAX_JSON_BYTES || !isValidLottieJson(json)) {
            throw IOException("Invalid or oversized Telegram emoji animation JSON")
        }
        return json
    }

    private fun isValidLottieJson(json: String): Boolean {
        return runCatching {
            val objectValue = JSONObject(json)
            objectValue.optJSONArray("layers") != null &&
                    objectValue.optDouble("fr", 0.0) > 0.0 &&
                    objectValue.optDouble("op", 0.0) > objectValue.optDouble("ip", 0.0)
        }.getOrDefault(false)
    }

    private fun readUtf8Limited(input: java.io.InputStream, limit: Int): String {
        return String(readBytesLimited(input, limit), Charsets.UTF_8)
    }

    private fun readBytesLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > limit) throw IOException("Telegram emoji asset exceeded size limit")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun isVariationSelector(point: Int): Boolean {
        return point == 0xFE0E || point == 0xFE0F || point in 0xE0100..0xE01EF
    }

    private fun isSkinToneModifier(point: Int): Boolean = point in 0x1F3FB..0x1F3FF

    private fun isEmojiBase(point: Int): Boolean {
        return point in 0x1F000..0x1FAFF ||
                point in 0x2600..0x27BF ||
                point in setOf(0x00A9, 0x00AE, 0x203C, 0x2049, 0x2122, 0x2139, 0x3030, 0x303D, 0x3297, 0x3299)
    }

    private const val ANIMATION_RECORD_COUNT = 599
    private const val GZIP_MAGIC_1: Byte = 0x1f
    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val KEYCAP = 0x20E3
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
    private val TAG_CHARACTERS = 0xE0020..0xE007F
}
