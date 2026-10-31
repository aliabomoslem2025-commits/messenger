/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.media

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.widget.SeekBar
import android.content.res.ColorStateList
import androidx.core.view.isVisible
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.Locale
import im.vector.app.R
import im.vector.app.databinding.MergeImageAttachmentOverlayBinding
import im.vector.lib.attachmentviewer.AttachmentEventListener
import im.vector.lib.attachmentviewer.AttachmentEvents
import org.matrix.android.sdk.api.session.file.ContentDownloadStateTracker

class AttachmentOverlayView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr), AttachmentEventListener {

    var interactionListener: AttachmentInteractionListener? = null
    val views: MergeImageAttachmentOverlayBinding

    private var isPlaying = false
    private var suspendSeekBarUpdate = false
    private var trackedDownloadUrl: String? = null
    private var downloadStateTracker: ContentDownloadStateTracker? = null
    private var downloadListener: ContentDownloadStateTracker.UpdateListener? = null

    init {
        inflate(context, R.layout.merge_image_attachment_overlay, this)
        views = MergeImageAttachmentOverlayBinding.bind(this)
        setBackgroundColor(Color.TRANSPARENT)
        views.overlayBackButton.setOnClickListener {
            interactionListener?.onDismiss()
        }
        views.overlayShareButton.setOnClickListener {
            interactionListener?.onShare()
        }
        views.overlayDownloadButton.setOnClickListener {
            interactionListener?.onDownload()
        }
        views.overlayDownloadProgress.indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        views.overlayDownloadProgress.progressTintList = ColorStateList.valueOf(Color.WHITE)
        views.overlayPlayPauseButton.setOnClickListener {
            interactionListener?.onPlayPause(!isPlaying)
        }

        views.overlaySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    interactionListener?.videoSeekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                suspendSeekBarUpdate = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                suspendSeekBarUpdate = false
            }
        })
    }

    fun updateWith(counter: String, senderInfo: String) {
        views.overlayCounterText.text = counter
        views.overlayInfoText.text = senderInfo
    }


    /**
     * Bind the viewer's top-bar save affordance to the selected MXC resource. The SDK tracker
     * reports actual transfer bytes; E2EE decryption is shown as an indeterminate ring.
     */
    fun bindDownloadProgress(tracker: ContentDownloadStateTracker, url: String?) {
        if (trackedDownloadUrl == url && downloadStateTracker === tracker) return
        unbindDownloadProgress()
        if (url.isNullOrBlank() || !url.startsWith("mxc://")) {
            views.overlayDownloadProgress.isVisible = false
            return
        }
        trackedDownloadUrl = url
        downloadStateTracker = tracker
        val listener = object : ContentDownloadStateTracker.UpdateListener {
            override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
                when (state) {
                    ContentDownloadStateTracker.State.Idle,
                    is ContentDownloadStateTracker.State.Failure,
                    ContentDownloadStateTracker.State.Success -> {
                        views.overlayDownloadProgress.isVisible = false
                        views.overlayDownloadProgress.isIndeterminate = false
                        views.overlayDownloadProgress.progress =
                                if (state is ContentDownloadStateTracker.State.Success) 100 else 0
                    }
                    is ContentDownloadStateTracker.State.Downloading -> {
                        val determinate = !state.indeterminate && state.total > 0L
                        views.overlayDownloadProgress.isIndeterminate = !determinate
                        if (determinate) {
                            views.overlayDownloadProgress.progress =
                                    (100.0 * state.current.toDouble() / state.total.toDouble()).toInt().coerceIn(0, 100)
                        }
                        views.overlayDownloadProgress.isVisible = true
                    }
                    ContentDownloadStateTracker.State.Decrypting -> {
                        views.overlayDownloadProgress.isIndeterminate = true
                        views.overlayDownloadProgress.isVisible = true
                    }
                }
            }
        }
        downloadListener = listener
        tracker.track(url, listener)
    }

    private fun unbindDownloadProgress() {
        val url = trackedDownloadUrl
        val tracker = downloadStateTracker
        val listener = downloadListener
        if (url != null && tracker != null && listener != null) {
            tracker.unTrack(url, listener)
        }
        trackedDownloadUrl = null
        downloadStateTracker = null
        downloadListener = null
        views.overlayDownloadProgress.isVisible = false
    }

    override fun onDetachedFromWindow() {
        unbindDownloadProgress()
        super.onDetachedFromWindow()
    }

    private fun formatPlaybackTime(milliseconds: Int): String {
        val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
        val seconds = totalSeconds % 60
        val totalMinutes = totalSeconds / 60
        return if (totalMinutes >= 60) {
            String.format(
                    Locale.ROOT,
                    "%d:%02d:%02d",
                    totalMinutes / 60,
                    totalMinutes % 60,
                    seconds
            )
        } else {
            String.format(Locale.ROOT, "%d:%02d", totalMinutes, seconds)
        }
    }

    override fun onEvent(event: AttachmentEvents) {
        when (event) {
            is AttachmentEvents.ContinueVideoPlaybackInBackground,
            AttachmentEvents.StopBackgroundVideoPlayback -> Unit
            is AttachmentEvents.VideoEvent -> {
                views.overlayPlayPauseButton.setImageResource(if (!event.isPlaying) R.drawable.ic_play_arrow else R.drawable.ic_pause)
                views.overlayCurrentTimeText.text = formatPlaybackTime(event.progress)
                views.overlayDurationText.text = formatPlaybackTime(event.duration)
                if (!suspendSeekBarUpdate) {
                    val safeDuration = (if (event.duration <= 0) 100 else event.duration).toFloat()
                    val percent = ((event.progress.coerceAtLeast(0) / safeDuration) * 100f).toInt().coerceIn(0, 100)
                    isPlaying = event.isPlaying
                    views.overlaySeekBar.progress = percent
                }
            }
        }
    }
}
