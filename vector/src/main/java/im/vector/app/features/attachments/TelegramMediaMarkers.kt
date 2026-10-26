/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */
package im.vector.app.features.attachments

/**
 * Client interoperability marker for Telegram-style circular video notes.
 *
 * Matrix has no standard video-note message type. The marker is carried in the media filename
 * (the normal m.room.message body for media events) so this client can render the recording as a
 * circle after sync and on another installation. The original filename is stripped in the UI.
 */
internal object TelegramMediaMarkers {
    const val VIDEO_NOTE_FILENAME_PREFIX = "__messenger_video_note__:"
}
