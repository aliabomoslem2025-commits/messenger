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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.session.file.ContentDownloadStateTracker
import org.matrix.android.sdk.api.session.file.FileService
import org.matrix.android.sdk.api.session.room.model.message.MessageWithAttachmentContent
import timber.log.Timber
import javax.inject.Inject

/**
 * Binds the Telegram-style circular media affordance to the Matrix SDK download lifecycle.
 *
 * Downloads always go through FileService so authenticated MXC URLs and encrypted attachments
 * are handled by the active Matrix session. The ring is driven by the SDK tracker; the small
 * decryption wait is resolved against FileService.fileState(), not guessed byte progress.
 */
@ActivityScoped
class ContentDownloadOverlayBinder @Inject constructor(
        private val activeSessionHolder: ActiveSessionHolder,
) {

    private data class Registration(
            val mxcUrl: String,
            val listener: ContentDownloadStateTracker.UpdateListener,
            val progress: ProgressBar,
            val downloadIcon: ImageView?,
            val showPlayIconOnSuccess: Boolean,
            val messageContent: MessageWithAttachmentContent,
            val onMediaClick: (() -> Unit)?,
    )

    private val registrations = mutableMapOf<String, Registration>()
    private val downloadJobs = mutableMapOf<String, Job>()
    private val decryptionPollJobs = mutableMapOf<String, Job>()

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
        val content = messageContent
        if (session == null || content == null || !mxcUrl.startsWith("mxc://")) {
            progress.isVisible = false
            return
        }

        progress.max = 100

        fun renderIcon(registration: Registration, downloaded: Boolean) {
            val icon = registration.downloadIcon ?: return
            if (downloaded) {
                if (registration.showPlayIconOnSuccess) {
                    icon.setImageResource(R.drawable.ic_material_play_circle)
                    icon.isVisible = true
                } else {
                    icon.isVisible = false
                }
            } else {
                icon.setImageResource(R.drawable.ic_telegram_download_arrow)
                icon.isVisible = true
            }
        }

        fun setReady(registration: Registration) {
            if (registrations[eventId] !== registration) return
            progress.isIndeterminate = false
            progress.progress = 100
            progress.isVisible = false
            renderIcon(registration, downloaded = true)
        }

        fun setIdle(registration: Registration) {
            if (registrations[eventId] !== registration) return
            progress.isIndeterminate = false
            progress.progress = 0
            progress.isVisible = false
            renderIcon(registration, downloaded = false)
        }

        fun showDecryptionProgress(registration: Registration) {
            if (registrations[eventId] !== registration) return
            progress.isIndeterminate = true
            progress.isVisible = true
            renderIcon(registration, downloaded = false)
        }

        val listener = object : ContentDownloadStateTracker.UpdateListener {
            override fun onDownloadStateUpdate(state: ContentDownloadStateTracker.State) {
                val currentRegistration = registrations[eventId] ?: return
                when (state) {
                    ContentDownloadStateTracker.State.Idle -> {
                        when (val fileState = runCatching { session.fileService().fileState(content) }.getOrNull()) {
                            is FileService.FileState.InCache -> {
                                if (fileState.decryptedFileInCache) {
                                    setReady(currentRegistration)
                                } else {
                                    showDecryptionProgress(currentRegistration)
                                    pollForDecryption(eventId, currentRegistration)
                                }
                            }
                            FileService.FileState.Downloading -> {
                                showDecryptionProgress(currentRegistration)
                            }
                            else -> setIdle(currentRegistration)
                        }
                    }
                    is ContentDownloadStateTracker.State.Downloading -> {
                        progress.isIndeterminate = state.indeterminate || state.total <= 0L
                        if (!progress.isIndeterminate) {
                            progress.progress = (100.0 * state.current.toDouble() / state.total.toDouble())
                                    .toInt().coerceIn(0, 100)
                        }
                        progress.isVisible = true
                        renderIcon(currentRegistration, downloaded = false)
                    }
                    ContentDownloadStateTracker.State.Decrypting -> showDecryptionProgress(currentRegistration)
                    ContentDownloadStateTracker.State.Success -> {
                        when (val fileState = runCatching { session.fileService().fileState(content) }.getOrNull()) {
                            is FileService.FileState.InCache -> {
                                if (fileState.decryptedFileInCache) {
                                    setReady(currentRegistration)
                                } else {
                                    showDecryptionProgress(currentRegistration)
                                    pollForDecryption(eventId, currentRegistration)
                                }
                            }
                            else -> {
                                // The progress interceptor finishes the raw transfer before the
                                // SDK completes E2EE decryption and exposes the clear file.
                                showDecryptionProgress(currentRegistration)
                                pollForDecryption(eventId, currentRegistration)
                            }
                        }
                    }
                    is ContentDownloadStateTracker.State.Failure -> setIdle(currentRegistration)
                }
            }
        }

        val registration = Registration(
                mxcUrl = mxcUrl,
                listener = listener,
                progress = progress,
                downloadIcon = downloadIcon,
                showPlayIconOnSuccess = showPlayIconOnSuccess,
                messageContent = content,
                onMediaClick = onMediaClick
        )
        registrations[eventId] = registration

        when (val fileState = runCatching { session.fileService().fileState(content) }.getOrNull()) {
            is FileService.FileState.InCache -> {
                if (fileState.decryptedFileInCache) setReady(registration) else showDecryptionProgress(registration)
            }
            FileService.FileState.Downloading -> showDecryptionProgress(registration)
            else -> setIdle(registration)
        }
        session.contentDownloadProgressTracker().track(mxcUrl, listener)

        downloadIcon?.setOnClickListener {
            when (val fileState = runCatching { session.fileService().fileState(content) }.getOrNull()) {
                is FileService.FileState.InCache -> {
                    if (fileState.decryptedFileInCache) {
                        registration.onMediaClick?.invoke()
                    } else {
                        startDownload(eventId, registration)
                    }
                }
                FileService.FileState.Downloading -> Unit
                else -> startDownload(eventId, registration)
            }
        }
    }

    private fun startDownload(eventId: String, registration: Registration) {
        if (downloadJobs[eventId]?.isActive == true) return
        val session = activeSessionHolder.getSafeActiveSession() ?: return
        registration.progress.isIndeterminate = true
        registration.progress.isVisible = true

        val job = session.coroutineScope.launch {
            val result = runCatching { session.fileService().downloadFile(registration.messageContent) }
            downloadJobs.remove(eventId)
            decryptionPollJobs.remove(eventId)?.cancel()
            val currentRegistration = registrations[eventId] ?: return@launch
            if (result.isSuccess) {
                currentRegistration.progress.isIndeterminate = false
                currentRegistration.progress.progress = 100
                currentRegistration.progress.isVisible = false
                val icon = currentRegistration.downloadIcon
                if (currentRegistration.showPlayIconOnSuccess) {
                    icon?.setImageResource(R.drawable.ic_material_play_circle)
                    icon?.isVisible = true
                } else {
                    icon?.isVisible = false
                }
            } else {
                currentRegistration.progress.isIndeterminate = false
                currentRegistration.progress.progress = 0
                currentRegistration.progress.isVisible = false
                currentRegistration.downloadIcon?.apply {
                    setImageResource(R.drawable.ic_telegram_download_arrow)
                    isVisible = true
                }
                result.exceptionOrNull()?.let {
                    Timber.e(it, "Unable to download Matrix media for event $eventId")
                }
            }
        }
        downloadJobs[eventId] = job
    }

    private fun pollForDecryption(eventId: String, registration: Registration) {
        if (downloadJobs[eventId]?.isActive == true || decryptionPollJobs[eventId]?.isActive == true) return
        val session = activeSessionHolder.getSafeActiveSession() ?: return
        val job = session.coroutineScope.launch {
            repeat(DECRYPTION_POLL_ATTEMPTS) {
                delay(DECRYPTION_POLL_INTERVAL_MS)
                if (registrations[eventId] !== registration) return@launch
                val fileState = runCatching { session.fileService().fileState(registration.messageContent) }.getOrNull()
                if (fileState is FileService.FileState.InCache && fileState.decryptedFileInCache) {
                    registration.progress.isIndeterminate = false
                    registration.progress.progress = 100
                    registration.progress.isVisible = false
                    registration.downloadIcon?.let { icon ->
                        if (registration.showPlayIconOnSuccess) {
                            icon.setImageResource(R.drawable.ic_material_play_circle)
                            icon.isVisible = true
                        } else {
                            icon.isVisible = false
                        }
                    }
                    decryptionPollJobs.remove(eventId)
                    return@launch
                }
            }
            // Unknown may be a short gap between raw transfer completion and the decrypted
            // cache entry becoming visible. Keep the ring until the bounded poll expires.
            decryptionPollJobs.remove(eventId)
            if (registrations[eventId] === registration) {
                registration.progress.isIndeterminate = false
                registration.progress.progress = 0
                registration.progress.isVisible = false
                registration.downloadIcon?.apply {
                    setImageResource(R.drawable.ic_telegram_download_arrow)
                    isVisible = true
                }
            }
        }
        decryptionPollJobs[eventId] = job
    }

    fun unbind(eventId: String) {
        val registration = registrations.remove(eventId) ?: return
        activeSessionHolder.getSafeActiveSession()?.contentDownloadProgressTracker()?.unTrack(
                registration.mxcUrl,
                registration.listener
        )
        decryptionPollJobs.remove(eventId)?.cancel()
        registration.downloadIcon?.setOnClickListener(null)
    }

    fun clear() {
        registrations.keys.toList().forEach(::unbind)
    }

    companion object {
        private const val DECRYPTION_POLL_INTERVAL_MS = 150L
        private const val DECRYPTION_POLL_ATTEMPTS = 80
    }
}
