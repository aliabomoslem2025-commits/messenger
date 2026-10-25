/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.settings

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.preference.VectorPreference
import im.vector.app.features.analytics.plan.MobileScreen
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings

@AndroidEntryPoint
class VectorSettingsRootFragment :
        VectorSettingsBaseFragment() {

    override var titleRes: Int = CommonStrings.title_activity_settings
    override val preferenceXmlRes = R.xml.vector_settings_root

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsScreenName = MobileScreen.ScreenName.Settings
    }

    override fun bindPref() {
        val iconColors = mapOf(
                "settings_general" to im.vector.lib.ui.styles.R.color.telegram_settings_blue,
                "settings_notifications" to im.vector.lib.ui.styles.R.color.telegram_settings_red,
                "settings_preferences" to im.vector.lib.ui.styles.R.color.telegram_settings_orange,
                "settings_voice_video" to im.vector.lib.ui.styles.R.color.telegram_settings_cyan,
                "settings_security_privacy" to im.vector.lib.ui.styles.R.color.telegram_settings_green,
                "settings_labs" to im.vector.lib.ui.styles.R.color.telegram_settings_purple,
                "settings_advanced" to im.vector.lib.ui.styles.R.color.telegram_settings_indigo,
                "settings_help_about" to im.vector.lib.ui.styles.R.color.telegram_settings_deep_orange,
                "settings_legals" to im.vector.lib.ui.styles.R.color.telegram_settings_violet
        )

        for (i in 0 until preferenceScreen.preferenceCount) {
            (preferenceScreen.getPreference(i) as? VectorPreference)?.let { preference ->
                preference.layoutResource = R.layout.item_telegram_settings_preference
                preference.tintIcon = false
                preference.iconBackgroundColor = iconColors[preference.key]?.let {
                    ContextCompat.getColor(requireContext(), it)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setBackgroundColor(
                ThemeUtils.getColor(requireContext(), im.vector.lib.ui.styles.R.attr.vctr_telegram_settings_background)
        )
        val verticalPadding = (12 * resources.displayMetrics.density).toInt()
        listView.updatePadding(top = verticalPadding, bottom = verticalPadding)
        listView.clipToPadding = false
        listView.overScrollMode = View.OVER_SCROLL_NEVER
    }
}
