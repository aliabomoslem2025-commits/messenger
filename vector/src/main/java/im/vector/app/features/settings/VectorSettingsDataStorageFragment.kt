/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.settings

import android.content.Context
import android.os.Bundle
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.preference.VectorPreference
import im.vector.app.features.analytics.plan.MobileScreen
import im.vector.lib.strings.CommonStrings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import javax.inject.Inject

@AndroidEntryPoint
class VectorSettingsDataStorageFragment : VectorSettingsBaseFragment() {

    override var titleRes: Int = R.string.telegram_settings_storage_title
    override val preferenceXmlRes = R.xml.vector_settings_data_storage

    @Inject lateinit var vectorPreferences: VectorPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analyticsScreenName = MobileScreen.ScreenName.SettingsPreferences
    }

    override fun bindPref() {
        findPreference<VectorPreference>(VectorPreferences.SETTINGS_MEDIA_SAVING_PERIOD_KEY)!!.let { preference ->
            preference.summary = vectorPreferences.getSelectedMediasSavingPeriodString()
            preference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                context?.let { context: Context ->
                    MaterialAlertDialogBuilder(context)
                            .setSingleChoiceItems(
                                    im.vector.lib.strings.R.array.media_saving_choice,
                                    vectorPreferences.getSelectedMediasSavingPeriod()
                            ) { dialog, selected ->
                                vectorPreferences.setSelectedMediasSavingPeriod(selected)
                                dialog.dismiss()
                                preference.summary = vectorPreferences.getSelectedMediasSavingPeriodString()
                            }
                            .show()
                }
                true
            }
        }
    }
}
