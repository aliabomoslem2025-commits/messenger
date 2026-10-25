/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.shape.MaterialShapeDrawable
import im.vector.app.R
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.databinding.ViewMessageBubbleBinding
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.style.shapeAppearanceModel
import im.vector.app.features.themes.ThemeUtils
import timber.log.Timber

class MessageBubbleView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr), TimelineMessageLayoutRenderer {

    private var isIncoming: Boolean = false

    private val horizontalStubPadding = DimensionConverter(resources).dpToPx(10)
    private val verticalStubPadding = DimensionConverter(resources).dpToPx(6)

    private lateinit var views: ViewMessageBubbleBinding
    private lateinit var bubbleDrawable: MaterialShapeDrawable
    private lateinit var rippleMaskDrawable: MaterialShapeDrawable

    init {
        inflate(context, R.layout.view_message_bubble, this)
        context.withStyledAttributes(attrs, im.vector.lib.ui.styles.R.styleable.MessageBubble) {
            isIncoming = getBoolean(im.vector.lib.ui.styles.R.styleable.MessageBubble_incoming_style, false)
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        views = ViewMessageBubbleBinding.bind(this)
        // Keep conversation geometry independent of the app/message language:
        // received bubbles stay at the leading (left) edge and sent bubbles at the
        // trailing (right) edge. MessageTextItem resolves paragraph direction from
        // each message, allowing Persian/Arabic text without mirroring the timeline.
        val messageAlignmentDirection = if (isIncoming) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        views.informationBottom.layoutDirection = messageAlignmentDirection
        views.messageThreadSummaryContainer.layoutDirection = messageAlignmentDirection
        views.bubbleWrapper.layoutDirection = messageAlignmentDirection
        views.bubbleView.layoutDirection = View.LAYOUT_DIRECTION_LTR

        bubbleDrawable = MaterialShapeDrawable()
        rippleMaskDrawable = MaterialShapeDrawable()
        DrawableCompat.setTint(rippleMaskDrawable, Color.WHITE)
        views.bubbleView.apply {
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            background = RippleDrawable(
                    ContextCompat.getColorStateList(context, com.google.android.material.R.color.mtrl_btn_ripple_color)
                            ?: ColorStateList.valueOf(Color.TRANSPARENT),
                    bubbleDrawable,
                    rippleMaskDrawable
            )
        }
    }

    override fun renderMessageLayout(messageLayout: TimelineMessageLayout) {
        (messageLayout as? TimelineMessageLayout.Bubble)
                ?.updateDrawables()
                ?.setConstraints()
                ?.toggleMessageOverlay()
                ?.setPadding()
                ?.setMargins()
                ?.setTelegramSendStatePlacement()
                ?.setAdditionalTopSpace()
                ?.setMessageTextColor()
                ?: Timber.v("Can't render messageLayout $messageLayout")
    }

    private fun TimelineMessageLayout.Bubble.updateDrawables() = apply {
        val shapeAppearanceModel = cornersRadius.shapeAppearanceModel()
        bubbleDrawable.apply {
            this.shapeAppearanceModel = shapeAppearanceModel
            this.fillColor = if (isPseudoBubble) {
                ColorStateList.valueOf(Color.TRANSPARENT)
            } else {
                val backgroundColorAttr =
                        if (isIncoming) {
                            im.vector.lib.ui.styles.R.attr.vctr_message_bubble_inbound
                        } else {
                            im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound
                        }
                val backgroundColor = ThemeUtils.getColor(context, backgroundColorAttr)
                ColorStateList.valueOf(backgroundColor)
            }
        }
        rippleMaskDrawable.shapeAppearanceModel = shapeAppearanceModel
    }

    /**
     * Reapply the bubble-specific text palette after send-state binding updates the TextView tint.
     */
    internal fun refreshBubbleTextColor(messageLayout: TimelineMessageLayout.Bubble) {
        messageLayout.setMessageTextColor()
    }

    /**
     * Telegram's outgoing blue bubbles use white text. Apply this only to the
     * actual message body, not globally, so incoming messages, system notices,
     * settings and unrelated Matrix screens retain their normal text palette.
     */
    private fun TimelineMessageLayout.Bubble.setMessageTextColor() = apply {
        val messageText = views.viewStubContainer.root.findViewById<TextView>(R.id.messageTextView)
        if (messageText != null) {
            val textColorAttribute = if (isIncoming) {
                im.vector.lib.ui.styles.R.attr.vctr_content_primary
            } else {
                im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text
            }
            messageText.setTextColor(ThemeUtils.getColor(context, textColorAttribute))
        }
    }

    private fun TimelineMessageLayout.Bubble.setConstraints() = apply {
        ConstraintSet().apply {
            clone(views.bubbleView)
            clear(R.id.viewStubContainer, ConstraintSet.END)
            if (timestampInsideMessage) {
                connect(R.id.viewStubContainer, ConstraintSet.END, com.google.android.material.R.id.parent, ConstraintSet.END, 0)
            } else {
                connect(R.id.viewStubContainer, ConstraintSet.END, R.id.messageTimeView, ConstraintSet.START, 0)
            }
            applyTo(views.bubbleView)
        }
    }

    private fun TimelineMessageLayout.Bubble.toggleMessageOverlay() = apply {
        if (addMessageOverlay) {
            val timeColor = ContextCompat.getColor(context, im.vector.lib.ui.styles.R.color.palette_white)
            views.messageTimeView.setTextColor(timeColor)
            views.messageOverlayView.isVisible = true
            (views.messageOverlayView.background as? GradientDrawable)?.cornerRadii = cornersRadius.toFloatArray()
        } else {
            val timeColor = if (!isIncoming) {
                ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text)
            } else {
                ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_content_tertiary)
            }
            views.messageTimeView.setTextColor(timeColor)
            views.messageOverlayView.isVisible = false
        }
    }

    private fun TimelineMessageLayout.Bubble.setPadding() = apply {
        if (isPseudoBubble && timestampInsideMessage) {
            views.viewStubContainer.root.setPadding(0, 0, 0, 0)
        } else {
            views.viewStubContainer.root.setPadding(horizontalStubPadding, verticalStubPadding, horizontalStubPadding, verticalStubPadding)
        }
    }

    private fun TimelineMessageLayout.Bubble.setMargins() = apply {
        if (isIncoming) {
            views.messageEndGuideline.updateLayoutParams<LayoutParams> {
                marginEnd = resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_margin_end)
            }
            views.messageStartGuideline.updateLayoutParams<LayoutParams> {
                marginStart = resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_margin_start)
            }
        } else {
            views.messageEndGuideline.updateLayoutParams<LayoutParams> {
                marginEnd = resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_margin_start)
            }
            views.messageStartGuideline.updateLayoutParams<LayoutParams> {
                marginStart = resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_margin_end)
            }
        }
    }

    /**
     * Keep the delivery indicator inside the outgoing bubble, after the timestamp,
     * matching Telegram's compact message footer. Incoming timestamps keep their
     * normal right inset because incoming messages have no delivery indicator.
     */
    private fun TimelineMessageLayout.Bubble.setTelegramSendStatePlacement() = apply {
        val endInset = DimensionConverter(resources).dpToPx(8)
        if (isIncoming) {
            views.messageSendStateImageView.updateLayoutParams<LayoutParams> {
                removeRule(android.widget.RelativeLayout.ALIGN_END)
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                marginEnd = endInset
            }
            views.eventSendingIndicator.updateLayoutParams<LayoutParams> {
                removeRule(android.widget.RelativeLayout.ALIGN_END)
                addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                marginEnd = endInset
            }
            views.messageTimeView.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                marginEnd = endInset
            }
        } else {
            views.messageSendStateImageView.updateLayoutParams<LayoutParams> {
                removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                addRule(android.widget.RelativeLayout.ALIGN_END, R.id.bubbleWrapper)
                marginEnd = endInset
            }
            views.eventSendingIndicator.updateLayoutParams<LayoutParams> {
                removeRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                addRule(android.widget.RelativeLayout.ALIGN_END, R.id.bubbleWrapper)
                marginEnd = endInset
            }
            views.messageTimeView.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                marginEnd = DimensionConverter(resources).dpToPx(26)
            }
            views.messageSendStateImageView.imageTintList = android.content.res.ColorStateList.valueOf(
                    ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text)
            )
        }
    }

    private fun TimelineMessageLayout.Bubble.setAdditionalTopSpace() = apply {
        views.additionalTopSpace.isVisible = addTopMargin
    }

    private fun TimelineMessageLayout.Bubble.CornersRadius.toFloatArray(): FloatArray {
        return floatArrayOf(topStartRadius, topStartRadius, topEndRadius, topEndRadius, bottomEndRadius, bottomEndRadius, bottomStartRadius, bottomStartRadius)
    }
}
