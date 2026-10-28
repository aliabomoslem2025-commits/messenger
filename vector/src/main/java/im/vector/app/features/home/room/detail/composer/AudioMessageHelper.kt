/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.composer

import android.content.Context
import androidx.core.content.FileProvider
import im.vector.app.core.resources.BuildMeta
import im.vector.app.features.home.room.detail.timeline.helper.AudioMessagePlaybackTracker
import im.vector.app.features.voice.VoiceFailure
import im.vector.app.features.voice.VoiceRecorder
import im.vector.app.features.voice.VoiceRecorderProvider
import im.vector.lib.core.utils.timer.CountUpTimer
import im.vector.lib.multipicker.entity.MultiPickerAudioType
import im.vector.lib.multipicker.utils.toMultiPickerAudioType
import org.matrix.android.sdk.api.extensions.orFalse
import org.matrix.android.sdk.api.extensions.tryOrNull
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * Helper class to record audio for voice messages.
 */
class AudioMessageHelper @Inject constructor(
        private val context: Context,
        private val playbackTracker: AudioMessagePlaybackTracker,
        private val buildMeta: BuildMeta,
        voiceRecorderProvider: VoiceRecorderProvider
) {
    private var currentPlayingId: String? = null
    private val voiceRecorder: VoiceRecorder by lazy { voiceRecorderProvider.provideVoiceRecorder() }

    private val amplitudeList = mutableListOf<Int>()

    private var amplitudeTicker: CountUpTimer? = null

    fun initializeRecorder(roomId: String, attachmentData: ContentAttachmentData) {
        voiceRecorder.initializeRecord(roomId, attachmentData)
        amplitudeList.clear()
        attachmentData.waveform?.let {
            amplitudeList.addAll(it)
            playbackTracker.updateCurrentRecording(AudioMessagePlaybackTracker.RECORDING_ID, amplitudeList)
        }
    }

    fun startRecording(roomId: String) {
        stopPlayback()
        playbackTracker.pauseAllPlaybacks()
        amplitudeList.clear()

        try {
            voiceRecorder.startRecord(roomId)
        } catch (failure: Throwable) {
            Timber.e(failure, "Unable to start recording")
            throw VoiceFailure.UnableToRecord(failure)
        }
        startRecordingAmplitudes()
    }

    fun stopRecording(): MultiPickerAudioType? {
        val voiceMessageFile = tryOrNull("Cannot stop media recorder!") {
            voiceRecorder.stopRecord()
            voiceRecorder.getVoiceMessageFile()
        }

        tryOrNull("Cannot stop media recording amplitude") {
            stopRecordingAmplitudes()
        }

        return try {
            voiceMessageFile?.let {
                val outputFileUri = FileProvider.getUriForFile(context, buildMeta.applicationId + ".fileProvider", it, "Voice message.${it.extension}")
                outputFileUri
                        .toMultiPickerAudioType(context)
                        ?.apply {
                            waveform = if (amplitudeList.size < 50) {
                                amplitudeList
                            } else {
                                amplitudeList.chunked(amplitudeList.size / 50) { items -> items.maxOrNull() ?: 0 }
                            }
                        }
            }
        } catch (e: FileNotFoundException) {
            Timber.e(e, "Cannot stop voice recording")
            null
        } catch (e: RuntimeException) {
            Timber.e(e, "Error while retrieving metadata")
            null
        }
    }

    /**
     * When entering in playback mode actually.
     */
    fun pauseRecording() {
        // TODO should we pause instead of stop?
        voiceRecorder.stopRecord()
        stopRecordingAmplitudes()
    }

    /**
     * Stop an in-progress recording when the app backgrounds without stopping the
     * independent playback service. Called only while the UI state is recording.
     */
    fun stopRecordingForBackground(): MultiPickerAudioType? {
        val audio = stopRecording()
        return audio
    }

    fun deleteRecording() {
        tryOrNull("Cannot stop media recording amplitude") {
            stopRecordingAmplitudes()
        }
        tryOrNull("Cannot stop media recorder!") {
            voiceRecorder.cancelRecord()
        }
    }

    fun startOrPauseRecordingPlayback() {
        voiceRecorder.getVoiceMessageFile()?.let {
            startOrPausePlayback(AudioMessagePlaybackTracker.RECORDING_ID, it)
        }
    }

    fun startOrPausePlayback(id: String, file: File) {
        val playbackState = playbackTracker.getPlaybackState(id)
        stopRecordingAmplitudes()
        val serviceIsPlayingThisMessage = AudioPlaybackService.isPlayingFor(context, id)
        if (playbackState is AudioMessagePlaybackTracker.Listener.State.Playing || serviceIsPlayingThisMessage) {
            AudioPlaybackService.pause(context)
            playbackTracker.pausePlayback(id)
            currentPlayingId = id
        } else {
            // The service owns the MediaPlayer and persists it across Activity/Fragment
            // lifecycle changes. Restore the service's position if this ViewModel was recreated.
            val resumeAt = playbackTracker.getPlaybackTime(id) ?: AudioPlaybackService.persistedPosition(context, id) ?: 0
            playbackTracker.pauseAllPlaybacks()
            currentPlayingId = id
            AudioPlaybackService.play(context, id, file, resumeAt)
            playbackTracker.startPlayback(id)
        }
    }

    fun stopPlayback() {
        currentPlayingId?.let(playbackTracker::pausePlayback)
        AudioPlaybackService.stop(context)
        currentPlayingId = null
    }

    fun movePlaybackTo(id: String, percentage: Float, totalDuration: Int) {
        val toMillisecond = (totalDuration * percentage).toInt().coerceAtLeast(0)
        val wasPlaying = playbackTracker.getPlaybackState(id) is AudioMessagePlaybackTracker.Listener.State.Playing ||
                AudioPlaybackService.isPlayingFor(context, id)
        playbackTracker.pauseAllPlaybacks()

        if (currentPlayingId == id || AudioPlaybackService.isActiveFor(context, id)) {
            currentPlayingId = id
            AudioPlaybackService.seek(context, toMillisecond)
        }
        if (wasPlaying) {
            playbackTracker.updatePlayingAtPlaybackTime(id, toMillisecond, percentage)
        } else {
            playbackTracker.updatePausedAtPlaybackTime(id, toMillisecond, percentage)
        }
    }

    private fun startRecordingAmplitudes() {
        amplitudeTicker?.stop()
        amplitudeTicker = CountUpTimer(intervalInMs = 50).apply {
            tickListener = CountUpTimer.TickListener { onAmplitudeTick() }
            start()
        }
    }

    private fun onAmplitudeTick() {
        try {
            val maxAmplitude = voiceRecorder.getMaxAmplitude()
            amplitudeList.add(maxAmplitude)
            playbackTracker.updateCurrentRecording(AudioMessagePlaybackTracker.RECORDING_ID, amplitudeList)
        } catch (e: IllegalStateException) {
            Timber.e(e, "Cannot get max amplitude. Amplitude recording timer will be stopped.")
            stopRecordingAmplitudes()
        } catch (e: RuntimeException) {
            Timber.e(e, "Cannot get max amplitude (native error). Amplitude recording timer will be stopped.")
            stopRecordingAmplitudes()
        }
    }

    private fun stopRecordingAmplitudes() {
        amplitudeTicker?.stop()
        amplitudeTicker = null
    }

    fun stopTracking() {
        playbackTracker.unregisterListeners()
    }

    fun stopAllVoiceActions(deleteRecord: Boolean = true): MultiPickerAudioType? {
        val audioType = stopRecording()
        stopPlayback()
        if (deleteRecord) {
            deleteRecording()
        }
        return audioType
    }
}
