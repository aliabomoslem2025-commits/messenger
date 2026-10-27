/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */
package im.vector.app.features.home.room.detail.timeline.emoji

import android.content.Context
import java.util.LinkedHashMap

/**
 * Maps Unicode emoji graphemes to the complete standard Telegram animated-emoji set.
 *
 * The bundled manifest includes every TGS entry in the source set. The binary animation is fetched
 * lazily by TelegramAnimatedEmojiSpan and cached in the app's private cache directory, avoiding
 * a multi-megabyte APK increase while allowing every listed emoji and variant to be played.
 */
object TelegramEmojiText {
    private const val MANIFEST_PATH = "telegram_emoji/full/manifest.json"
    private const val MAX_MANIFEST_BYTES = 1_000_000

    private data class AssetVariant(
            val assetName: String,
            val variant: Int,
    )

    @Volatile
    private var manifestIndex: Map<String, List<AssetVariant>>? = null

    private val manifestLock = Any()

    /**
     * True only when text consists of one Unicode emoji grapheme and an animation for it exists
     * in the official-set manifest. Unsupported/newer emoji remain ordinary text messages.
     */
    fun isAnimatedEmoji(context: Context, rawText: String): Boolean {
        val emoji = rawText.trim()
        return isSingleEmoji(emoji) && animationAssetName(context, emoji) != null
    }

    /**
     * Selects an asset variant deterministically for this message so RecyclerView rebinding does
     * not change animation identity. If Telegram's set has more than one TGS document for an
     * emoticon, all variants remain indexed and different message IDs can use different variants.
     */
    fun animationAssetName(context: Context, rawEmoji: String, stableKey: String = rawEmoji): String? {
        val emoji = normalize(rawEmoji.trim())
        if (emoji.isEmpty()) return null
        val variants = loadManifest(context)[emoji] ?: return fallbackAsset(emoji)
        if (variants.isEmpty()) return fallbackAsset(emoji)
        val index = ((stableKey.hashCode().toLong() and 0x7fffffffL) % variants.size).toInt()
        return variants[index].assetName
    }

    private fun fallbackAsset(emoji: String): String? = when (normalize(emoji)) {
        "😀" -> "U+1F600_1.tgs"
        "😂" -> "U+1F602_1.tgs"
        "🤣" -> "U+1F923_1.tgs"
        else -> null
    }

    private fun loadManifest(context: Context): Map<String, List<AssetVariant>> {
        manifestIndex?.let { return it }
        synchronized(manifestLock) {
            manifestIndex?.let { return it }
            val result = runCatching {
                context.applicationContext.assets.open(MANIFEST_PATH).use { stream ->
                    val bytes = stream.readBytes()
                    require(bytes.size <= MAX_MANIFEST_BYTES) { "Animated emoji manifest is too large" }
                    val root = org.json.JSONObject(String(bytes, Charsets.UTF_8))
                    val items = root.getJSONArray("items")
                    val mutable = LinkedHashMap<String, MutableList<AssetVariant>>()
                    for (index in 0 until items.length()) {
                        val item = items.getJSONObject(index)
                        val emoji = normalize(item.optString("emoji"))
                        val file = item.optString("assetName")
                        val variant = item.optInt("variant", 1)
                        if (emoji.isEmpty() || !isSafeAssetName(file)) continue
                        val variants = mutable.getOrPut(emoji) { mutableListOf() }
                        if (variants.none { it.assetName == file }) {
                            variants.add(AssetVariant(file, variant))
                        }
                    }
                    mutable.mapValues { (_, variants) -> variants.sortedBy { it.variant } }
                }
            }.getOrElse { error ->
                // If the manifest is damaged, keep the three explicitly bundled animations usable.
                android.util.Log.e("TelegramEmojiText", "Failed to read animated emoji manifest", error)
                mapOf(
                        "😀" to listOf(AssetVariant("U+1F600_1.tgs", 1)),
                        "😂" to listOf(AssetVariant("U+1F602_1.tgs", 1)),
                        "🤣" to listOf(AssetVariant("U+1F923_1.tgs", 1)),
                )
            }
            manifestIndex = result
            return result
        }
    }

    private fun isSafeAssetName(file: String): Boolean {
        return file.endsWith(".tgs") && file.isNotBlank() &&
                file.all { it.isLetterOrDigit() || it == '_' || it == '+' || it == '.' || it == '-' }
    }

    private fun normalize(emoji: String): String {
        return emoji.replace("\uFE0F", "").replace("\uFE0E", "")
    }

    /**
     * Returns true only when the trimmed body contains one emoji grapheme. Handles variation
     * selectors, skin tones, paired regional indicators, keycaps and zero-width-joiner sequences.
     */
    fun isSingleEmoji(rawText: String): Boolean {
        val text = normalize(rawText.trim())
        if (text.isEmpty() || text.any { it.isWhitespace() }) return false

        val points = ArrayList<Int>()
        var index = 0
        while (index < text.length) {
            val point = Character.codePointAt(text, index)
            points.add(point)
            index += Character.charCount(point)
        }
        if (points.isEmpty()) return false

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
        return emojiClusters == 1 && !expectsJoinedEmoji && regionalIndicatorCount == 0
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

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val KEYCAP = 0x20E3
    private val REGIONAL_INDICATORS = 0x1F1E6..0x1F1FF
    private val TAG_CHARACTERS = 0xE0020..0xE007F
}
