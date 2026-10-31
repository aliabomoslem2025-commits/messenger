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
import im.vector.app.features.session.coroutineScope
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.session.file.ContentDownloadStateTracker
import org.matrix.android.sdk.api.session.file.FileService
import org.matrix.android.sdk.api.session.room.model.message.MessageWithAttachmentContent
import timber.log.Timber
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
            showPlayIconOnSuccess: Boolean = false,
            messageContent: MessageWithAttachmentContent? = null,
            onMediaClick: (() -> Unit)? = null
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
        val cachedAtBind = messageContent?.let { runCatching { session.fileService().fileState(it) }.getOrNull() }
        val isReadyAtBind = cachedAtBind is FileService.FileState.InCache && cachedAtBind.decryptedFileInCache
        renderDownloadIcon(isDownloaded = isReadyAtBind)
        val listener = object : ContentDownloadStateTracker.UpdateListener {
            override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
                when (state) {
                    ContentDownloadStateTracker.State.Idle -> {
                        progress.progress = 0
                        progress.isIndeterminate = false
                        progress.isVisible = false
                        val cached = messageContent?.let { runCatching { session.fileService().fileState(it) }.getOrNull() }
                        val isReady = cached is FileService.FileState.InCache && cached.decryptedFileInCache
                        renderDownloadIcon(isDownloaded = isReady)
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

        // The download affordance owns the Matrix download request. FileService performs
        // authenticated retrieval and E2EE decryption, while the tracker above drives the
        // real circular progress state. A completed item delegates to the normal viewer.
        downloadIcon?.setOnClickListener {
            val content = messageContent ?: return@setOnClickListener
            val fileState = runCatching { session.fileService().fileState(content) }.getOrNull()
            if (fileState is FileService.FileState.InCache && fileState.decryptedFileInCache) {
                onMediaClick?.invoke()
            } else if (fileState !is FileService.FileState.Downloading) {
                session.coroutineScope.launch {
                    runCatching { session.fileService().downloadFile(content) }
                            .onFailure { Timber.e(it, "Unable to download Matrix media for event $eventId") }
                }
            }
        }
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
