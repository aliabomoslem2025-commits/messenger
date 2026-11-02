/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.attachments

import org.matrix.android.sdk.api.session.content.ContentAttachmentData

fun ContentAttachmentData.isPreviewable(): Boolean {
    // Every image and video selected from the gallery must pass through the preview so
    // the user can review the selection and add a caption before sending.
    return type == ContentAttachmentData.Type.VIDEO ||
            (type == ContentAttachmentData.Type.IMAGE && getSafeMimeType()?.startsWith("image/") != false)
}

data class GroupedContentAttachmentData(
        val previewables: List<ContentAttachmentData>,
        val notPreviewables: List<ContentAttachmentData>
)

fun List<ContentAttachmentData>.toGroupedContentAttachmentData(): GroupedContentAttachmentData {
    return groupBy { it.isPreviewable() }
            .let {
                GroupedContentAttachmentData(
                        it[true].orEmpty(),
                        it[false].orEmpty()
                )
            }
}
