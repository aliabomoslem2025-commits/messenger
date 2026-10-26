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
    fun standaloneCelebrationAndSnowEmojiTriggerTheirEffects() {
        assertEquals(ChatEffect.CONFETTI, ChatEffectManager.resolveStandaloneTextEffect("🎉"))
        assertEquals(ChatEffect.CONFETTI, ChatEffectManager.resolveStandaloneTextEffect(" 🥳 "))
        assertEquals(ChatEffect.SNOWFALL, ChatEffectManager.resolveStandaloneTextEffect("❄️"))
    }

    @Test
    fun ordinaryTextContainingAnEffectEmojiDoesNotTriggerFullscreenEffect() {
        assertNull(ChatEffectManager.resolveStandaloneTextEffect("Happy birthday 🎉"))
        assertNull(ChatEffectManager.resolveStandaloneTextEffect("❄️ outside today"))
    }

    @Test
    fun unsupportedAndEmptyContentDoesNotTriggerFullscreenEffect() {
        assertNull(ChatEffectManager.resolveStandaloneTextEffect("🙂"))
        assertNull(ChatEffectManager.resolveStandaloneTextEffect(""))
    }
}
