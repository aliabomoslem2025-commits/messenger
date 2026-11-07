/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
    private var drawDirectTail: Boolean = false
    private var directTailIsIncoming: Boolean = true
    private val directTailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val directTailPath = Path()
    private val directTailSize = DimensionConverter(resources).dpToPx(9).toFloat()

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
        val bubbleLayout = messageLayout as? TimelineMessageLayout.Bubble
        drawDirectTail = bubbleLayout?.hasDirectTail == true
        directTailIsIncoming = bubbleLayout?.isIncoming ?: true
        invalidate()
        bubbleLayout
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

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!drawDirectTail || !::views.isInitialized || !::bubbleDrawable.isInitialized) return

        val bubble = views.bubbleView
        val wrapper = views.bubbleWrapper
        if (bubble.width <= 0 || bubble.height <= 0) return

        val left = (wrapper.left + bubble.left).toFloat()
        val top = (wrapper.top + bubble.top).toFloat()
        val right = left + bubble.width
        val bottom = top + bubble.height
        val tail = directTailSize
        val fillColor = bubbleDrawable.fillColor?.defaultColor ?: Color.TRANSPARENT
        if (fillColor == Color.TRANSPARENT) return

        directTailPaint.color = fillColor
        directTailPath.reset()
        if (directTailIsIncoming) {
            directTailPath.moveTo(left + tail * 1.15f, bottom - tail * 0.9f)
            directTailPath.cubicTo(
                    left + tail * 0.78f, bottom - tail * 0.22f,
                    left + tail * 0.12f, bottom + tail * 0.48f,
                    left - tail * 0.72f, bottom + tail * 0.24f
            )
            directTailPath.cubicTo(
                    left - tail * 0.48f, bottom - tail * 0.28f,
                    left - tail * 0.02f, bottom - tail * 0.72f,
                    left + tail * 0.58f, bottom - tail * 1.05f
            )
        } else {
            directTailPath.moveTo(right - tail * 1.15f, bottom - tail * 0.9f)
            directTailPath.cubicTo(
                    right - tail * 0.78f, bottom - tail * 0.22f,
                    right - tail * 0.12f, bottom + tail * 0.48f,
                    right + tail * 0.72f, bottom + tail * 0.24f
            )
            directTailPath.cubicTo(
                    right + tail * 0.48f, bottom - tail * 0.28f,
                    right + tail * 0.02f, bottom - tail * 0.72f,
                    right - tail * 0.58f, bottom - tail * 1.05f
            )
        }
        directTailPath.close()
        canvas.drawPath(directTailPath, directTailPaint)
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
            val textColorAttribute = if (isPseudoBubble && timestampInsideMessage && !addMessageOverlay) {
                im.vector.lib.ui.styles.R.attr.vctr_content_primary
            } else if (isIncoming) {
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
            val timeColor = if (isPseudoBubble && timestampInsideMessage && !addMessageOverlay) {
                ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
            } else if (!isIncoming) {
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
        val edgeInset = resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_margin_end)
        val groupAvatarGutter = resources.getDimensionPixelSize(im.vector.lib.ui.styles.R.dimen.chat_bubble_margin_start)
        val startInset = if (isIncoming && !isDirect) groupAvatarGutter else edgeInset

        // Telegram sent messages sit close to the trailing edge. The previous 28dp
        // trailing inset made each outgoing bubble look detached from the screen edge.
        views.messageStartGuideline.updateLayoutParams<LayoutParams> {
            marginStart = startInset
        }
        views.messageEndGuideline.updateLayoutParams<LayoutParams> {
            marginEnd = edgeInset
        }
    }

    /**
     * Keep the delivery indicator inside the outgoing bubble, after the timestamp,
     * matching Telegram's compact message footer. Incoming timestamps keep their
     * normal right inset because incoming messages have no delivery indicator.
     */
    private fun TimelineMessageLayout.Bubble.setTelegramSendStatePlacement() = apply {
        val endInset = DimensionConverter(resources).dpToPx(8)
        views.messageSendStateImageView.setOutgoingBubbleTint(!isIncoming)
        if (isIncoming) {
            views.eventSendingIndicator.indeterminateTintList = ColorStateList.valueOf(
                    ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
            )
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
            views.eventSendingIndicator.indeterminateTintList = ColorStateList.valueOf(
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
