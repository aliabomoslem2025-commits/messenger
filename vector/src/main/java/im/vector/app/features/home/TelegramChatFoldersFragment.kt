/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home

import android.content.ClipData
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.databinding.FragmentTelegramChatFoldersBinding
import im.vector.app.features.home.room.list.home.header.HomeRoomFilter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class TelegramChatFoldersFragment : Fragment(R.layout.fragment_telegram_chat_folders) {

    @Inject lateinit var chatFoldersStore: ChatFoldersStore

    private var _binding: FragmentTelegramChatFoldersBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentTelegramChatFoldersBinding.bind(view)

        binding.telegramFoldersSeparateSwitch.setOnCheckedChangeListener { _, checked ->
            chatFoldersStore.setSeparateChatsEnabled(checked)
        }
        binding.telegramFoldersCreateButton.setOnClickListener { showCreateFolderDialog() }
        binding.telegramFoldersRestoreButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.telegram_folder_restore_all)
                    .setMessage(R.string.telegram_folder_hero_description)
                    .setNegativeButton(R.string.telegram_folder_action_cancel, null)
                    .setPositiveButton(R.string.telegram_folder_action_save) { _, _ ->
                        chatFoldersStore.restoreDefaults()
                    }
                    .show()
        }

        chatFoldersStore.foldersFlow
                .onEach(::renderFolders)
                .launchIn(viewLifecycleOwner.lifecycleScope)
        chatFoldersStore.separateChatsFlow
                .onEach { binding.telegramFoldersSeparateSwitch.isChecked = it }
                .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    private fun renderFolders(folders: List<ChatFolderDefinition>) {
        val container = binding.telegramFoldersList
        container.removeAllViews()
        folders.forEachIndexed { index, folder ->
            val row = layoutInflater.inflate(R.layout.item_telegram_chat_folder, container, false)
            row.tag = folder.id
            row.findViewById<TextView>(R.id.telegramChatFolderName).text = folder.name
            row.findViewById<TextView>(R.id.telegramChatFolderFilter).text = filterLabel(folder.filter)
            val more = row.findViewById<ImageView>(R.id.telegramChatFolderMore)
            val drag = row.findViewById<ImageView>(R.id.telegramChatFolderDrag)

            more.isVisible = folder.filter != HomeRoomFilter.ALL
            more.setOnClickListener { anchor -> showFolderMenu(anchor, folder) }
            drag.setOnTouchListener { handle, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    val clip = ClipData.newPlainText("chat-folder-id", folder.id)
                    @Suppress("DEPRECATION")
                    handle.startDrag(clip, View.DragShadowBuilder(row), folder.id, 0)
                    true
                } else {
                    false
                }
            }
            row.setOnDragListener { target, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        target.alpha = 0.72f
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> {
                        target.alpha = 1f
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        target.alpha = 1f
                        val draggedId = event.clipData?.getItemAt(0)?.text?.toString()
                        val targetId = target.tag as? String
                        if (!draggedId.isNullOrBlank() && !targetId.isNullOrBlank()) {
                            val sourceIndex = folders.indexOfFirst { it.id == draggedId }
                            val targetIndex = folders.indexOfFirst { it.id == targetId }
                            if (sourceIndex >= 0 && targetIndex >= 0) {
                                chatFoldersStore.moveFolder(draggedId, targetIndex - sourceIndex)
                            }
                        }
                        true
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        target.alpha = 1f
                        true
                    }
                    else -> true
                }
            }
            container.addView(row)

            if (index < folders.lastIndex) {
                val divider = View(requireContext()).apply {
                    setBackgroundColor(resolveThemeColor(im.vector.lib.ui.styles.R.attr.vctr_content_quaternary))
                    layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            (resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
                    ).apply {
                        marginStart = (18 * resources.displayMetrics.density).roundToInt()
                        marginEnd = (18 * resources.displayMetrics.density).roundToInt()
                    }
                }
                container.addView(divider)
            }
        }
    }

    private fun showFolderMenu(anchor: View, folder: ChatFolderDefinition) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, ACTION_RENAME, 0, getString(R.string.telegram_folder_action_rename))
            menu.add(
                    0,
                    ACTION_TOGGLE,
                    1,
                    getString(if (folder.enabled) R.string.telegram_folder_action_hide else R.string.telegram_folder_action_show)
            )
            menu.add(0, ACTION_MOVE_UP, 2, getString(R.string.telegram_folder_action_move_up))
            menu.add(0, ACTION_MOVE_DOWN, 3, getString(R.string.telegram_folder_action_move_down))
            menu.add(0, ACTION_DELETE, 4, getString(R.string.telegram_folder_action_delete))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    ACTION_RENAME -> showRenameDialog(folder)
                    ACTION_TOGGLE -> chatFoldersStore.setFolderEnabled(folder.id, !folder.enabled)
                    ACTION_MOVE_UP -> chatFoldersStore.moveFolder(folder.id, -1)
                    ACTION_MOVE_DOWN -> chatFoldersStore.moveFolder(folder.id, 1)
                    ACTION_DELETE -> showDeleteDialog(folder)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun showCreateFolderDialog() {
        showFolderEditor(existing = null)
    }

    private fun showRenameDialog(folder: ChatFolderDefinition) {
        if (folder.filter == HomeRoomFilter.ALL) return
        showFolderEditor(existing = folder)
    }

    private fun showFolderEditor(existing: ChatFolderDefinition?) {
        val context = requireContext()
        val padding = (20 * resources.displayMetrics.density).roundToInt()
        val editor = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val nameInput = EditText(context).apply {
            hint = getString(R.string.telegram_folder_name_hint)
            setSingleLine(true)
            setText(existing?.name.orEmpty())
            contentDescription = getString(R.string.telegram_folder_name_hint)
        }
        val options = listOf(
                HomeRoomFilter.ALL,
                HomeRoomFilter.UNREADS,
                HomeRoomFilter.FAVOURITES,
                HomeRoomFilter.PEOPlE,
                HomeRoomFilter.ROOMS
        )
        val optionLabels = options.map(::filterLabel)
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, optionLabels)
            isEnabled = existing == null
            val selectedIndex = options.indexOf(existing?.filter ?: HomeRoomFilter.ALL).coerceAtLeast(0)
            setSelection(selectedIndex)
        }
        editor.addView(nameInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val hint = TextView(context).apply {
            text = getString(R.string.telegram_folder_choose_filter)
            setTextColor(resolveThemeColor(im.vector.lib.ui.styles.R.attr.vctr_content_secondary))
            setPadding(0, padding / 2, 0, 0)
        }
        editor.addView(hint)
        editor.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(if (existing == null) R.string.telegram_folder_create_new else R.string.telegram_folder_action_rename)
                .setView(editor)
                .setNegativeButton(R.string.telegram_folder_action_cancel, null)
                .setPositiveButton(R.string.telegram_folder_action_save, null)
                .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text?.toString().orEmpty().trim()
                when {
                    name.isBlank() -> nameInput.error = getString(R.string.telegram_folder_empty_name)
                    existing != null -> {
                        chatFoldersStore.renameFolder(existing.id, name)
                        dialog.dismiss()
                    }
                    else -> {
                        val filter = options[spinner.selectedItemPosition]
                        val created = chatFoldersStore.createFolder(name, filter)
                        if (created) {
                            dialog.dismiss()
                        } else {
                            Toast.makeText(requireContext(), R.string.telegram_folder_filter_exists, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(folder: ChatFolderDefinition) {
        if (folder.filter == HomeRoomFilter.ALL) return
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.telegram_folder_delete_title)
                .setMessage(R.string.telegram_folder_delete_message)
                .setNegativeButton(R.string.telegram_folder_action_cancel, null)
                .setPositiveButton(R.string.telegram_folder_action_delete) { _, _ ->
                    chatFoldersStore.deleteFolder(folder.id)
                }
                .show()
    }

    private fun filterLabel(filter: HomeRoomFilter): String = getString(
            when (filter) {
                HomeRoomFilter.ALL -> R.string.telegram_folder_filter_all
                HomeRoomFilter.UNREADS -> R.string.telegram_folder_filter_unread
                HomeRoomFilter.FAVOURITES -> R.string.telegram_folder_filter_favorites
                HomeRoomFilter.PEOPlE -> R.string.telegram_folder_filter_personal
                HomeRoomFilter.ROOMS -> R.string.telegram_folder_filter_groups
            }
    )

    private fun resolveThemeColor(attribute: Int): Int {
        val values = android.util.TypedValue()
        requireContext().theme.resolveAttribute(attribute, values, true)
        return if (values.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(requireContext(), values.resourceId)
        } else {
            values.data
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ACTION_RENAME = 100
        private const val ACTION_TOGGLE = 101
        private const val ACTION_MOVE_UP = 102
        private const val ACTION_MOVE_DOWN = 103
        private const val ACTION_DELETE = 104
    }
}
