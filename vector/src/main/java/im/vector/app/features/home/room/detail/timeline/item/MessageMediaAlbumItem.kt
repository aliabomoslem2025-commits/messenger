/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.text.method.MovementMethod
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.features.home.room.detail.timeline.helper.ContentDownloadOverlayBinder
import im.vector.app.features.home.room.detail.timeline.view.MediaAlbumGridView
import im.vector.app.features.media.ImageContentRenderer
import im.vector.app.features.media.VideoContentRenderer
import org.matrix.android.sdk.api.session.room.model.message.MessageImageInfoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageVideoContent
import org.matrix.android.sdk.api.session.room.model.message.MessageWithAttachmentContent

/**
 * One visual timeline row for a set of independently uploaded Matrix media events.
 * Each tile still uses its own Matrix event for opening, downloading and decryption.
 */
data class MediaAlbumTile(
        val eventId: String,
        val imageData: ImageContentRenderer.Data,
        val messageContent: MessageWithAttachmentContent,
        val imageContent: MessageImageInfoContent? = null,
        val videoContent: MessageVideoContent? = null,
        val videoData: VideoContentRenderer.Data? = null,
        val downloadUrl: String? = null,
        val isDownloaded: Boolean = false,
)

@EpoxyModelClass
abstract class MessageMediaAlbumItem : AbsMessageItem<MessageMediaAlbumItem.Holder>() {

    @EpoxyAttribute
    lateinit var mediaTiles: List<MediaAlbumTile>

    @EpoxyAttribute
    lateinit var imageContentRenderer: ImageContentRenderer

    @EpoxyAttribute
    lateinit var contentDownloadOverlayBinder: ContentDownloadOverlayBinder

    @EpoxyAttribute
    var caption: CharSequence? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var captionMovementMethod: MovementMethod? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var tileClickListener: ((MediaAlbumTile, View) -> Unit)? = null

    override fun bind(holder: Holder) {
        super.bind(holder)
        holder.albumGrid.bind(
                tiles = mediaTiles,
                imageContentRenderer = imageContentRenderer,
                downloadOverlayBinder = contentDownloadOverlayBinder,
                onTileClick = tileClickListener
        )
        holder.captionView.isVisible = !caption.isNullOrBlank()
        holder.captionView.text = caption
        holder.captionView.movementMethod = captionMovementMethod
        holder.captionView.setOnLongClickListener(attributes.itemLongClickListener)
    }

    override fun unbind(holder: Holder) {
        holder.albumGrid.clear()
        holder.captionView.text = null
        holder.captionView.movementMethod = null
        holder.captionView.setOnLongClickListener(null)
        super.unbind(holder)
    }

    // This row is reconstructed from its live group as timeline pages and local echoes update.
    override fun isCacheable(): Boolean = false

    override fun getViewStubId() = STUB_ID

    class Holder : AbsMessageItem.Holder(STUB_ID) {
        val albumGrid by bind<MediaAlbumGridView>(R.id.messageMediaAlbumGrid)
        val captionView by bind<TextView>(R.id.messageMediaAlbumCaption)
    }

    companion object {
        private val STUB_ID = R.id.messageContentMediaAlbumStub
    }
}
