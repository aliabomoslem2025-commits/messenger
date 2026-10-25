/*
 * Copyright 2019-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.helper

import android.graphics.drawable.Drawable
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import androidx.core.view.isVisible
import dagger.hilt.android.scopes.ActivityScoped
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.features.home.room.detail.timeline.item.MessageFileItem
import im.vector.app.features.home.room.detail.timeline.item.MessageVoiceItem
import org.matrix.android.sdk.api.session.file.ContentDownloadStateTracker
import javax.inject.Inject

@ActivityScoped
class ContentDownloadStateTrackerBinder @Inject constructor(private val activeSessionHolder: ActiveSessionHolder) {

    private val updateListeners = mutableMapOf<String, StoppableContentDownloadListener>()

    fun bind(
            mxcUrl: String,
            holder: MessageFileItem.Holder
    ) {
        activeSessionHolder.getSafeActiveSession()?.also { session ->
            val downloadStateTracker = session.contentDownloadProgressTracker()
            val updateListener = ContentDownloadUpdater(holder)
            updateListeners[mxcUrl] = updateListener
            downloadStateTracker.track(mxcUrl, updateListener)
        }
    }

    fun bindVoice(
            mxcUrl: String,
            holder: MessageVoiceItem.Holder,
    ) {
        if (mxcUrl.isBlank()) return
        activeSessionHolder.getSafeActiveSession()?.also { session ->
            val tracker = session.contentDownloadProgressTracker()
            val listener = VoiceContentDownloadUpdater(holder)
            updateListeners[mxcUrl] = listener
            tracker.track(mxcUrl, listener)
        }
    }

    fun bindAudio(
            mxcUrl: String,
            holder: MessageAudioItem.Holder,
    ) {
        if (mxcUrl.isBlank()) return
        activeSessionHolder.getSafeActiveSession()?.also { session ->
            val tracker = session.contentDownloadProgressTracker()
            val listener = AudioContentDownloadUpdater(holder)
            updateListeners[mxcUrl] = listener
            tracker.track(mxcUrl, listener)
        }
    }

    fun unbind(mxcUrl: String) {
        activeSessionHolder.getSafeActiveSession()?.also { session ->
            val downloadStateTracker = session.contentDownloadProgressTracker()
            updateListeners[mxcUrl]?.also {
                it.stop()
                downloadStateTracker.unTrack(mxcUrl, it)
            }
        }
    }

    fun clear() {
        activeSessionHolder.getSafeActiveSession()?.also {
            it.contentDownloadProgressTracker().clear()
        }
    }
}

private interface StoppableContentDownloadListener : ContentDownloadStateTracker.UpdateListener {
    fun stop()
}

private class ContentDownloadUpdater(private val holder: MessageFileItem.Holder) : StoppableContentDownloadListener {

    override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
        when (state) {
            ContentDownloadStateTracker.State.Idle -> handleIdle()
            is ContentDownloadStateTracker.State.Downloading -> handleProgress(state)
            ContentDownloadStateTracker.State.Decrypting -> handleDecrypting()
            ContentDownloadStateTracker.State.Success -> handleSuccess()
            is ContentDownloadStateTracker.State.Failure -> handleFailure()
        }
    }

    private var animatedDrawable: AnimatedVectorDrawableCompat? = null
    private var animationLoopCallback = object : Animatable2Compat.AnimationCallback() {
        override fun onAnimationEnd(drawable: Drawable?) {
            animatedDrawable?.start()
        }
    }

    override fun stop() {
        animatedDrawable?.unregisterAnimationCallback(animationLoopCallback)
        animatedDrawable?.stop()
        animatedDrawable = null
    }

    private fun handleIdle() {
        holder.fileDownloadProgress.progress = 0
        holder.fileDownloadProgress.isIndeterminate = false
    }

    private fun handleDecrypting() {
        holder.fileDownloadProgress.isIndeterminate = true
    }

    private fun handleProgress(state: ContentDownloadStateTracker.State.Downloading) {
        doHandleProgress(state.current, state.total, state.indeterminate)
    }

    private fun doHandleProgress(current: Long, total: Long, indeterminate: Boolean) {
        holder.fileDownloadProgress.isIndeterminate = indeterminate || total <= 0L
        if (holder.fileDownloadProgress.isIndeterminate) {
            holder.fileDownloadProgress.progress = 0
        } else {
            val percent = (100.0 * current.toDouble() / total.toDouble()).toInt().coerceIn(0, 100)
            holder.fileDownloadProgress.progress = percent
        }
        if (animatedDrawable == null) {
            animatedDrawable = AnimatedVectorDrawableCompat.create(holder.view.context, R.drawable.ic_download_anim)
            holder.fileImageView.setImageDrawable(animatedDrawable)
            animatedDrawable?.start()
            animatedDrawable?.registerAnimationCallback(animationLoopCallback)
        }
    }

    private fun handleFailure() {
        stop()
        holder.fileDownloadProgress.isIndeterminate = false
        holder.fileDownloadProgress.progress = 0
        holder.fileImageView.setImageResource(R.drawable.ic_close_round)
    }

    private fun handleSuccess() {
        stop()
        holder.fileDownloadProgress.isIndeterminate = false
        holder.fileDownloadProgress.progress = 0
        holder.fileImageView.setImageResource(R.drawable.ic_paperclip)
    }
}

private class AudioContentDownloadUpdater(
        private val holder: MessageAudioItem.Holder,
) : StoppableContentDownloadListener {

    override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
        when (state) {
            ContentDownloadStateTracker.State.Idle -> {
                holder.audioDownloadProgress.isVisible = false
                if (!holder.isFileDownloaded) showDownloadAction()
            }
            is ContentDownloadStateTracker.State.Downloading -> {
                holder.audioDownloadProgress.isVisible = true
                holder.audioDownloadProgress.isIndeterminate = state.indeterminate || state.total <= 0L
                if (holder.audioDownloadProgress.isIndeterminate) {
                    holder.audioDownloadProgress.progress = 0
                } else {
                    holder.audioDownloadProgress.progress =
                            (100.0 * state.current.toDouble() / state.total.toDouble()).toInt().coerceIn(0, 100)
                }
                showDownloadAction()
            }
            ContentDownloadStateTracker.State.Decrypting -> {
                holder.audioDownloadProgress.isVisible = true
                holder.audioDownloadProgress.isIndeterminate = true
                showDownloadAction()
            }
            ContentDownloadStateTracker.State.Success -> {
                stop()
                holder.isFileDownloaded = true
                showPlaybackAction()
            }
            is ContentDownloadStateTracker.State.Failure -> {
                stop()
                holder.isFileDownloaded = false
                showDownloadAction()
            }
        }
    }

    override fun stop() {
        holder.audioDownloadProgress.isVisible = false
        holder.audioDownloadProgress.isIndeterminate = false
        holder.audioDownloadProgress.progress = 0
    }

    private fun showDownloadAction() {
        holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_telegram_download_arrow)
        holder.audioPlaybackControlButton.contentDescription =
                holder.view.context.getString(R.string.telegram_voice_download_message)
    }

    private fun showPlaybackAction() {
        holder.audioPlaybackControlButton.setImageResource(R.drawable.ic_play_pause_play)
        holder.audioPlaybackControlButton.contentDescription =
                holder.view.context.getString(
                        im.vector.lib.strings.CommonStrings.a11y_play_audio_message,
                        holder.filenameView.text?.toString().orEmpty(),
                )
    }
}

private class VoiceContentDownloadUpdater(
        private val holder: MessageVoiceItem.Holder,
) : StoppableContentDownloadListener {

    override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
        when (state) {
            ContentDownloadStateTracker.State.Idle -> {
                holder.voiceDownloadProgress.isVisible = false
                if (!holder.isFileDownloaded) showDownloadAction()
            }
            is ContentDownloadStateTracker.State.Downloading -> {
                holder.voiceDownloadProgress.isVisible = true
                holder.voiceDownloadProgress.isIndeterminate = state.indeterminate || state.total <= 0L
                if (!holder.voiceDownloadProgress.isIndeterminate) {
                    holder.voiceDownloadProgress.progress =
                            (100.0 * state.current.toDouble() / state.total.toDouble()).toInt().coerceIn(0, 100)
                }
                showDownloadAction()
            }
            ContentDownloadStateTracker.State.Decrypting -> {
                holder.voiceDownloadProgress.isVisible = true
                holder.voiceDownloadProgress.isIndeterminate = true
                showDownloadAction()
            }
            ContentDownloadStateTracker.State.Success -> {
                stop()
                holder.isFileDownloaded = true
                showPlaybackAction()
            }
            is ContentDownloadStateTracker.State.Failure -> {
                stop()
                holder.isFileDownloaded = false
                showDownloadAction()
            }
        }
    }

    override fun stop() {
        holder.voiceDownloadProgress.isVisible = false
        holder.voiceDownloadProgress.isIndeterminate = false
        holder.voiceDownloadProgress.progress = 0
    }

    private fun showDownloadAction() {
        holder.voicePlaybackControlButton.setImageResource(R.drawable.ic_telegram_download_arrow)
        holder.voicePlaybackControlButton.contentDescription =
                holder.view.context.getString(R.string.telegram_voice_download_message)
    }

    private fun showPlaybackAction() {
        holder.voicePlaybackControlButton.setImageResource(R.drawable.ic_play_pause_play)
        holder.voicePlaybackControlButton.contentDescription =
                holder.view.context.getString(im.vector.lib.strings.CommonStrings.a11y_play_voice_message)
    }
}
