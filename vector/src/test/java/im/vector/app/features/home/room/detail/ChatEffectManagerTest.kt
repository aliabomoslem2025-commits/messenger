/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */
package im.vector.app.features.home.room.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatEffectManagerTest {

    @Test
    fun standaloneLaughterEmojiTriggersLaughterEffect() {
        assertEquals(ChatEffect.LAUGHTER, ChatEffectManager.resolveStandaloneEmojiEffect("😂"))
        assertEquals(ChatEffect.LAUGHTER, ChatEffectManager.resolveStandaloneEmojiEffect(" 🤣 "))
    }

    @Test
    fun standaloneCryingEmojiTriggersCryingEffect() {
        assertEquals(ChatEffect.CRYING, ChatEffectManager.resolveStandaloneEmojiEffect("😭"))
    }

    @Test
    fun standaloneHeartEmojiTriggersHeartEffect() {
        assertEquals(ChatEffect.HEARTS, ChatEffectManager.resolveStandaloneEmojiEffect("❤️"))
    }

    @Test
    fun sentenceContainingEmojiDoesNotTriggerFullScreenEffect() {
        assertNull(ChatEffectManager.resolveStandaloneEmojiEffect("I am happy 😂"))
        assertNull(ChatEffectManager.resolveStandaloneEmojiEffect("❤️ thank you"))
    }

    @Test
    fun unsupportedEmojiDoesNotTriggerEffect() {
        assertNull(ChatEffectManager.resolveStandaloneEmojiEffect("🙂"))
        assertNull(ChatEffectManager.resolveStandaloneEmojiEffect(""))
    }
}
