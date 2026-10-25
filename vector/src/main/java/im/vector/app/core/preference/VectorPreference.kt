/*
 * Copyright 2018-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.core.preference

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.widget.ImageViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import im.vector.app.features.themes.ThemeUtils
import org.matrix.android.sdk.api.extensions.orFalse
import timber.log.Timber

/**
 * create a Preference with a dedicated click/long click methods.
 * It also allow the title to be displayed on several lines
 */
open class VectorPreference : Preference {

    var mTypeface = Typeface.NORMAL

    /**
     * Callback to be invoked when this Preference is long clicked.
     */
    var onPreferenceLongClickListener: OnPreferenceLongClickListener? = null

    /**
     * Interface definition for a callback to be invoked when a preference is
     * long clicked.
     */
    interface OnPreferenceLongClickListener {
        /**
         * Called when a Preference has been clicked.
         *
         * @param preference The Preference that was clicked.
         * @return True if the click was handled.
         */
        fun onPreferenceLongClick(preference: Preference): Boolean
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

    init {
        // Set to false to remove the space when there is no icon
        isIconSpaceReserved = true
    }

    var isHighlighted = false
        set(value) {
            field = value
            notifyChanged()
        }

    var tintIcon = false
        set(value) {
            field = value
            notifyChanged()
        }

    /**
     * Optional colored rounded tile behind the root-settings icon.
     * Null keeps the regular Element preference appearance.
     */
    var iconBackgroundColor: Int? = null
        set(value) {
            field = value
            notifyChanged()
        }

    /** Position of this row in a grouped Telegram-style settings card (-1 disables it). */
    var telegramCardIndex: Int = -1
        set(value) {
            field = value
            notifyChanged()
        }

    /** Number of visible rows in the grouped settings card. */
    var telegramCardCount: Int = 0
        set(value) {
            field = value
            notifyChanged()
        }

    var currentHighlightAnimator: Animator? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val itemView = holder.itemView
        addClickListeners(itemView)

        // display the title in multi-line to avoid ellipsis.
        try {
            val title = holder.findViewById(android.R.id.title) as? TextView
            val summary = holder.findViewById(android.R.id.summary) as? TextView
            if (title != null) {
                title.isSingleLine = false
                title.setTypeface(null, mTypeface)
            }

            summary?.setTypeface(null, mTypeface)

            // cancel existing animation (find a way to resume if happens during anim?)
            currentHighlightAnimator?.cancel()
            if (isHighlighted) {
                val colorFrom = Color.TRANSPARENT
                val colorTo = ThemeUtils.getColor(itemView.context, com.google.android.material.R.attr.colorPrimary)
                currentHighlightAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
                    duration = 250 // milliseconds
                    addUpdateListener { animator ->
                        itemView.setBackgroundColor(animator.animatedValue as Int)
                    }
                    doOnEnd {
                        currentHighlightAnimator = ValueAnimator.ofObject(ArgbEvaluator(), colorTo, colorFrom).apply {
                            duration = 250 // milliseconds
                            addUpdateListener { animator ->
                                itemView.setBackgroundColor(animator.animatedValue as Int)
                            }
                            doOnEnd {
                                isHighlighted = false
                            }
                            start()
                        }
                    }
                    startDelay = 200
                    start()
                }
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT)
            }
        } catch (e: Exception) {
            Timber.e(e, "onBindView")
        }

        super.onBindViewHolder(holder)

        if (telegramCardIndex >= 0 && telegramCardCount > 0) {
            val density = context.resources.displayMetrics.density
            val radius = 18f * density
            val isFirst = telegramCardIndex == 0
            val isLast = telegramCardIndex == telegramCardCount - 1
            val topLeft = if (isFirst) radius else 0f
            val topRight = if (isFirst) radius else 0f
            val bottomRight = if (isLast) radius else 0f
            val bottomLeft = if (isLast) radius else 0f
            itemView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                        topLeft, topLeft,
                        topRight, topRight,
                        bottomRight, bottomRight,
                        bottomLeft, bottomLeft,
                )
                setColor(ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_room_list_background))
            }
        }

        val icon = holder.findViewById(android.R.id.icon) as? ImageView
        icon?.let { imageView ->
            val tileColor = iconBackgroundColor
            if (tileColor != null) {
                val density = context.resources.displayMetrics.density
                imageView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 11f * density
                    setColor(tileColor)
                }
                val inset = (9f * density).toInt()
                imageView.setPadding(inset, inset, inset, inset)
                ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(Color.WHITE))
            } else if (tintIcon) {
                val color = ThemeUtils.getColor(context, im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
                ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(color))
            } else {
                imageView.background = null
                imageView.setPadding(0, 0, 0, 0)
                ImageViewCompat.setImageTintList(imageView, null)
            }
        }
    }

    /**
     * @param view
     */
    private fun addClickListeners(view: View) {
        view.setOnLongClickListener {
            onPreferenceLongClickListener?.onPreferenceLongClick(this@VectorPreference).orFalse()
        }

        view.setOnClickListener {
            // call only the click listener
            onPreferenceClickListener?.onPreferenceClick(this@VectorPreference)
        }
    }
}
