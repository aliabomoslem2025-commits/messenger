/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.databinding.FragmentTelegramSettingsBinding
import im.vector.app.features.navigation.SettingsActivityPayload
import im.vector.app.features.settings.VectorSettingsActivity
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.util.MatrixItem
import org.matrix.android.sdk.flow.flow
import org.matrix.android.sdk.flow.unwrap
import javax.inject.Inject

/**
 * Telegram-inspired settings landing page embedded in the home tab.
 *
 * Every row opens an existing Matrix/Element settings flow. The page is only a
 * presentation/navigation layer: it does not create local fake settings or account data.
 */
@AndroidEntryPoint
class TelegramSettingsFragment : Fragment(R.layout.fragment_telegram_settings) {

    @Inject lateinit var session: Session
    @Inject lateinit var avatarRenderer: AvatarRenderer

    private var _binding: FragmentTelegramSettingsBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTelegramSettingsBinding.bind(view)
        buildSettingsRows()
        observeCurrentUser()
        binding.telegramSettingsProfileHeader.setOnClickListener {
            openSettings(SettingsActivityPayload.General)
        }
    }

    private fun observeCurrentUser() {
        session.flow()
                .liveUser(session.myUserId)
                .unwrap()
                .onEach { user ->
                    val displayName = user.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?: session.myUserId.removePrefix("@").substringBefore(":")
                    binding.telegramSettingsDisplayName.text = displayName
                    binding.telegramSettingsUserId.text = session.myUserId
                    avatarRenderer.render(
                            MatrixItem.UserItem(session.myUserId, user.displayName, user.avatarUrl),
                            binding.telegramSettingsAvatar
                    )
                }
                .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun buildSettingsRows() {
        val primary = binding.telegramSettingsPrimaryCard
        val secondary = binding.telegramSettingsSecondaryCard

        addRow(
                primary,
                getString(R.string.telegram_settings_account_title),
                getString(R.string.telegram_settings_account_summary),
                R.drawable.ic_settings_root_general,
                0xFF229ED9.toInt(),
                SettingsActivityPayload.General
        )
        addRow(
                primary,
                getString(R.string.telegram_settings_chat_title),
                getString(R.string.telegram_settings_chat_summary),
                R.drawable.ic_settings_root_preferences,
                0xFFFF9F0A.toInt(),
                SettingsActivityPayload.Preferences
        )
        addRow(
                primary,
                getString(R.string.telegram_settings_privacy_title),
                getString(R.string.telegram_settings_privacy_summary),
                R.drawable.ic_settings_root_security_privacy,
                0xFF34C759.toInt(),
                SettingsActivityPayload.SecurityPrivacy
        )
        addRow(
                primary,
                getString(R.string.telegram_settings_notifications_title),
                getString(R.string.telegram_settings_notifications_summary),
                R.drawable.ic_settings_root_notification,
                0xFFF04455.toInt(),
                SettingsActivityPayload.Notifications
        )
        addRow(
                primary,
                getString(R.string.telegram_settings_storage_title),
                getString(R.string.telegram_settings_storage_summary),
                R.drawable.ic_settings_root_advanced,
                0xFF4B7BF5.toInt(),
                SettingsActivityPayload.Preferences
        )
        addRow(
                primary,
                getString(R.string.telegram_settings_devices_title),
                getString(R.string.telegram_settings_devices_summary),
                R.drawable.ic_device_type_desktop,
                0xFF1FB6C9.toInt(),
                SettingsActivityPayload.SecurityPrivacyManageSessions
        )
        addRow(
                primary,
                getString(R.string.telegram_settings_language_title),
                getString(R.string.telegram_settings_language_summary),
                R.drawable.ic_settings_root_preferences,
                0xFFAE5BEF.toInt(),
                SettingsActivityPayload.Language
        )

        addRow(
                secondary,
                getString(R.string.telegram_settings_calls_title),
                getString(R.string.telegram_settings_calls_summary),
                R.drawable.ic_settings_root_call,
                0xFFFF8425.toInt(),
                SettingsActivityPayload.VoiceVideo
        )
        addRow(
                secondary,
                getString(R.string.telegram_settings_help_title),
                getString(R.string.telegram_settings_help_summary),
                R.drawable.ic_settings_root_help_about,
                0xFF6758F5.toInt(),
                SettingsActivityPayload.HelpAbout
        )
        addRow(
                secondary,
                getString(R.string.telegram_settings_legal_title),
                getString(R.string.telegram_settings_legal_summary),
                R.drawable.ic_settings_root_legals,
                0xFF8D63E6.toInt(),
                SettingsActivityPayload.Legal
        )
    }

    private fun addRow(
            container: LinearLayout,
            title: String,
            summary: String,
            iconRes: Int,
            iconColor: Int,
            payload: SettingsActivityPayload
    ) {
        val row = layoutInflater.inflate(R.layout.item_telegram_settings_row, container, false)
        val icon = row.findViewById<ImageView>(R.id.telegramSettingsRowIcon)
        row.findViewById<TextView>(R.id.telegramSettingsRowTitle).text = title
        row.findViewById<TextView>(R.id.telegramSettingsRowSummary).text = summary

        icon.setImageResource(iconRes)
        icon.setColorFilter(Color.WHITE)
        icon.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(iconColor)
        }

        row.contentDescription = "$title. $summary"
        row.setOnClickListener { openSettings(payload) }
        container.addView(row)
    }

    private fun openSettings(payload: SettingsActivityPayload) {
        startActivity(VectorSettingsActivity.getIntent(requireContext(), payload))
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
