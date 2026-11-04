/*
 * Copyright 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */
package im.vector.app.features.home.room.detail.composer.voice

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextSwitcher
import android.widget.TextView
import androidx.annotation.ColorInt
import java.util.Locale

/** Digit-by-digit animated clock for voice and circular video-note recording. */
class AnimatedClockTickerView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    private val switchers = ArrayList<TextSwitcher>()
    private var lastFormatted = ""
    private var textColorInt = Color.WHITE
    private var textSizeSp = 14f

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setupViews()
    }

    private fun setupViews() {
        removeAllViews()
        switchers.clear()
        for (index in 0 until 5) {
            if (index == 2) {
                addView(TextView(context).apply {
                    text = ":"
                    setTextColor(textColorInt)
                    setTextSize(textSizeSp)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            } else {
                val switcher = TextSwitcher(context).apply {
                    setFactory {
                        TextView(context).apply {
                            setTextColor(textColorInt)
                            setTextSize(textSizeSp)
                            typeface = Typeface.MONOSPACE
                            gravity = Gravity.CENTER
                            layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    Gravity.CENTER
                            )
                        }
                    }
                    inAnimation = TranslateAnimation(
                            TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                            TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                            TranslateAnimation.RELATIVE_TO_PARENT, -1f,
                            TranslateAnimation.RELATIVE_TO_PARENT, 0f
                    ).apply { duration = 180L; interpolator = AccelerateDecelerateInterpolator() }
                    outAnimation = TranslateAnimation(
                            TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                            TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                            TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                            TranslateAnimation.RELATIVE_TO_PARENT, 1f
                    ).apply { duration = 180L; interpolator = AccelerateDecelerateInterpolator() }
                }
                switchers.add(switcher)
                addView(switcher, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            }
        }
        setTimeFormatted("00:00")
    }

    fun setTextColor(@ColorInt color: Int) {
        textColorInt = color
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView) child.setTextColor(color)
            if (child is TextSwitcher) {
                (child.currentView as? TextView)?.setTextColor(color)
                (child.nextView as? TextView)?.setTextColor(color)
            }
        }
    }

    fun setTextSizeSp(sizeSp: Float) {
        textSizeSp = sizeSp
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is TextView) child.textSize = sizeSp
            if (child is TextSwitcher) {
                (child.currentView as? TextView)?.textSize = sizeSp
                (child.nextView as? TextView)?.textSize = sizeSp
            }
        }
    }

    fun setTimeMs(elapsedMs: Long) {
        val seconds = (elapsedMs.coerceAtLeast(0L) / 1_000L).toInt()
        setTimeFormatted(String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60))
    }

    fun setTimeFormatted(formatted: String) {
        if (formatted.length < 5 || switchers.size != 4) return
        val digits = charArrayOf(formatted[0], formatted[1], formatted[3], formatted[4])
        if (lastFormatted.length == 5) {
            val previous = charArrayOf(lastFormatted[0], lastFormatted[1], lastFormatted[3], lastFormatted[4])
            for (index in digits.indices) if (digits[index] != previous[index]) {
                switchers[index].setText(digits[index].toString())
            }
        } else {
            for (index in digits.indices) switchers[index].setCurrentText(digits[index].toString())
        }
        lastFormatted = formatted
    }
}
