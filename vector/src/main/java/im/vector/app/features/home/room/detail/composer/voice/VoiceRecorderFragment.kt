/*
 * Copyright 2022-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.composer.voice

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.airbnb.mvrx.parentFragmentViewModel
import com.airbnb.mvrx.withState
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.core.hardware.vibrate
import im.vector.app.core.platform.VectorBaseFragment
import im.vector.app.core.resources.BuildMeta
import im.vector.app.core.utils.checkPermissions
import im.vector.app.core.utils.onPermissionDeniedSnackbar
import im.vector.app.core.utils.registerForPermissionsResult
import im.vector.app.databinding.FragmentVoiceRecorderBinding
import im.vector.app.features.attachments.AttachmentsHelper
import im.vector.app.features.attachments.ContactAttachment
import im.vector.app.features.home.room.detail.composer.voice.InlineRoundVideoRecorder
import im.vector.app.core.utils.PERMISSIONS_FOR_VOICE_MESSAGE
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import im.vector.app.features.home.room.detail.TimelineViewModel
import im.vector.app.features.home.room.detail.composer.MessageComposerAction
import im.vector.app.features.home.room.detail.composer.MessageComposerViewEvents
import im.vector.app.features.home.room.detail.composer.MessageComposerViewModel
import im.vector.app.features.home.room.detail.composer.MessageComposerViewState
import im.vector.app.features.home.room.detail.composer.SendMode
import im.vector.app.features.home.room.detail.composer.boolean
import im.vector.app.features.home.room.detail.timeline.helper.AudioMessagePlaybackTracker
import im.vector.lib.core.utils.timer.Clock
import im.vector.lib.strings.CommonStrings
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class VoiceRecorderFragment : VectorBaseFragment<FragmentVoiceRecorderBinding>() {

    @Inject lateinit var audioMessagePlaybackTracker: AudioMessagePlaybackTracker
    @Inject lateinit var clock: Clock
    @Inject lateinit var buildMeta: BuildMeta

    private lateinit var videoNoteAttachmentsHelper: AttachmentsHelper
    private var inlineVideoNoteRecorder: InlineRoundVideoRecorder? = null
    private var inlineVideoNoteOutputFile: File? = null
    private var inlineVideoNoteFinalizing = false
    private var voiceRecordingStartedByGesture = false

    private val timelineViewModel: TimelineViewModel by parentFragmentViewModel()
    private val messageComposerViewModel: MessageComposerViewModel by parentFragmentViewModel()

    private val permissionVoiceMessageLauncher = registerForPermissionsResult { allGranted, deniedPermanently ->
        if (allGranted) {
            // In this case, let the user start again the gesture
        } else if (deniedPermanently) {
            vectorBaseActivity.onPermissionDeniedSnackbar(CommonStrings.denied_permission_voice_message)
        }
    }

    private val permissionVideoNoteLauncher = registerForPermissionsResult { allGranted, deniedPermanently ->
        if (!allGranted && deniedPermanently && isAdded) {
            Toast.makeText(
                    requireContext(),
                    im.vector.app.R.string.telegram_video_note_permission_error,
                    Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentVoiceRecorderBinding {
        return FragmentVoiceRecorderBinding.inflate(inflater, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        videoNoteAttachmentsHelper = AttachmentsHelper(requireContext(), object : AttachmentsHelper.Callback {
            override fun onContactAttachmentReady(contactAttachment: ContactAttachment) = Unit

            override fun onContentAttachmentsReady(attachments: List<ContentAttachmentData>) {
                attachments.forEach { attachment ->
                    messageComposerViewModel.handle(
                            MessageComposerAction.SendMediaAttachment(attachment, getRootThreadEventId())
                    )
                }
            }

            override fun onAttachmentError(throwable: Throwable) {
                Toast.makeText(
                        requireContext(),
                        throwable.localizedMessage ?: getString(im.vector.app.R.string.telegram_video_note_recording_failed),
                        Toast.LENGTH_LONG
                ).show()
            }
        }, buildMeta).register()

        val videoPreview = views.voiceMessageRecorderView.findViewById<android.view.TextureView>(im.vector.app.R.id.videoNotePreviewView)
        val videoTimer = views.voiceMessageRecorderView.findViewById<android.widget.TextView>(im.vector.app.R.id.voiceMessageTimer)
        val videoTimerIndicator = views.voiceMessageRecorderView.findViewById<android.widget.ImageView>(im.vector.app.R.id.voiceMessageTimerIndicator)
        inlineVideoNoteRecorder = InlineRoundVideoRecorder(
                preview = videoPreview,
                timer = videoTimer,
                timerIndicator = videoTimerIndicator,
                onFailure = { failure ->
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(
                                    requireContext(),
                                    failure.localizedMessage ?: getString(im.vector.app.R.string.telegram_video_note_recording_failed),
                                    Toast.LENGTH_LONG
                            ).show()
                            inlineVideoNoteOutputFile?.let(videoNoteAttachmentsHelper::discardInlineVideoNoteOutput)
                            inlineVideoNoteOutputFile = null
                            views.voiceMessageRecorderView.hideVideoNoteRecordingViews()
                        }
                    }
                },
                onMaximumDurationReached = { finishInlineVideoNote(cancelled = false) }
        )

        messageComposerViewModel.observeViewEvents {
            when (it) {
                is MessageComposerViewEvents.AnimateSendButtonVisibility -> handleSendButtonVisibilityChanged(it.isVisible)
                else -> Unit
            }
        }

        messageComposerViewModel.onEach(MessageComposerViewState::sendMode, MessageComposerViewState::canSendMessage) { mode, canSend ->
            if (!canSend.boolean()) {
                return@onEach
            }
            if (mode is SendMode.Voice) {
                views.voiceMessageRecorderView.isVisible = true
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Removed listeners should be set again
        setupVoiceMessageView()
    }

    override fun onPause() {
        super.onPause()
        // The camera/microphone must be released when the app backgrounds. Audio message
        // playback remains owned by AudioPlaybackService and continues independently.
        if (inlineVideoNoteRecorder != null && !inlineVideoNoteFinalizing) {
            inlineVideoNoteRecorder?.cancel()
            inlineVideoNoteOutputFile?.let(videoNoteAttachmentsHelper::discardInlineVideoNoteOutput)
            inlineVideoNoteOutputFile = null
            views.voiceMessageRecorderView.hideVideoNoteRecordingViews()
        }
    }

    override fun onDestroyView() {
        inlineVideoNoteRecorder?.release()
        inlineVideoNoteRecorder = null
        super.onDestroyView()
    }

    override fun invalidate() = withState(timelineViewModel, messageComposerViewModel) { mainState, messageComposerState ->
        if (mainState.tombstoneEvent != null) return@withState

        val hasVoiceDraft = messageComposerState.voiceRecordingUiState is VoiceMessageRecorderView.RecordingUiState.Draft
        with(views.root) {
            isVisible = messageComposerState.isVoiceMessageRecorderVisible || hasVoiceDraft
            render(messageComposerState.voiceRecordingUiState)
        }
    }

    private fun handleSendButtonVisibilityChanged(isSendButtonVisible: Boolean) {
        if (isSendButtonVisible) {
            views.root.isVisible = false
        } else {
            views.root.alpha = 0f
            views.root.isVisible = true
            views.root.animate().alpha(1f).setDuration(150).start()
        }
    }

    private fun finishInlineVideoNote(cancelled: Boolean) {
        // The timer and MediaRecorder can both report the maximum duration on adjacent
        // callbacks. Finalize only once so the same MP4 is never sent/deleted twice.
        if (inlineVideoNoteFinalizing && !cancelled) return
        val recorder = inlineVideoNoteRecorder ?: return
        val pendingFile = inlineVideoNoteOutputFile
        if (cancelled) {
            inlineVideoNoteFinalizing = false
            recorder.cancel()
            videoNoteAttachmentsHelper.discardInlineVideoNoteOutput(pendingFile)
            inlineVideoNoteOutputFile = null
            views.voiceMessageRecorderView.hideVideoNoteRecordingViews()
            return
        }

        inlineVideoNoteFinalizing = true
        // Camera startup is asynchronous. If the user releases during initialization,
        // the recorder queues stop until MediaRecorder is ready, then returns the finalized MP4.
        recorder.stop { completedFile ->
            inlineVideoNoteFinalizing = false
            val validFile = completedFile != null &&
                    completedFile.isFile &&
                    completedFile.length() > 1_024L
            if (validFile) {
                // Reuse the normal Matrix attachment conversion/send path. The resulting m.video
                // event carries a video_note_* filename marker for the round thumbnail treatment.
                videoNoteAttachmentsHelper.onCameraVideoResult()
            } else {
                videoNoteAttachmentsHelper.discardInlineVideoNoteOutput(pendingFile)
            }
            inlineVideoNoteOutputFile = null
            if (isAdded) {
                requireActivity().runOnUiThread {
                    if (isAdded) views.voiceMessageRecorderView.hideVideoNoteRecordingViews()
                }
            }
        }
    }

    private fun setupVoiceMessageView() {
        audioMessagePlaybackTracker.track(AudioMessagePlaybackTracker.RECORDING_ID, views.voiceMessageRecorderView)
        views.voiceMessageRecorderView.callback = object : VoiceMessageRecorderView.Callback {

            override fun onVoiceRecordingStarted() {
                voiceRecordingStartedByGesture = checkPermissions(
                        PERMISSIONS_FOR_VOICE_MESSAGE,
                        requireActivity(),
                        permissionVoiceMessageLauncher
                )
                if (voiceRecordingStartedByGesture) {
                    messageComposerViewModel.handle(MessageComposerAction.StartRecordingVoiceMessage)
                    vibrate(requireContext())
                }
            }

            override fun onVideoNoteRequested() {
                val videoPermissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
                if (checkPermissions(videoPermissions, requireActivity(), permissionVideoNoteLauncher)) {
                    try {
                        val output = videoNoteAttachmentsHelper.prepareInlineVideoNoteOutput()
                        inlineVideoNoteOutputFile = output
                        inlineVideoNoteRecorder?.start(output)
                        vibrate(requireContext())
                    } catch (failure: Throwable) {
                        Toast.makeText(
                                requireContext(),
                                failure.localizedMessage ?: getString(im.vector.app.R.string.telegram_video_note_recording_failed),
                                Toast.LENGTH_LONG
                        ).show()
                        inlineVideoNoteOutputFile?.let(videoNoteAttachmentsHelper::discardInlineVideoNoteOutput)
                        inlineVideoNoteOutputFile = null
                        views.voiceMessageRecorderView.hideVideoNoteRecordingViews()
                    }
                }
            }

            override fun onVideoNoteReleased() {
                finishInlineVideoNote(cancelled = false)
            }

            override fun onVideoNoteCancelled() {
                finishInlineVideoNote(cancelled = true)
            }

            override fun onVoicePlaybackButtonClicked() {
                messageComposerViewModel.handle(MessageComposerAction.PlayOrPauseRecordingPlayback)
            }

            override fun onVoiceRecordingCancelled() {
                if (!voiceRecordingStartedByGesture) {
                    updateRecordingUiState(VoiceMessageRecorderView.RecordingUiState.Idle)
                    return
                }
                voiceRecordingStartedByGesture = false
                messageComposerViewModel.handle(MessageComposerAction.EndRecordingVoiceMessage(isCancelled = true, rootThreadEventId = getRootThreadEventId()))
                vibrate(requireContext())
                updateRecordingUiState(VoiceMessageRecorderView.RecordingUiState.Idle)
            }

            override fun onVoiceRecordingLocked() {
                if (!voiceRecordingStartedByGesture) return
                val startedState = withState(messageComposerViewModel) { it.voiceRecordingUiState as? VoiceMessageRecorderView.RecordingUiState.Recording }
                val startTime = startedState?.recordingStartTimestamp ?: clock.epochMillis()
                updateRecordingUiState(VoiceMessageRecorderView.RecordingUiState.Locked(startTime))
            }

            override fun onVoiceRecordingEnded() {
                if (voiceRecordingStartedByGesture) onSendVoiceMessage()
            }

            override fun onSendVoiceMessage() {
                if (!voiceRecordingStartedByGesture) return
                voiceRecordingStartedByGesture = false
                messageComposerViewModel.handle(
                        MessageComposerAction.EndRecordingVoiceMessage(isCancelled = false, rootThreadEventId = getRootThreadEventId())
                )
                updateRecordingUiState(VoiceMessageRecorderView.RecordingUiState.Idle)
            }

            override fun onDeleteVoiceMessage() {
                if (!voiceRecordingStartedByGesture) return
                voiceRecordingStartedByGesture = false
                messageComposerViewModel.handle(
                        MessageComposerAction.EndRecordingVoiceMessage(isCancelled = true, rootThreadEventId = getRootThreadEventId())
                )
                updateRecordingUiState(VoiceMessageRecorderView.RecordingUiState.Idle)
            }

            override fun onRecordingLimitReached() = pauseRecording()

            override fun onRecordingWaveformClicked() = pauseRecording()

            override fun onVoiceWaveformTouchedUp(percentage: Float, duration: Int) {
                messageComposerViewModel.handle(
                        MessageComposerAction.VoiceWaveformTouchedUp(AudioMessagePlaybackTracker.RECORDING_ID, duration, percentage)
                )
            }

            override fun onVoiceWaveformMoved(percentage: Float, duration: Int) {
                messageComposerViewModel.handle(
                        MessageComposerAction.VoiceWaveformTouchedUp(AudioMessagePlaybackTracker.RECORDING_ID, duration, percentage)
                )
            }

            private fun updateRecordingUiState(state: VoiceMessageRecorderView.RecordingUiState) {
                messageComposerViewModel.handle(
                        MessageComposerAction.OnVoiceRecordingUiStateChanged(state)
                )
            }

            private fun pauseRecording() {
                messageComposerViewModel.handle(
                        MessageComposerAction.PauseRecordingVoiceMessage
                )
                updateRecordingUiState(VoiceMessageRecorderView.RecordingUiState.Draft)
            }
        }
    }

    /**
     * Returns the root thread event if we are in a thread room, otherwise returns null.
     */
    fun getRootThreadEventId(): String? = withState(timelineViewModel) { it.rootThreadEventId }
}
