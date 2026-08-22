package com.matrixmessenger.feature.chat.presentation

import android.content.Context
import com.matrixmessenger.feature.voice.data.recorder.VideoNoteRecorder
import com.matrixmessenger.core.media.MediaPipelineManager
import com.matrixmessenger.core.media.UploadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoNoteController @Inject constructor(
    @ApplicationContext private val context: Context,
    val recorder: VideoNoteRecorder,
    private val pipelineManager: MediaPipelineManager,
    private val uploadManager: UploadManager
) {
    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _state = MutableStateFlow<VideoNoteState>(VideoNoteState.Idle)
    val state: StateFlow<VideoNoteState> = _state.asStateFlow()

    private var recordingJob: Job? = null
    private var watchdogJob: Job? = null
    private var maxDurationMs: Long = 20_000L
    private var currentRoomId: String? = null
    private var currentFile: File? = null
    private var lastRecordedDurationMs: Long = 0

    private fun transitionTo(newState: VideoNoteState) {
        val currentState = _state.value
        if (isValidTransition(currentState, newState)) {
            Timber.d("VIDEO_NOTE: Transition ${currentState::class.simpleName} -> ${newState::class.simpleName}")
            _state.value = newState
            handleStateEntry(newState)
        } else {
            Timber.w("VIDEO_NOTE: Invalid transition attempt ${currentState::class.simpleName} -> ${newState::class.simpleName}")
        }
    }

    private fun isValidTransition(from: VideoNoteState, to: VideoNoteState): Boolean {
        if (to is VideoNoteState.Failed || to == VideoNoteState.Idle) return true
        return when (from) {
            VideoNoteState.Idle -> to == VideoNoteState.Pressing
            VideoNoteState.Pressing -> to == VideoNoteState.Preparing || to == VideoNoteState.Idle
            VideoNoteState.Preparing -> to is VideoNoteState.Recording || to == VideoNoteState.StopRequested
            is VideoNoteState.Recording -> to == VideoNoteState.StopRequested || to == VideoNoteState.Cancelled
            VideoNoteState.StopRequested -> to == VideoNoteState.Finalizing || to == VideoNoteState.Cancelled
            VideoNoteState.Finalizing -> to == VideoNoteState.Sending
            VideoNoteState.Sending -> to == VideoNoteState.Done
            VideoNoteState.Done -> to == VideoNoteState.Idle
            else -> false
        }
    }

    private fun handleStateEntry(state: VideoNoteState) {
        startWatchdog(state)
        when (state) {
            VideoNoteState.Idle -> releaseResources()
            else -> {}
        }
    }

    fun onPointerDown(roomId: String, cacheDir: File) {
        Timber.d("VIDEO_NOTE: DOWN")
        currentRoomId = roomId
        currentFile = File(cacheDir, "video_note_${UUID.randomUUID()}.mp4")
        lastRecordedDurationMs = 0
        transitionTo(VideoNoteState.Pressing)
    }

    fun startPreparation() {
        if (_state.value != VideoNoteState.Pressing) return
        Timber.d("VIDEO_NOTE: PREPARE_START")
        transitionTo(VideoNoteState.Preparing)
        
        val file = currentFile ?: return

        controllerScope.launch {
            try {
                recorder.startRecording(file) { outputFile ->
                    onRecordingFinalized(outputFile)
                }.onSuccess {
                    if (_state.value == VideoNoteState.StopRequested) {
                        Timber.d("VIDEO_NOTE: CAMERA_READY (Stop was requested)")
                        stopRecordingInternal()
                    } else {
                        Timber.d("VIDEO_NOTE: RECORDING_START")
                        transitionTo(VideoNoteState.Recording(0, 0f))
                        startTimer()
                    }
                }.onFailure {
                    Timber.e(it, "VIDEO_NOTE: Preparation failed")
                    transitionTo(VideoNoteState.Failed(it.message ?: "Failed to start camera"))
                }
            } catch (e: Exception) {
                Timber.e(e, "VIDEO_NOTE: Exception during preparation")
                transitionTo(VideoNoteState.Failed(e.message ?: "Camera error"))
            }
        }
    }

    fun onPointerUp() {
        Timber.d("VIDEO_NOTE: POINTER_UP")
        val currentState = _state.value
        when (currentState) {
            VideoNoteState.Pressing -> transitionTo(VideoNoteState.Idle)
            VideoNoteState.Preparing -> transitionTo(VideoNoteState.StopRequested)
            is VideoNoteState.Recording -> stopRecordingInternal()
            else -> {}
        }
    }

    private fun stopRecordingInternal() {
        Timber.d("VIDEO_NOTE: STOPPING")
        transitionTo(VideoNoteState.StopRequested)
        recordingJob?.cancel()
        recorder.stopRecording()
    }

    fun cancelRecording() {
        Timber.d("VIDEO_NOTE: CANCELLED")
        transitionTo(VideoNoteState.Cancelled)
        recordingJob?.cancel()
        recorder.stopRecording()
        transitionTo(VideoNoteState.Idle)
    }

    private fun onRecordingFinalized(file: File?) {
        Timber.d("VIDEO_NOTE: FILE_READY")
        if (file == null || !file.exists()) {
            transitionTo(VideoNoteState.Failed("Recording failed: No file"))
            return
        }

        if (_state.value == VideoNoteState.Cancelled) {
            file.delete()
            return
        }

        transitionTo(VideoNoteState.Finalizing)
        
        val roomId = currentRoomId ?: return
        val durationMs = lastRecordedDurationMs

        controllerScope.launch {
            pipelineManager.processVideoNote(file).fold(
                onSuccess = { processedFile ->
                    Timber.d("VIDEO_NOTE: UPLOAD_START")
                    transitionTo(VideoNoteState.Sending)
                    uploadManager.uploadVideoNote(roomId, processedFile, durationMs, 480, 480)
                        .onSuccess {
                            Timber.d("VIDEO_NOTE: SENT")
                            transitionTo(VideoNoteState.Done)
                            delay(500)
                            transitionTo(VideoNoteState.Idle)
                        }
                        .onFailure {
                            Timber.e(it, "VIDEO_NOTE: Upload failed")
                            transitionTo(VideoNoteState.Failed(it.message ?: "Upload failed"))
                        }
                },
                onFailure = {
                    Timber.e(it, "VIDEO_NOTE: Processing failed")
                    transitionTo(VideoNoteState.Failed(it.message ?: "Processing failed"))
                }
            )
        }
    }

    private fun startTimer() {
        recordingJob?.cancel()
        recordingJob = controllerScope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                lastRecordedDurationMs = elapsed
                val progress = (elapsed.toFloat() / maxDurationMs).coerceIn(0f, 1f)
                
                _state.value = VideoNoteState.Recording(elapsed, progress)
                
                if (elapsed >= maxDurationMs) {
                    Timber.d("VIDEO_NOTE: MAX_DURATION_REACHED")
                    stopRecordingInternal()
                    break
                }
                delay(16)
            }
        }
    }

    private fun startWatchdog(state: VideoNoteState) {
        watchdogJob?.cancel()
        val timeout = when (state) {
            VideoNoteState.Preparing -> 5000L
            VideoNoteState.StopRequested -> 10000L
            else -> return
        }

        watchdogJob = controllerScope.launch {
            delay(timeout)
            Timber.e("VIDEO_NOTE: Watchdog triggered for state ${state::class.simpleName}")
            transitionTo(VideoNoteState.Failed("Operation timed out"))
            transitionTo(VideoNoteState.Idle)
        }
    }

    private fun releaseResources() {
        Timber.d("VIDEO_NOTE: RELEASED")
        try {
            recorder.stopRecording()
            recorder.stopPreview()
            recordingJob?.cancel()
            watchdogJob?.cancel()
            currentRoomId = null
        } catch (e: Exception) {
            Timber.e(e, "Error releasing resources")
        }
    }
}
