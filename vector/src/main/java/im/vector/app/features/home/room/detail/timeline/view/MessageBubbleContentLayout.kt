/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.view

import android.content.Context
import android.text.TextDirectionHeuristics
import android.util.AttributeSet
import android.view.View
import android.view.ViewStub
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import im.vector.app.R
import im.vector.app.core.resources.DefaultLocaleProvider
import im.vector.app.core.resources.getLayoutDirectionFromCurrentLocale

class MessageBubbleContentLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        ConstraintLayout(context, attrs, defStyleAttr) {

    private var messageTextView: TextView? = null

    private lateinit var contentContainerView: View
    private lateinit var timeView: View
    private lateinit var contentOverlayView: View
    private var messageLayoutDirection: Int = View.LAYOUT_DIRECTION_LOCALE

    private val timeViewMeasuredWidthWithMargins: Int
        get() = timeView.measuredWidth + timeView.marginStart + timeView.marginEnd

    override fun onFinishInflate() {
        super.onFinishInflate()
        val textViewStub: ViewStub = findViewById(R.id.messageContentTextStub)
        contentContainerView = findViewById(R.id.viewStubContainer)
        contentOverlayView = findViewById(R.id.messageOverlayView)
        timeView = findViewById(R.id.messageTimeView)
        textViewStub.setOnInflateListener { _, inflated ->
            textViewStub.setOnInflateListener(null)
            messageTextView = inflated.findViewById(R.id.messageTextView)
        }
        messageLayoutDirection = DefaultLocaleProvider(resources).getLayoutDirectionFromCurrentLocale()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Resolve existing text before measuring. This changes only the text view; the outer
        // bubble keeps Element's sender-based left/right placement.
        updateMessageLayoutDirection()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // The TextView may be created by a ViewStub during the first super.onMeasure() call.
        // In that case the message text was not available before measurement. Apply its
        // resolved direction now and remeasure once so the first RTL message is measured
        // with the same paragraph direction it will use when laid out.
        val directionAfterFirstMeasure = messageLayoutDirection
        updateMessageLayoutDirection()
        if (messageTextView != null && messageLayoutDirection != directionAfterFirstMeasure) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            updateMessageLayoutDirection()
        }

        val messageTextView = this.messageTextView
        // Then if we have a text message layout, we can resize ourself so we can position the timeView without losing space.
        if (messageTextView != null) {
            val width: Int
            val height: Int
            val textLineCount = messageTextView.lineCount
            val maxContentWidth = (contentContainerView.layoutParams as LayoutParams).matchConstraintMaxWidth
            val lastLineWidth = if (textLineCount != 0) messageTextView.layout.getLineWidth(textLineCount - 1) else 0f
            if (textLineCount == 1 && contentContainerView.measuredWidth + timeViewMeasuredWidthWithMargins < maxContentWidth) {
                width = contentContainerView.measuredWidth + timeViewMeasuredWidthWithMargins
                height = contentContainerView.measuredHeight
            } else if (textLineCount > 1 && lastLineWidth + timeViewMeasuredWidthWithMargins
                    < contentContainerView.measuredWidth - contentContainerView.paddingEnd) {
                width = contentContainerView.measuredWidth
                height = contentContainerView.measuredHeight
            } else {
                width = contentContainerView.measuredWidth
                height = contentContainerView.measuredHeight + timeView.measuredHeight
            }
            setMeasuredDimension(width, height)
        }
    }

    /**
     * The app layout direction and the paragraph direction are separate concerns.
     * Use the first strong character in the actual message so Persian/Arabic
     * paragraphs align to the right even when the app itself uses an LTR locale.
     */
    private fun updateMessageLayoutDirection() {
        val textView = messageTextView
        val text = textView?.text
        val resolvedDirection = when {
            text == null || text.isBlank() ->
                DefaultLocaleProvider(resources).getLayoutDirectionFromCurrentLocale()
            TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(text, 0, text.length) ->
                View.LAYOUT_DIRECTION_RTL
            else -> View.LAYOUT_DIRECTION_LTR
        }
        messageLayoutDirection = resolvedDirection

        // textAlignment="viewStart" follows this view's layout direction. Updating
        // the actual TextView (including EditorStyledTextView) lets RTL paragraphs
        // align right inside a bubble when the app locale itself is LTR.
        textView?.let {
            if (it.layoutDirection != resolvedDirection) {
                it.layoutDirection = resolvedDirection
            }
            if (it.textDirection != View.TEXT_DIRECTION_FIRST_STRONG) {
                it.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            }
            if (it.textAlignment != View.TEXT_ALIGNMENT_VIEW_START) {
                it.textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        // If we have a text message layout, we want to render it manually, so we don't call super.onLayout
        if (messageTextView != null) {
            val parentLeft: Int = paddingLeft
            val parentRight: Int = right - left - paddingRight
            val parentTop: Int = paddingTop
            val parentBottom: Int = bottom - top - paddingBottom
            if (messageLayoutDirection == LAYOUT_DIRECTION_RTL) {
                val contentLeft = parentRight - contentContainerView.measuredWidth - contentContainerView.marginEnd
                contentContainerView.layout(
                        contentLeft,
                        parentTop + contentContainerView.marginTop,
                        parentRight - contentContainerView.marginEnd,
                        parentTop + contentContainerView.marginTop + contentContainerView.measuredHeight
                )
                timeView.layout(
                        parentLeft + timeView.marginEnd,
                        parentBottom - timeView.measuredHeight - timeView.marginBottom,
                        parentLeft + timeView.measuredWidth + timeView.marginEnd,
                        parentBottom - timeView.marginBottom
                )
            } else {
                contentContainerView.layout(
                        parentLeft,
                        parentTop,
                        parentLeft + contentContainerView.measuredWidth,
                        parentTop + contentContainerView.measuredHeight
                )
                timeView.layout(
                        parentRight - timeView.measuredWidth - timeView.marginEnd,
                        parentBottom - timeView.measuredHeight - timeView.marginBottom,
                        parentRight - timeView.marginEnd,
                        parentBottom - timeView.marginBottom
                )
            }
        } else {
            super.onLayout(changed, left, top, right, bottom)
        }
    }
}
