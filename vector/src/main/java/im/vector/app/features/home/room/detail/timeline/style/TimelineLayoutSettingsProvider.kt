/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.style

import javax.inject.Inject

/**
 * This messenger uses Telegram-style bubbles as its canonical conversation layout.
 * Matrix timeline models and event handling remain unchanged; only their presentation
 * is consistently rendered through the incoming/outgoing bubble layouts.
 */
class TimelineLayoutSettingsProvider @Inject constructor() {

    fun getLayoutSettings(): TimelineLayoutSettings = TimelineLayoutSettings.BUBBLE
}
