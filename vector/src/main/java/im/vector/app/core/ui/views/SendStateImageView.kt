/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.ui.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isInvisible
import im.vector.app.R
import im.vector.app.features.home.room.detail.timeline.item.SendStateDecoration
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings

class SendStateImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var currentSendState: SendStateDecoration = SendStateDecoration.NONE
    private var useOutgoingBubbleTint: Boolean = false

    init {
        if (isInEditMode) {
            render(SendStateDecoration.SENT)
        }
    }

    /**
     * Message bubbles may use a saturated outgoing surface. Keep the icon/state
     * itself Matrix-driven, but use the bubble's contrasting foreground for
     * normal sending/sent indicators when the owning bubble is outgoing.
     */
    fun setOutgoingBubbleTint(enabled: Boolean) {
        if (useOutgoingBubbleTint == enabled) return
        useOutgoingBubbleTint = enabled
        render(currentSendState)
    }

    fun render(sendState: SendStateDecoration) {
        currentSendState = sendState
        val tintAttribute = if (useOutgoingBubbleTint) {
            im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text
        } else {
            im.vector.lib.ui.styles.R.attr.vctr_content_tertiary
        }
        isInvisible = when (sendState) {
            SendStateDecoration.SENDING_NON_MEDIA -> {
                setImageResource(R.drawable.ic_sending_message)
                imageTintList = ColorStateList.valueOf(ThemeUtils.getColor(context, tintAttribute))
                contentDescription = context.getString(CommonStrings.event_status_a11y_sending)
                false
            }
            SendStateDecoration.SENT -> {
                setImageResource(R.drawable.ic_message_sent)
                imageTintList = ColorStateList.valueOf(ThemeUtils.getColor(context, tintAttribute))
                contentDescription = context.getString(CommonStrings.event_status_a11y_sent)
                false
            }
            SendStateDecoration.READ -> {
                setImageResource(R.drawable.ic_message_read)
                val readTint = if (useOutgoingBubbleTint) {
                    ThemeUtils.getColor(context, tintAttribute)
                } else {
                    0xFF3390EC.toInt()
                }
                imageTintList = ColorStateList.valueOf(readTint)
                contentDescription = context.getString(R.string.telegram_message_read_status)
                false
            }
            SendStateDecoration.FAILED -> {
                setImageResource(R.drawable.ic_sending_message_failed)
                imageTintList = null
                contentDescription = context.getString(CommonStrings.event_status_a11y_failed)
                false
            }
            SendStateDecoration.SENDING_MEDIA,
            SendStateDecoration.NONE -> {
                true
            }
        }
    }
}
