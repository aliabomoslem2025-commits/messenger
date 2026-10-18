/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.list

import androidx.recyclerview.widget.DefaultItemAnimator

private const val ADD_DURATION_MS = 180L
private const val REMOVE_DURATION_MS = 140L
private const val MOVE_DURATION_MS = 180L

class RoomListAnimator : DefaultItemAnimator() {

    init {
        addDuration = ADD_DURATION_MS
        removeDuration = REMOVE_DURATION_MS
        moveDuration = MOVE_DURATION_MS
        changeDuration = 120L
    }
}
