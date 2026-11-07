/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.helper

import android.content.res.Resources
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.scopes.ActivityScoped
import im.vector.app.features.settings.VectorPreferences
import javax.inject.Inject
import kotlin.math.roundToInt

@ActivityScoped
class TimelineMediaSizeProvider @Inject constructor(
        private val resources: Resources,
        private val vectorPreferences: VectorPreferences
) {

    var recyclerView: RecyclerView? = null
    private var cachedSize: Pair<Boolean, Pair<Int, Int>>? = null

    fun getMaxSize(isTelegramChannel: Boolean = false): Pair<Int, Int> {
        cachedSize?.takeIf { it.first == isTelegramChannel }?.let { return it.second }
        return computeMaxSize(isTelegramChannel).also { size ->
            if (size.first > 0 && size.second > 0) {
                cachedSize = isTelegramChannel to size
            }
        }
    }

    private fun computeMaxSize(isTelegramChannel: Boolean): Pair<Int, Int> {
        val width = recyclerView?.width ?: 0
        val height = recyclerView?.height ?: 0
        val maxImageWidth: Int
        val maxImageHeight: Int
        // Broadcast-channel posts are closer to Telegram's full-width cards.
        // Keep ordinary conversations at the narrower media width.
        val widthRatio = if (isTelegramChannel) 0.92f else 0.7f
        if (width < height) {
            maxImageWidth = (width * widthRatio).roundToInt()
            maxImageHeight = (height * 0.5f).roundToInt()
        } else {
            maxImageWidth = (width * widthRatio).roundToInt()
            maxImageHeight = (height * 0.7f).roundToInt()
        }
        return if (vectorPreferences.useMessageBubblesLayout() && !isTelegramChannel) {
            val bubbleMaxImageWidth = maxImageWidth.coerceAtMost(resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_fixed_size))
            Pair(bubbleMaxImageWidth, maxImageHeight)
        } else {
            Pair(maxImageWidth, maxImageHeight)
        }
    }
}
