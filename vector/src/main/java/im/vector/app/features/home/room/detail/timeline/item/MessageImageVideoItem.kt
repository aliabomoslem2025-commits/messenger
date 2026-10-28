/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.content.res.ColorStateList
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import com.bumptech.glide.load.resource.bitmap.CircleCrop
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
import im.vector.app.features.media.ImageContentRenderer
import im.vector.app.features.themes.ThemeUtils
import org.matrix.android.sdk.api.session.room.model.message.MessageType
import kotlin.math.abs
import kotlin.math.max

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

    @EpoxyAttribute
    var isMediaDownloaded: Boolean = false

    override fun bind(holder: Holder) {
        super.bind(holder)
        val messageLayout = baseAttributes.informationData.messageLayout
        val dimensionConverter = DimensionConverter(holder.view.resources)
        val mediaWidth = mediaData.width
        val mediaHeight = mediaData.height
        // A capture made by VideoNoteCaptureActivity carries a stable filename marker
        // through Matrix's m.video body. This makes the circle treatment survive upload,
        // sync and rendering on the recipient's device. Square videos from other clients
        // are also rendered as circles; regular landscape videos keep rounded rectangles.
        val isTaggedVideoNote = attributes.informationData.messageType == MessageType.MSGTYPE_VIDEO &&
                mediaData.filename.startsWith("video_note_", ignoreCase = true)
        val isSquareVideoNote = attributes.informationData.messageType == MessageType.MSGTYPE_VIDEO &&
                mediaWidth != null && mediaHeight != null &&
                abs(mediaWidth - mediaHeight) <= max(4, max(mediaWidth, mediaHeight) / 20)
        val isCircularVideoNote = isTaggedVideoNote || isSquareVideoNote
        holder.setCircularVideoNote(isCircularVideoNote, dimensionConverter.dpToPx(208))
        val imageCornerTransformation = when {
            isCircularVideoNote -> CircleCrop()
            messageLayout is TimelineMessageLayout.Bubble -> messageLayout.cornersRadius.granularRoundedCorners()
            else -> RoundedCorners(dimensionConverter.dpToPx(8))
        }
        holder.imageView.clipToOutline = false
        imageContentRenderer.render(mediaData, mode, holder.imageView, imageCornerTransformation)
        if (isCircularVideoNote) {
            holder.imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        }
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

        val isOutgoingBubble = (messageLayout as? TimelineMessageLayout.Bubble)?.isIncoming == false
        holder.downloadProgress.progressDrawable = ContextCompat.getDrawable(
                holder.view.context,
                if (isOutgoingBubble) R.drawable.bg_telegram_voice_download_progress_outgoing
                else R.drawable.bg_telegram_voice_download_progress
        )
        holder.downloadProgress.indeterminateDrawable = ContextCompat.getDrawable(
                holder.view.context,
                R.drawable.bg_telegram_voice_download_indeterminate
        )
        val progressTint = ThemeUtils.getColor(
                holder.view.context,
                if (isOutgoingBubble) im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text
                else im.vector.lib.ui.styles.R.attr.vctr_telegram_send_accent
        )
        holder.downloadProgress.indeterminateTintList = ColorStateList.valueOf(progressTint)
        holder.mediaContentView.onClick(attributes.itemClickListener)
        holder.mediaContentView.setOnLongClickListener(attributes.itemLongClickListener)

        val isImageMessage = attributes.informationData.messageType in listOf(MessageType.MSGTYPE_IMAGE, MessageType.MSGTYPE_STICKER_LOCAL)
        val autoplayAnimatedImages = attributes.autoplayAnimatedImages

        val downloadUrl = mxcDownloadUrl
        val isDownloadingCandidate = !isMediaDownloaded && !downloadUrl.isNullOrBlank()
        val showAnimatedPlayControl = playable && !(isImageMessage && autoplayAnimatedImages)
        holder.playContentView.visibility = if (isDownloadingCandidate || showAnimatedPlayControl) View.VISIBLE else View.GONE
        if (isDownloadingCandidate) {
            holder.playContentView.setImageResource(R.drawable.ic_telegram_download_arrow)
        } else {
            holder.playContentView.setImageResource(R.drawable.ic_material_play_circle)
        }

        if (!downloadUrl.isNullOrBlank()) {
            contentDownloadOverlayBinder.bind(
                    eventId = attributes.informationData.eventId,
                    mxcUrl = downloadUrl,
                    progress = holder.downloadProgress,
                    downloadIcon = holder.playContentView,
                    showPlayIconOnSuccess = showAnimatedPlayControl
            )
        } else {
            holder.downloadProgress.isVisible = false
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

        private var originalMediaWidth: Int? = null
        private var originalMediaHeight: Int? = null

        fun setCircularVideoNote(enabled: Boolean, sizePx: Int) {
            val params = mediaContentView.layoutParams ?: return
            if (originalMediaWidth == null) {
                originalMediaWidth = params.width
                originalMediaHeight = params.height
            }
            params.width = if (enabled) sizePx else originalMediaWidth ?: params.width
            params.height = if (enabled) sizePx else originalMediaHeight ?: params.height
            mediaContentView.layoutParams = params
            mediaContentView.clipToOutline = enabled
            mediaContentView.outlineProvider = if (enabled) CIRCLE_OUTLINE_PROVIDER else null
            if (enabled) mediaContentView.invalidateOutline()
        }

        companion object {
            private val CIRCLE_OUTLINE_PROVIDER = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
    }

    companion object {
        private val STUB_ID = R.id.messageContentMediaStub
    }
}
