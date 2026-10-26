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
import android.view.ViewGroup
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

    var currentHighlightAnimator: Animator? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        val itemView = holder.itemView
        addClickListeners(itemView)
        applyTelegramPreferenceCard(holder, itemView)

        // display the title in multi-line to avoid ellipsis.
        try {
            val title = holder.findViewById(android.R.id.title) as? TextView
            val summary = holder.findViewById(android.R.id.summary) as? TextView
            if (title != null) {
                title.isSingleLine = false
                title.setTypeface(null, mTypeface)
            }

            summary?.setTypeface(null, mTypeface)

            val icon = holder.findViewById(android.R.id.icon) as? ImageView
            icon?.let {
                val (fallbackIcon, accentColor) = getPreferenceIconStyle()
                if (it.drawable == null || icon == null) {
                    it.setImageResource(fallbackIcon)
                }
                it.isVisible = true
                it.setPadding(dp(8), dp(8), dp(8), dp(8))
                it.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(12).toFloat()
                    setColor(accentColor)
                }
                ImageViewCompat.setImageTintList(it, ColorStateList.valueOf(Color.WHITE))
            }

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
                itemView.background = createPreferenceCardBackground()
            }
        } catch (e: Exception) {
            Timber.e(e, "onBindView")
        }

        super.onBindViewHolder(holder)
    }

    private fun applyTelegramPreferenceCard(holder: PreferenceViewHolder, itemView: View) {
        val horizontalPadding = dp(14)
        itemView.setPadding(horizontalPadding, dp(12), horizontalPadding, dp(12))
        (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = dp(8)
            params.marginEnd = dp(8)
            params.topMargin = dp(4)
            params.bottomMargin = dp(4)
            itemView.layoutParams = params
        }
        itemView.background = createPreferenceCardBackground()
    }

    private fun createPreferenceCardBackground() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(ThemeUtils.getColor(context, com.google.android.material.R.attr.colorSurface))
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun getPreferenceIconStyle(): Pair<Int, Int> {
        val descriptor = "${key.orEmpty()} ${title?.toString().orEmpty()}".lowercase()
        return when {
            "notification" in descriptor || "sound" in descriptor || "badge" in descriptor ->
                R.drawable.ic_settings_root_notification to 0xFFF04455.toInt()
            "privacy" in descriptor || "security" in descriptor || "password" in descriptor || "device" in descriptor || "session" in descriptor ->
                R.drawable.ic_settings_root_security_privacy to 0xFF34C759.toInt()
            "call" in descriptor || "video" in descriptor || "audio" in descriptor || "voice" in descriptor ->
                R.drawable.ic_settings_root_call to 0xFFFF8425.toInt()
            "language" in descriptor || "text" in descriptor || "theme" in descriptor || "appearance" in descriptor || "bubble" in descriptor ->
                R.drawable.ic_settings_root_preferences to 0xFFFF9F0A.toInt()
            "storage" in descriptor || "media" in descriptor || "download" in descriptor || "cache" in descriptor ->
                R.drawable.ic_settings_root_advanced to 0xFF4B7BF5.toInt()
            "account" in descriptor || "profile" in descriptor || "username" in descriptor ->
                R.drawable.ic_settings_root_general to 0xFF229ED9.toInt()
            else -> R.drawable.ic_settings_root_preferences to 0xFF8D63E6.toInt()
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
