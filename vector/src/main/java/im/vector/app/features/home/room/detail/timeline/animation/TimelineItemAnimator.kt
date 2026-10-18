/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.animation

import androidx.recyclerview.widget.DefaultItemAnimator

private const val ADD_DURATION_MS = 170L
private const val REMOVE_DURATION_MS = 130L
private const val MOVE_DURATION_MS = 170L
private const val CHANGE_DURATION_MS = 120L

/**
 * Subtle timeline transitions inspired by modern messenger interfaces.
 *
 * Keep change animations disabled: timeline rows are frequently rebound for
 * receipts, send state, and reactions, and cross-fades can otherwise make
 * already-visible messages flicker. Add/remove/move transitions remain enabled.
 */
class TimelineItemAnimator : DefaultItemAnimator() {

    init {
        addDuration = ADD_DURATION_MS
        removeDuration = REMOVE_DURATION_MS
        moveDuration = MOVE_DURATION_MS
        changeDuration = CHANGE_DURATION_MS
        supportsChangeAnimations = false
    }
}
