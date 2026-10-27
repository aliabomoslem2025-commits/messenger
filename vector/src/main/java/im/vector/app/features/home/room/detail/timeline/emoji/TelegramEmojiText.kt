/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */
package im.vector.app.features.home.room.detail.timeline.emoji

/**
 * Unicode-only emoji detection and the locally bundled Telegram animation assets.
 * Unknown emoji remain normal Unicode glyphs; they are not given a fake scale animation.
 */
object TelegramEmojiText {
    private val animationAssets = mapOf(
            "😀" to "U+1F600_1.tgs.b64",
            "😂" to "U+1F602_1.tgs.b64",
            "🤣" to "U+1F923_1.tgs.b64",
    )

    fun animationAssetName(emoji: String): String? {
        return animationAssets[emoji.trim().replace("\uFE0F", "")]
    }

    /**
     * Returns true only when the body contains one emoji grapheme. Handles variation selectors,
     * skin tones, paired regional indicators, keycaps and ZWJ emoji such as family sequences.
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
