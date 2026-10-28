/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.timeline.helper

import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.isVisible
import dagger.hilt.android.scopes.ActivityScoped
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import org.matrix.android.sdk.api.session.file.ContentDownloadStateTracker
import javax.inject.Inject

/**
 * Binds a circular media overlay to the Matrix SDK download state for a video event.
 * This observes the SDK tracker; it does not start a second download or synthesize progress.
 */
@ActivityScoped
class ContentDownloadOverlayBinder @Inject constructor(
        private val activeSessionHolder: ActiveSessionHolder,
) {

    private data class Registration(
            val mxcUrl: String,
            val listener: ContentDownloadStateTracker.UpdateListener,
    )

    private val registrations = mutableMapOf<String, Registration>()

    fun bind(
            eventId: String,
            mxcUrl: String,
            progress: ProgressBar,
            downloadIcon: ImageView? = null,
            showPlayIconOnSuccess: Boolean = false
    ) {
        unbind(eventId)
        val session = activeSessionHolder.getSafeActiveSession()
        if (session == null || !mxcUrl.startsWith("mxc://")) {
            progress.isVisible = false
            return
        }

        progress.max = 100
        fun renderDownloadIcon(isDownloaded: Boolean) {
            downloadIcon ?: return
            if (isDownloaded) {
                if (showPlayIconOnSuccess) {
                    downloadIcon.setImageResource(R.drawable.ic_material_play_circle)
                    downloadIcon.isVisible = true
                } else {
                    downloadIcon.isVisible = false
                }
            } else {
                downloadIcon.setImageResource(R.drawable.ic_telegram_download_arrow)
                downloadIcon.isVisible = true
            }
        }

        // A remote, not-yet-cached media item starts with a download affordance. The
        // progress indicator and icon are both driven by the Matrix SDK download tracker.
        renderDownloadIcon(isDownloaded = false)
        val listener = object : ContentDownloadStateTracker.UpdateListener {
            override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
                when (state) {
                    ContentDownloadStateTracker.State.Idle -> {
                        progress.progress = 0
                        progress.isIndeterminate = false
                        progress.isVisible = false
                        renderDownloadIcon(isDownloaded = false)
                    }
                    is ContentDownloadStateTracker.State.Downloading -> {
                        progress.isIndeterminate = false
                        progress.progress = if (state.total > 0L) {
                            (100.0 * state.current.toDouble() / state.total.toDouble()).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        progress.isVisible = true
                    }
                    ContentDownloadStateTracker.State.Decrypting -> {
                        progress.isIndeterminate = true
                        progress.isVisible = true
                    }
                    ContentDownloadStateTracker.State.Success -> {
                        progress.isIndeterminate = false
                        progress.progress = 100
                        progress.isVisible = false
                        renderDownloadIcon(isDownloaded = true)
                    }
                    is ContentDownloadStateTracker.State.Failure -> {
                        progress.isIndeterminate = false
                        progress.progress = 0
                        progress.isVisible = false
                        renderDownloadIcon(isDownloaded = false)
                    }
                }
            }
        }
        registrations[eventId] = Registration(mxcUrl, listener)
        session.contentDownloadProgressTracker().track(mxcUrl, listener)
    }

    fun unbind(eventId: String) {
        val registration = registrations.remove(eventId) ?: return
        activeSessionHolder.getSafeActiveSession()?.contentDownloadProgressTracker()?.unTrack(
                registration.mxcUrl,
                registration.listener
        )
    }

    fun clear() {
        registrations.keys.toList().forEach(::unbind)
    }
}
