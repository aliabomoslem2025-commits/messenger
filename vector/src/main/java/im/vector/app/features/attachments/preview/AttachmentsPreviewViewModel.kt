/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.attachments.preview

import im.vector.app.core.platform.VectorViewModel

class AttachmentsPreviewViewModel(initialState: AttachmentsPreviewViewState) :
        VectorViewModel<AttachmentsPreviewViewState, AttachmentsPreviewAction, AttachmentsPreviewViewEvents>(initialState) {

    override fun handle(action: AttachmentsPreviewAction) {
        when (action) {
            is AttachmentsPreviewAction.SetCurrentAttachment -> handleSetCurrentAttachment(action)
            is AttachmentsPreviewAction.ToggleAttachmentSelection -> handleToggleAttachmentSelection(action)
            is AttachmentsPreviewAction.UpdatePathOfCurrentAttachment -> handleUpdatePathOfCurrentAttachment(action)
            is AttachmentsPreviewAction.UpdateCaption -> handleUpdateCaption(action.caption)
            AttachmentsPreviewAction.RemoveCurrentAttachment -> handleRemoveCurrentAttachment()
        }
    }

    private fun handleRemoveCurrentAttachment() = withState { state ->
        val currentAttachment = state.attachments.getOrNull(state.currentAttachmentIndex)
        val selectedUris = state.selectedAttachmentUris.takeIf { it.isNotEmpty() }
                ?: currentAttachment?.let { setOf(it.queryUri.toString()) }
                ?: emptySet()
        if (selectedUris.isEmpty()) return@withState

        val currentUri = currentAttachment?.queryUri?.toString()
        val attachments = state.attachments.filterNot { it.queryUri.toString() in selectedUris }
        val preservedIndex = currentUri?.let { uri ->
            attachments.indexOfFirst { it.queryUri.toString() == uri }.takeIf { it >= 0 }
        }
        val newIndex = preservedIndex
                ?: state.currentAttachmentIndex.coerceAtMost(attachments.lastIndex).coerceAtLeast(0)
        setState {
            copy(attachments = attachments, currentAttachmentIndex = newIndex, selectedAttachmentUris = emptySet())
        }
    }

    private fun handleToggleAttachmentSelection(action: AttachmentsPreviewAction.ToggleAttachmentSelection) = withState { state ->
        if (state.attachments.none { it.queryUri.toString() == action.uri }) return@withState
        val selected = if (action.uri in state.selectedAttachmentUris) {
            state.selectedAttachmentUris - action.uri
        } else {
            state.selectedAttachmentUris + action.uri
        }
        setState { copy(selectedAttachmentUris = selected) }
    }

    private fun handleUpdatePathOfCurrentAttachment(action: AttachmentsPreviewAction.UpdatePathOfCurrentAttachment) = withState {
        val currentAttachment = it.attachments.getOrNull(it.currentAttachmentIndex) ?: return@withState
        val oldUri = currentAttachment.queryUri.toString()
        val attachments = it.attachments.mapIndexed { index, contentAttachmentData ->
            if (index == it.currentAttachmentIndex) contentAttachmentData.copy(queryUri = action.newUri) else contentAttachmentData
        }
        val updatedSelection = if (oldUri in it.selectedAttachmentUris) {
            (it.selectedAttachmentUris - oldUri) + action.newUri.toString()
        } else it.selectedAttachmentUris
        setState { copy(attachments = attachments, selectedAttachmentUris = updatedSelection) }
    }

    private fun handleSetCurrentAttachment(action: AttachmentsPreviewAction.SetCurrentAttachment) = withState { state ->
        val index = action.index.coerceIn(0, (state.attachments.size - 1).coerceAtLeast(0))
        setState { copy(currentAttachmentIndex = index) }
    }

    private fun handleUpdateCaption(caption: String) = withState { state ->
        if (state.caption != caption) {
            setState { copy(caption = caption) }
        }
    }
}
