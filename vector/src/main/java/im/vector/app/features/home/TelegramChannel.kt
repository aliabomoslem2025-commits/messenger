/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

/**
 * Matrix room marker for Telegram-style broadcast channels.
 *
 * The channel remains a normal Matrix room for synchronization and media transport,
 * while its power levels distinguish administrator posts from subscriber reactions.
 */
object TelegramChannel {
    const val ROOM_TYPE = "im.vector.app.telegram.channel"
}
