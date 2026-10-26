/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.core.preference

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import im.vector.app.R
import im.vector.app.features.themes.ThemeUtils

/**
 * Consistent card and icon styling for every setting preference, including switches
 * and drop-down preferences (which do not derive from VectorPreference).
 */
internal object TelegramPreferenceStyler {

    fun apply(preference: Preference, holder: PreferenceViewHolder) {
        val itemView = holder.itemView
        val density = itemView.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        itemView.setPadding(dp(14), dp(12), dp(14), dp(12))
        (itemView.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = dp(8)
            params.marginEnd = dp(8)
            params.topMargin = dp(4)
            params.bottomMargin = dp(4)
            itemView.layoutParams = params
        }
        itemView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(18).toFloat()
            setColor(ThemeUtils.getColor(itemView.context, com.google.android.material.R.attr.colorSurface))
        }

        val icon = holder.findViewById(android.R.id.icon) as? ImageView ?: return
        val (iconRes, accent) = iconStyle(preference)
        if (icon.drawable == null) icon.setImageResource(iconRes)
        icon.visibility = View.VISIBLE
        icon.setPadding(dp(8), dp(8), dp(8), dp(8))
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(accent)
        }
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(Color.WHITE))
    }

    private fun iconStyle(preference: Preference): Pair<Int, Int> {
        val descriptor = "${preference.key.orEmpty()} ${preference.title?.toString().orEmpty()}".lowercase()
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
}
