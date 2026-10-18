/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.action

import androidx.recyclerview.widget.DefaultItemAnimator

private const val ADD_DURATION_IN_MILLIS = 180L
private const val REMOVE_DURATION_IN_MILLIS = 140L
private const val MOVE_DURATION_IN_MILLIS = 160L
private const val CHANGE_DURATION_IN_MILLIS = 100L

/**
 * Short transitions for the message-action list and the "Report content" submenu.
 *
 * Message actions are added and removed as menu state changes. Disable change
 * cross-fades to avoid blinking when the existing Matrix state is rebound.
 */
class MessageActionsAnimator : DefaultItemAnimator() {

    init {
        addDuration = ADD_DURATION_IN_MILLIS
        removeDuration = REMOVE_DURATION_IN_MILLIS
        moveDuration = MOVE_DURATION_IN_MILLIS
        changeDuration = CHANGE_DURATION_IN_MILLIS
        supportsChangeAnimations = false
    }
}
