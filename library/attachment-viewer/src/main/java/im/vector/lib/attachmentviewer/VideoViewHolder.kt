/*
 * Copyright 2020-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.lib.attachmentviewer

import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import im.vector.lib.attachmentviewer.databinding.ItemVideoAttachmentBinding
import im.vector.lib.core.utils.timer.CountUpTimer
import java.io.File
import java.lang.ref.WeakReference

// TODO, it would be probably better to use a unique media player
// for better customization and control
// But for now VideoView is enough, it released player when detached, we use a timer to update progress
class VideoViewHolder constructor(itemView: View) :
        BaseViewHolder(itemView) {

    private var isSelected = false
    private var mVideoPath: String? = null
    private var countUpTimer: CountUpTimer? = null
    private var progress: Int = 0
    private var wasPaused = false

    var eventListener: WeakReference<AttachmentEventListener>? = null

    val views = ItemVideoAttachmentBinding.bind(itemView)

    internal val target = DefaultVideoLoaderTarget(this, views.videoThumbnailImage)

    override fun onRecycled() {
        super.onRecycled()
        stopTimer()
        mVideoPath = null
    }

    fun videoReady(file: File) {
        mVideoPath = file.path
        if (isSelected) {
            startPlaying()
        }
    }

    fun videoReady(path: String) {
        mVideoPath = path
        if (isSelected) {
            startPlaying()
        }
    }

    fun videoFileLoadError() {
    }

    override fun entersBackground() {
        if (views.videoView.isPlaying) {
            progress = views.videoView.currentPosition
            // VideoView releases its decoder when stopped. Ask the app layer to keep
            // the media audio alive in a foreground service while the viewer is hidden.
            mVideoPath?.let { path ->
                eventListener?.get()?.onEvent(
                        AttachmentEvents.ContinueVideoPlaybackInBackground(path, progress)
                )
            }
            stopTimer()
            views.videoView.stopPlayback()
        }
    }

    override fun entersForeground() {
        // Stop the audio-only fallback started while the viewer was backgrounded.
        eventListener?.get()?.onEvent(AttachmentEvents.StopBackgroundVideoPlayback)
        // Picture-in-picture keeps the original VideoView alive. Do not rebuild it or
        // reset its playback position when the Activity returns to the foreground.
        if (views.videoView.isPlaying) {
            isSelected = true
            return
        }
        onSelected(isSelected)
    }

    fun isVideoPlaying(): Boolean = views.videoView.isPlaying

    fun getVideoSize(): Pair<Int, Int> =
            views.videoView.width.coerceAtLeast(1) to views.videoView.height.coerceAtLeast(1)

    override fun onSelected(selected: Boolean) {
        if (!selected) {
            if (views.videoView.isPlaying) {
                progress = views.videoView.currentPosition
                views.videoView.stopPlayback()
            } else {
                progress = 0
            }
            stopTimer()
        } else {
            if (mVideoPath != null) {
                startPlaying()
            }
        }
        isSelected = selected
    }

    private fun startPlaying() {
        views.videoThumbnailImage.isVisible = false
        views.videoLoaderProgress.isVisible = false
        views.videoView.isVisible = true

        views.videoView.setOnPreparedListener {
            stopTimer()
            countUpTimer = CountUpTimer(intervalInMs = 100).also {
                it.tickListener = CountUpTimer.TickListener {
                    val duration = views.videoView.duration
                    val progress = views.videoView.currentPosition
                    val isPlaying = views.videoView.isPlaying
                    //                        Log.v("FOO", "isPlaying $isPlaying $progress/$duration")
                    eventListener?.get()?.onEvent(AttachmentEvents.VideoEvent(isPlaying, progress, duration))
                }
                it.start()
            }
        }
        try {
            views.videoView.setVideoPath(mVideoPath)
        } catch (failure: Throwable) {
            // Couldn't open
            Log.v(VideoViewHolder::class.java.name, "Failed to start video")
        }

        if (!wasPaused) {
            views.videoView.start()
            if (progress > 0) {
                views.videoView.seekTo(progress)
            }
        }
    }

    private fun stopTimer() {
        countUpTimer?.stop()
        countUpTimer = null
    }

    override fun handleCommand(commands: AttachmentCommands) {
        if (!isSelected) return
        when (commands) {
            AttachmentCommands.StartVideo -> {
                wasPaused = false
                views.videoView.start()
            }
            AttachmentCommands.PauseVideo -> {
                if (views.videoView.isPlaying) {
                    progress = views.videoView.currentPosition
                }
                wasPaused = true
                views.videoView.pause()
            }
            is AttachmentCommands.SeekTo -> {
                val duration = views.videoView.duration
                if (duration > 0) {
                    val seekDuration = duration * (commands.percentProgress / 100f)
                    views.videoView.seekTo(seekDuration.toInt())
                }
            }
        }
    }

    override fun bind(attachmentInfo: AttachmentInfo) {
        super.bind(attachmentInfo)
        progress = 0
        wasPaused = false
    }
}
