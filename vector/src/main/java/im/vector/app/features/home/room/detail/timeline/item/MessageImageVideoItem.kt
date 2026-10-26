/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.files.LocalFilesHelper
import im.vector.app.core.glide.GlideApp
import im.vector.app.core.utils.DimensionConverter
import im.vector.app.features.home.room.detail.timeline.helper.ContentDownloadOverlayBinder
import im.vector.app.features.home.room.detail.timeline.helper.ContentUploadStateTrackerBinder
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.home.room.detail.timeline.style.granularRoundedCorners
import im.vector.app.features.attachments.TelegramMediaMarkers
import im.vector.app.features.media.ImageContentRenderer
import org.matrix.android.sdk.api.session.room.model.message.MessageType

@EpoxyModelClass
abstract class MessageImageVideoItem : AbsMessageItem<MessageImageVideoItem.Holder>() {

    @EpoxyAttribute
    lateinit var mediaData: ImageContentRenderer.Data

    @EpoxyAttribute
    var playable: Boolean = false

    @EpoxyAttribute
    var mode = ImageContentRenderer.Mode.THUMBNAIL

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var clickListener: ClickListener? = null

    @EpoxyAttribute
    lateinit var imageContentRenderer: ImageContentRenderer

    @EpoxyAttribute
    lateinit var contentUploadStateTrackerBinder: ContentUploadStateTrackerBinder

    @EpoxyAttribute
    lateinit var contentDownloadOverlayBinder: ContentDownloadOverlayBinder

    @EpoxyAttribute
    var mxcDownloadUrl: String? = null

    override fun bind(holder: Holder) {
        super.bind(holder)
        val messageLayout = baseAttributes.informationData.messageLayout
        val dimensionConverter = DimensionConverter(holder.view.resources)
        val isVideoNote = attributes.informationData.messageType == MessageType.MSGTYPE_VIDEO &&
                mediaData.filename.startsWith(TelegramMediaMarkers.VIDEO_NOTE_FILENAME_PREFIX)
        applyVideoNoteShape(holder, isVideoNote, dimensionConverter.dpToPx(184))
        val imageCornerTransformation = if (messageLayout is TimelineMessageLayout.Bubble) {
            messageLayout.cornersRadius.granularRoundedCorners()
        } else {
            RoundedCorners(dimensionConverter.dpToPx(8))
        }
        imageContentRenderer.render(mediaData, mode, holder.imageView, imageCornerTransformation)
        if (!attributes.informationData.sendState.hasFailed()) {
            contentUploadStateTrackerBinder.bind(
                    attributes.informationData.eventId,
                    LocalFilesHelper(holder.view.context).isLocalFile(mediaData.url),
                    holder.progressLayout
            )
        } else {
            holder.progressLayout.isVisible = false
        }
        holder.imageView.onClick(clickListener)
        holder.imageView.setOnLongClickListener(attributes.itemLongClickListener)
        ViewCompat.setTransitionName(holder.imageView, "imagePreview_${id()}")
        holder.mediaContentView.onClick(attributes.itemClickListener)
        holder.mediaContentView.setOnLongClickListener(attributes.itemLongClickListener)

        val isImageMessage = attributes.informationData.messageType in listOf(MessageType.MSGTYPE_IMAGE, MessageType.MSGTYPE_STICKER_LOCAL)
        val autoplayAnimatedImages = attributes.autoplayAnimatedImages

        holder.playContentView.visibility = if (playable && isImageMessage && autoplayAnimatedImages) {
            View.GONE
        } else if (playable) {
            View.VISIBLE
        } else {
            View.GONE
        }

        val downloadUrl = mxcDownloadUrl
        if (playable && !downloadUrl.isNullOrBlank()) {
            contentDownloadOverlayBinder.bind(
                    attributes.informationData.eventId,
                    downloadUrl,
                    holder.downloadProgress
            )
        } else {
            holder.downloadProgress.isVisible = false
        }
    }

    private fun applyVideoNoteShape(holder: Holder, isVideoNote: Boolean, diameterPx: Int) {
        if (!holder.hasOriginalMediaGeometry) {
            val originalParams = holder.mediaContentView.layoutParams
            holder.originalMediaWidth = originalParams.width
            holder.originalMediaHeight = originalParams.height
            holder.originalMediaOutlineProvider = holder.mediaContentView.outlineProvider
            holder.originalMediaClipToOutline = holder.mediaContentView.clipToOutline
            holder.originalImageScaleType = holder.imageView.scaleType
            holder.hasOriginalMediaGeometry = true
        }

        val params = holder.mediaContentView.layoutParams
        if (isVideoNote) {
            params.width = diameterPx
            params.height = diameterPx
            holder.mediaContentView.layoutParams = params
            holder.mediaContentView.outlineProvider = VIDEO_NOTE_CIRCLE_OUTLINE
            holder.mediaContentView.clipToOutline = true
            holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        } else {
            params.width = holder.originalMediaWidth
            params.height = holder.originalMediaHeight
            holder.mediaContentView.layoutParams = params
            holder.mediaContentView.outlineProvider = holder.originalMediaOutlineProvider
            holder.mediaContentView.clipToOutline = holder.originalMediaClipToOutline
            holder.imageView.scaleType = holder.originalImageScaleType
        }
    }

    override fun unbind(holder: Holder) {
        GlideApp.with(holder.view.context.applicationContext).clear(holder.imageView)
        imageContentRenderer.clear(holder.imageView)
        contentUploadStateTrackerBinder.unbind(attributes.informationData.eventId)
        contentDownloadOverlayBinder.unbind(attributes.informationData.eventId)
        holder.imageView.setOnClickListener(null)
        holder.imageView.setOnLongClickListener(null)
        super.unbind(holder)
    }

    override fun getViewStubId() = STUB_ID

    class Holder : AbsMessageItem.Holder(STUB_ID) {
        val progressLayout by bind<ViewGroup>(R.id.messageMediaUploadProgressLayout)
        val imageView by bind<ImageView>(R.id.messageThumbnailView)
        val playContentView by bind<ImageView>(R.id.messageMediaPlayView)
        val downloadProgress by bind<android.widget.ProgressBar>(R.id.messageMediaDownloadProgress)
        val mediaContentView by bind<ViewGroup>(R.id.messageContentMedia)

        var hasOriginalMediaGeometry = false
        var originalMediaWidth = ViewGroup.LayoutParams.WRAP_CONTENT
        var originalMediaHeight = ViewGroup.LayoutParams.WRAP_CONTENT
        var originalMediaOutlineProvider: ViewOutlineProvider? = null
        var originalMediaClipToOutline = false
        var originalImageScaleType = ImageView.ScaleType.FIT_CENTER
    }

    companion object {
        private val STUB_ID = R.id.messageContentMediaStub
        private val VIDEO_NOTE_CIRCLE_OUTLINE = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }
}
