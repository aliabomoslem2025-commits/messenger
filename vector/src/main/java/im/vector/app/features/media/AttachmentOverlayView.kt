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
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.Locale
import im.vector.app.R
import im.vector.app.databinding.MergeImageAttachmentOverlayBinding
import im.vector.lib.attachmentviewer.AttachmentEventListener
import im.vector.lib.attachmentviewer.AttachmentEvents
import org.matrix.android.sdk.api.session.file.ContentDownloadStateTracker
import org.matrix.android.sdk.api.session.file.FileService

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
    private var fileStateProvider: (() -> FileService.FileState?)? = null
    private var cancelDownload: (() -> Boolean)? = null
    private var networkDownloadActive = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var decryptionPollAttempts = 0
    // Lazily created because its callback accesses the binding initialized in init{}.
    private val decryptionPoll by lazy {
        object : Runnable {
            override fun run() {
                val state = fileStateProvider?.invoke()
                if (state is FileService.FileState.InCache && state.decryptedFileInCache) {
                    finishDownloadProgress(ready = true)
                } else if (++decryptionPollAttempts < DECRYPTION_POLL_ATTEMPTS) {
                    views.overlayDownloadProgress.isIndeterminate = true
                    views.overlayDownloadProgress.isVisible = true
                    mainHandler.postDelayed(this, DECRYPTION_POLL_INTERVAL_MS)
                } else {
                    finishDownloadProgress(ready = false)
                }
            }
        }
    }

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
            if (networkDownloadActive) {
                if (cancelDownload?.invoke() == true) {
                    networkDownloadActive = false
                    views.overlayDownloadButton.setImageResource(R.drawable.ic_material_save)
                }
            } else {
                interactionListener?.onDownload()
            }
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
    fun bindDownloadProgress(
            tracker: ContentDownloadStateTracker,
            url: String?,
            fileStateProvider: (() -> FileService.FileState?)? = null,
            cancelDownload: (() -> Boolean)? = null,
    ) {
        if (trackedDownloadUrl == url && downloadStateTracker === tracker) return
        unbindDownloadProgress()
        if (url.isNullOrBlank() || !url.startsWith("mxc://")) {
            views.overlayDownloadProgress.isVisible = false
            return
        }
        trackedDownloadUrl = url
        downloadStateTracker = tracker
        this.fileStateProvider = fileStateProvider
        this.cancelDownload = cancelDownload
        networkDownloadActive = fileStateProvider?.invoke() == FileService.FileState.Downloading
        views.overlayDownloadButton.setImageResource(
                if (networkDownloadActive) R.drawable.ic_cross else R.drawable.ic_material_save
        )
        val listener = object : ContentDownloadStateTracker.UpdateListener {
            override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
                when (state) {
                    ContentDownloadStateTracker.State.Idle,
                    is ContentDownloadStateTracker.State.Failure -> {
                        networkDownloadActive = false
                        views.overlayDownloadButton.setImageResource(R.drawable.ic_material_save)
                        mainHandler.removeCallbacks(decryptionPoll)
                        finishDownloadProgress(ready = false)
                    }
                    ContentDownloadStateTracker.State.Success -> {
                        networkDownloadActive = false
                        views.overlayDownloadButton.setImageResource(R.drawable.ic_material_save)
                        if (isDecryptedFileReady()) {
                            finishDownloadProgress(ready = true)
                        } else {
                            decryptionPollAttempts = 0
                            views.overlayDownloadProgress.isIndeterminate = true
                            views.overlayDownloadProgress.isVisible = true
                            mainHandler.removeCallbacks(decryptionPoll)
                            mainHandler.postDelayed(decryptionPoll, DECRYPTION_POLL_INTERVAL_MS)
                        }
                    }
                    is ContentDownloadStateTracker.State.Downloading -> {
                        networkDownloadActive = true
                        views.overlayDownloadButton.setImageResource(R.drawable.ic_cross)
                        val determinate = !state.indeterminate && state.total > 0L
                        views.overlayDownloadProgress.isIndeterminate = !determinate
                        if (determinate) {
                            views.overlayDownloadProgress.progress =
                                    (100.0 * state.current.toDouble() / state.total.toDouble()).toInt().coerceIn(0, 100)
                        }
                        views.overlayDownloadProgress.isVisible = true
                    }
                    ContentDownloadStateTracker.State.Decrypting -> {
                        networkDownloadActive = false
                        views.overlayDownloadButton.setImageResource(R.drawable.ic_material_save)
                        views.overlayDownloadProgress.isIndeterminate = true
                        views.overlayDownloadProgress.isVisible = true
                    }
                }
            }
        }
        downloadListener = listener
        tracker.track(url, listener)
    }

    private fun isDecryptedFileReady(): Boolean {
        val state = fileStateProvider?.invoke()
        return state is FileService.FileState.InCache && state.decryptedFileInCache
    }

    private fun finishDownloadProgress(ready: Boolean) {
        mainHandler.removeCallbacks(decryptionPoll)
        views.overlayDownloadProgress.isIndeterminate = false
        views.overlayDownloadProgress.progress = if (ready) 100 else 0
        views.overlayDownloadProgress.isVisible = false
    }

    private fun unbindDownloadProgress() {
        mainHandler.removeCallbacks(decryptionPoll)
        val url = trackedDownloadUrl
        val tracker = downloadStateTracker
        val listener = downloadListener
        if (url != null && tracker != null && listener != null) {
            tracker.unTrack(url, listener)
        }
        trackedDownloadUrl = null
        downloadStateTracker = null
        downloadListener = null
        fileStateProvider = null
        cancelDownload = null
        networkDownloadActive = false
        decryptionPollAttempts = 0
        views.overlayDownloadProgress.isVisible = false
    }

    companion object {
        private const val DECRYPTION_POLL_INTERVAL_MS = 500L
        private const val DECRYPTION_POLL_ATTEMPTS = 240
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
