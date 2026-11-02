/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.item

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.format.DateUtils
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.app.R
import im.vector.app.core.epoxy.ClickListener
import im.vector.app.core.epoxy.onClick
import im.vector.app.core.utils.TextUtils
import im.vector.app.features.home.room.detail.timeline.helper.AudioMessagePlaybackTracker
import im.vector.app.features.home.room.detail.timeline.helper.ContentDownloadStateTrackerBinder
import im.vector.app.features.home.room.detail.timeline.helper.ContentUploadStateTrackerBinder
import im.vector.app.features.home.room.detail.timeline.style.TimelineMessageLayout
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings

@EpoxyModelClass
abstract class MessageAudioItem : AbsMessageItem<MessageAudioItem.Holder>() {

    @EpoxyAttribute
    var filename: String = ""

    @EpoxyAttribute
    var mxcUrl: String = ""

    @EpoxyAttribute
    var duration: Int = 0

    @EpoxyAttribute
    var fileSize: Long = 0

    @EpoxyAttribute
    var izLocalFile = false

    @EpoxyAttribute
    open var isDownloaded: Boolean = false

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var onSeek: ((percentage: Float) -> Unit)? = null

    @EpoxyAttribute
    lateinit var contentUploadStateTrackerBinder: ContentUploadStateTrackerBinder

    @EpoxyAttribute
    lateinit var contentDownloadStateTrackerBinder: ContentDownloadStateTrackerBinder

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var playbackControlButtonClickListener: ClickListener? = null

    @EpoxyAttribute(EpoxyAttribute.Option.DoNotHash)
    var fullPlayerClickListener: ClickListener? = null

    @EpoxyAttribute
    lateinit var audioMessagePlaybackTracker: AudioMessagePlaybackTracker

    private var isUserSeeking = false

