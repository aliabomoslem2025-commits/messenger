/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.list

import androidx.recyclerview.widget.DefaultItemAnimator

private const val ADD_DURATION_MS = 170L
private const val REMOVE_DURATION_MS = 130L
private const val MOVE_DURATION_MS = 170L
private const val CHANGE_DURATION_MS = 120L

/**
 * Short list transitions keep navigation responsive.
 *
 * Room rows are rebound often for read receipts, unread counts, typing state,
 * and sync updates. Disabling change cross-fades prevents these routine Matrix
 * updates from making rows flicker while add/remove/move transitions remain.
 */
class RoomListAnimator : DefaultItemAnimator() {

    init {
        addDuration = ADD_DURATION_MS
        removeDuration = REMOVE_DURATION_MS
        moveDuration = MOVE_DURATION_MS
        changeDuration = CHANGE_DURATION_MS
        supportsChangeAnimations = false
    }
}
