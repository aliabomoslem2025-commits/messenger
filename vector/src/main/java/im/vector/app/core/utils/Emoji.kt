/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.utils

import com.vanniktech.emoji.isOnlyEmojis

/**
 * Test if a string contains emojis.
 * It seems that the regex [emoji_regex]+ does not work.
 * Some characters like ?, # or digit are accepted.
 *
 * @param str the body to test
 * @return true if the body contains only emojis
 */
fun containsOnlyEmojis(str: String?): Boolean {
    // Now rely on vanniktech library
    return str.isOnlyEmojis()
}

/**
 * Returns true when a string is a single emoji grapheme rather than several adjacent
 * emoji. This avoids android.icu APIs so the helper also works on the app's API 21 floor.
 * Handles the multi-code-point forms commonly used by Telegram emoji: variation
 * selectors, keycaps, skin tones, paired regional indicators, tag flags and ZWJ sequences.
 */
fun isSingleEmoji(str: String?): Boolean {
    val text = str?.trim().orEmpty()
    if (text.isEmpty() || !containsOnlyEmojis(text)) return false

    var clusterCount = 0
    var hasPendingRegionalIndicator = false
    var joinNext = false
    var index = 0
    while (index < text.length) {
        val codePoint = Character.codePointAt(text, index)
        index += Character.charCount(codePoint)

        when {
            codePoint == 0x200D -> {
                joinNext = true
                hasPendingRegionalIndicator = false
            }
            codePoint == 0xFE0E ||
                    codePoint == 0xFE0F ||
                    codePoint == 0x20E3 ||
                    codePoint in 0x1F3FB..0x1F3FF ||
                    codePoint in 0xE0020..0xE007F -> Unit
            joinNext -> {
                joinNext = false
                hasPendingRegionalIndicator = false
            }
            codePoint in 0x1F1E6..0x1F1FF -> {
                if (hasPendingRegionalIndicator) {
                    hasPendingRegionalIndicator = false
                } else {
                    clusterCount++
                    hasPendingRegionalIndicator = true
                }
            }
            else -> {
                clusterCount++
                hasPendingRegionalIndicator = false
            }
        }
        if (clusterCount > 1) return false
    }
    return clusterCount == 1
}

/**
 * Same as split, but considering emojis.
 */
fun CharSequence.splitEmoji(): List<CharSequence> {
    val result = mutableListOf<CharSequence>()

    var index = 0

    while (index < length) {
        val firstChar = get(index)

        if (firstChar.code == 0x200e) {
            // Left to right mark. What should I do with it?
        } else if (firstChar.code in 0xD800..0xDBFF && index + 1 < length) {
            // We have the start of a surrogate pair
            val secondChar = get(index + 1)

            if (secondChar.code in 0xDC00..0xDFFF) {
                // We have an emoji
                result.add("$firstChar$secondChar")
                index++
            } else {
                // Not sure what we have here...
                result.add("$firstChar")
            }
        } else {
            // Regular char
            result.add("$firstChar")
        }

        index++
    }

    return result
}