    override fun bind(holder: Holder) {
        super.bind(holder)
        renderSendState(holder.rootLayout, null)
        holder.isFileDownloaded = isDownloaded || izLocalFile
        holder.audioDownloadProgress.isVisible = false
        holder.audioDownloadProgress.progress = 0
        val outgoingBubble = (attributes.informationData.messageLayout as? TimelineMessageLayout.Bubble)?.isIncoming == false
        val actionColor = ThemeUtils.getColor(
                holder.view.context,
                if (outgoingBubble) im.vector.lib.ui.styles.R.attr.vctr_room_list_background
                else im.vector.lib.ui.styles.R.attr.vctr_telegram_send_accent
        )
        val iconColor = ThemeUtils.getColor(
                holder.view.context,
                if (outgoingBubble) im.vector.lib.ui.styles.R.attr.vctr_telegram_send_accent
                else im.vector.lib.ui.styles.R.attr.vctr_room_list_background
        )
        holder.audioPlaybackControlButton.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(actionColor)
        }
        holder.audioPlaybackControlButton.imageTintList = ColorStateList.valueOf(iconColor)
        holder.audioDownloadProgress.progressDrawable = androidx.core.content.ContextCompat.getDrawable(
                holder.view.context,
                if (outgoingBubble) R.drawable.bg_telegram_voice_download_progress_outgoing
                else R.drawable.bg_telegram_voice_download_progress
        )
        if (!holder.isFileDownloaded) {
            holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_telegram_download_arrow)
            holder.audioPlaybackControlButton.contentDescription =
                    holder.view.context.getString(R.string.telegram_audio_download_message)
        }
        bindViewAttributes(holder)
        bindUploadState(holder)
        if (!attributes.informationData.sendState.hasFailed() && !holder.isFileDownloaded && mxcUrl.isNotBlank()) {
            contentDownloadStateTrackerBinder.bindAudio(mxcUrl, holder)
        }
        applyLayoutTint(holder)
        bindSeekBar(holder)
        holder.audioPlaybackControlButton.setOnClickListener {
            if (mxcUrl.isNotBlank() && contentDownloadStateTrackerBinder.cancelDownload(mxcUrl)) {
                return@setOnClickListener
            }
            playbackControlButtonClickListener?.invoke(it)
        }
        holder.audioFullPlayerButton.setOnClickListener { fullPlayerClickListener?.invoke(it) }
        renderStateBasedOnAudioPlayback(holder)
    }

    private fun bindUploadState(holder: Holder) {
        if (attributes.informationData.sendState.hasFailed()) {
            holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_cross)
            holder.audioPlaybackControlButton.contentDescription =
                    holder.view.context.getString(CommonStrings.error_audio_message_unable_to_play, filename)
            holder.progressLayout.isVisible = false
        } else {
            contentUploadStateTrackerBinder.bind(attributes.informationData.eventId, izLocalFile, holder.progressLayout)
        }
    }

    private fun applyLayoutTint(holder: Holder) {
        val backgroundTint = if (attributes.informationData.messageLayout is TimelineMessageLayout.Bubble) {
            Color.TRANSPARENT
        } else {
            ThemeUtils.getColor(holder.view.context, im.vector.lib.ui.styles.R.attr.vctr_content_quinary)
        }
        holder.mainLayout.backgroundTintList = ColorStateList.valueOf(backgroundTint)
    }

    private fun bindViewAttributes(holder: Holder) {
        val formattedDuration = formatPlaybackTime(duration)
        val formattedFileSize = TextUtils.formatFileSize(holder.rootLayout.context, fileSize, true)
        val durationContentDescription = getPlaybackTimeContentDescription(holder.rootLayout.context, duration)

        holder.filenameView.text = filename
        holder.filenameView.onClick(attributes.itemClickListener)
        holder.audioPlaybackDuration.text = formattedDuration
        holder.fileSize.text = holder.rootLayout.context.getString(
                CommonStrings.audio_message_file_size, formattedFileSize
        )
        holder.mainLayout.contentDescription = holder.rootLayout.context.getString(
                CommonStrings.a11y_audio_message_item, filename, durationContentDescription, formattedFileSize
        )

        val bubbleLayout = attributes.informationData.messageLayout as? TimelineMessageLayout.Bubble
        val outgoingBubble = bubbleLayout != null && !bubbleLayout.isIncoming
        val bodyColorAttribute = if (outgoingBubble) {
            im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text
        } else {
            im.vector.lib.ui.styles.R.attr.vctr_content_primary
        }
        val metadataColorAttribute = if (outgoingBubble) {
            im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text
        } else {
            im.vector.lib.ui.styles.R.attr.vctr_content_tertiary
        }
        val context = holder.rootLayout.context
        holder.filenameView.setTextColor(ThemeUtils.getColor(context, bodyColorAttribute))
        holder.audioPlaybackDuration.setTextColor(ThemeUtils.getColor(context, metadataColorAttribute))
        holder.fileSize.setTextColor(ThemeUtils.getColor(context, metadataColorAttribute))
        holder.audioPlaybackTime.setTextColor(
                ThemeUtils.getColor(
                        context,
                        if (outgoingBubble) im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text
                        else im.vector.lib.ui.styles.R.attr.vctr_content_secondary
                )
        )
        val seekColorAttribute = if (outgoingBubble) {
            im.vector.lib.ui.styles.R.attr.vctr_message_bubble_outbound_text
        } else {
            im.vector.lib.ui.styles.R.attr.vctr_telegram_send_accent
        }
        val seekColor = ThemeUtils.getColor(context, seekColorAttribute)
        holder.audioSeekBar.progressTintList = ColorStateList.valueOf(seekColor)
        holder.audioSeekBar.thumbTintList = ColorStateList.valueOf(seekColor)
    }

    private fun bindSeekBar(holder: Holder) {
        holder.audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                holder.audioPlaybackTime.text = formatPlaybackTime(
                        (duration * (progress.toFloat() / 100)).toInt()
                )
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                isUserSeeking = false
                val percentage = seekBar.progress.toFloat() / 100
                onSeek?.invoke(percentage)
            }
        })
    }

    private fun renderStateBasedOnAudioPlayback(holder: Holder) {
        audioMessagePlaybackTracker.track(attributes.informationData.eventId) { state ->
            when (state) {
                is AudioMessagePlaybackTracker.Listener.State.Error,
                is AudioMessagePlaybackTracker.Listener.State.Idle -> renderIdleState(holder)
                is AudioMessagePlaybackTracker.Listener.State.Playing -> renderPlayingState(holder, state)
                is AudioMessagePlaybackTracker.Listener.State.Paused -> renderPausedState(holder, state)
                is AudioMessagePlaybackTracker.Listener.State.Recording -> Unit
            }
        }
    }

    private fun renderIdleState(holder: Holder) {
        if (holder.isFileDownloaded) {
            holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_play_pause_play)
            holder.audioPlaybackControlButton.contentDescription =
                    holder.view.context.getString(CommonStrings.a11y_play_audio_message, filename)
        } else {
            holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_telegram_download_arrow)
            holder.audioPlaybackControlButton.contentDescription =
                    holder.view.context.getString(R.string.telegram_audio_download_message)
        }
        holder.audioPlaybackTime.text = formatPlaybackTime(duration)
        holder.audioSeekBar.progress = 0
    }

    private fun renderPlayingState(holder: Holder, state: AudioMessagePlaybackTracker.Listener.State.Playing) {
        holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_play_pause_pause)
        holder.audioPlaybackControlButton.contentDescription =
                holder.view.context.getString(CommonStrings.a11y_pause_audio_message, filename)

        if (!isUserSeeking) {
            holder.audioPlaybackTime.text = formatPlaybackTime(state.playbackTime)
            holder.audioSeekBar.progress = (state.percentage * 100).toInt()
        }
    }

    private fun renderPausedState(holder: Holder, state: AudioMessagePlaybackTracker.Listener.State.Paused) {
        holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_play_pause_play)
        holder.audioPlaybackControlButton.contentDescription =
                holder.view.context.getString(CommonStrings.a11y_play_audio_message, filename)
        holder.audioPlaybackTime.text = formatPlaybackTime(state.playbackTime)
        holder.audioSeekBar.progress = (state.percentage * 100).toInt()
    }

    private fun formatPlaybackTime(time: Int) = DateUtils.formatElapsedTime((time / 1000).toLong())

    private fun getPlaybackTimeContentDescription(context: Context, time: Int): String {
        val formattedPlaybackTime = formatPlaybackTime(time)
        val (minutes, seconds) = formattedPlaybackTime.split(":").map { it.toIntOrNull() ?: 0 }
        return context.getString(CommonStrings.a11y_audio_playback_duration, minutes, seconds)
    }

    override fun unbind(holder: Holder) {
        super.unbind(holder)
        contentUploadStateTrackerBinder.unbind(attributes.informationData.eventId)
        contentDownloadStateTrackerBinder.unbind(mxcUrl)
        audioMessagePlaybackTracker.untrack(attributes.informationData.eventId)
        holder.audioFullPlayerButton.setOnClickListener(null)
    }

    override fun getViewStubId() = STUB_ID

    class Holder : AbsMessageItem.Holder(STUB_ID) {
        val rootLayout by bind<ViewGroup>(R.id.messageRootLayout)
        val mainLayout by bind<ViewGroup>(R.id.messageMainInnerLayout)
        val filenameView by bind<TextView>(R.id.messageFilenameView)
        val audioFullPlayerButton by bind<ImageButton>(R.id.audioFullPlayerButton)
        val audioPlaybackControlButton by bind<ImageButton>(R.id.audioPlaybackControlButton)
        val audioDownloadProgress by bind<android.widget.ProgressBar>(R.id.audioDownloadProgress)
        var isFileDownloaded: Boolean = false
        val audioPlaybackTime by bind<TextView>(R.id.audioPlaybackTime)
        val progressLayout by bind<ViewGroup>(R.id.messageFileUploadProgressLayout)
        val fileSize by bind<TextView>(R.id.fileSize)
        val audioPlaybackDuration by bind<TextView>(R.id.audioPlaybackDuration)
        val audioSeekBar by bind<SeekBar>(R.id.audioSeekBar)
    }

    companion object {
        private val STUB_ID = R.id.messageContentAudioStub
    }
}
