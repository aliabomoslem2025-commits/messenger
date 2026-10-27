/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.timeline.emoji

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

class TelegramEmojiTextTest {

    @Test
    fun `single standard emoji is recognized`() {
        TelegramEmojiText.isSingleEmoji("😀") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("❤️") shouldBeEqualTo true
    }

    @Test
    fun `modifier and joiner emoji are recognized as one grapheme`() {
        TelegramEmojiText.isSingleEmoji("👍🏽") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("👨‍👩‍👧‍👦") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("🏴‍☠") shouldBeEqualTo true
    }

    @Test
    fun `flags and keycaps are recognized`() {
        TelegramEmojiText.isSingleEmoji("🇩🇪") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("1️⃣") shouldBeEqualTo true
    }

    @Test
    fun `multiple emoji and mixed text are not standalone emoji`() {
        TelegramEmojiText.isSingleEmoji("😀😂") shouldBeEqualTo false
        TelegramEmojiText.isSingleEmoji("hello 😀") shouldBeEqualTo false
        TelegramEmojiText.isSingleEmoji("") shouldBeEqualTo false
        TelegramEmojiText.isSingleEmoji("🇩") shouldBeEqualTo false
    }
}
