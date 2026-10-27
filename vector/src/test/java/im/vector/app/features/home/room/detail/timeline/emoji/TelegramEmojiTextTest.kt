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
    fun `detects a standalone animated emoji`() {
        TelegramEmojiText.isSingleEmoji("😀") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("😂") shouldBeEqualTo true
    }

    @Test
    fun `accepts emoji presentation selectors and skin tone modifiers`() {
        TelegramEmojiText.isSingleEmoji("❤️") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("👍🏽") shouldBeEqualTo true
    }

    @Test
    fun `accepts joined family emoji and flags as one grapheme`() {
        TelegramEmojiText.isSingleEmoji("👨‍👩‍👧‍👦") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("🇩🇪") shouldBeEqualTo true
    }

    @Test
    fun `accepts keycap emoji`() {
        TelegramEmojiText.isSingleEmoji("1️⃣") shouldBeEqualTo true
        TelegramEmojiText.isSingleEmoji("#️⃣") shouldBeEqualTo true
    }

    @Test
    fun `rejects multiple emoji and ordinary text`() {
        TelegramEmojiText.isSingleEmoji("😂😂") shouldBeEqualTo false
        TelegramEmojiText.isSingleEmoji("hello 😀") shouldBeEqualTo false
        TelegramEmojiText.isSingleEmoji("Hello") shouldBeEqualTo false
        TelegramEmojiText.isSingleEmoji("") shouldBeEqualTo false
        TelegramEmojiText.isSingleEmoji("123") shouldBeEqualTo false
    }
}
