/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

@file:OptIn(androidx.camera.video.ExperimentalPersistentRecording::class)

package im.vector.app.features.home.room.detail.composer.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import im.vector.app.R
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Telegram-style round video-note recorder.
 *
 * CameraX persistent recording keeps one MP4 muxer and its audio track alive while the
 * VideoCapture use case is rebound from the front lens to the rear lens and back.
 */
class InlineRoundVideoRecorder(
        private val context: Context,
        private val lifecycleOwner: LifecycleOwner,
        private val preview: PreviewView,
        private val timer: View,
        private val timerIndicator: ImageView,
        private val progressRing: VideoNoteProgressRingView,
        private val onFailure: (Throwable) -> Unit,
        private val onMaximumDurationReached: () -> Unit,
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private val maxRecordingDurationMs = preview.resources
            .getInteger(R.integer.telegram_video_note_max_duration_seconds)
            .coerceIn(1, 60).toLong() * 1_000L

    private var cameraProvider: ProcessCameraProvider? = null
    private var recorder: Recorder? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var previewUseCase: Preview? = null
    private var activeCamera: Camera? = null
    private var recording: Recording? = null
    private var outputFile: File? = null
    private var preferFrontCamera = true
    private var flashEnabled = false
    private var pendingStart = false
    private var isRecording = false
    private var isFinalizing = false
    private var isCancelled = false
    private var maxDurationCallbackSent = false
    private var startedAtMs = 0L
    private var stopCallback: ((File?) -> Unit)? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            if (timer is AnimatedClockTickerView) {
                timer.setTimeMs(elapsed)
            } else if (timer is TextView) {
                timer.text = formatTime(elapsed)
            }
            progressRing.progress = (elapsed.toFloat() / maxRecordingDurationMs).coerceIn(0f, 1f)
            if (elapsed >= maxRecordingDurationMs && !maxDurationCallbackSent) {
                maxDurationCallbackSent = true
                onMaximumDurationReached()
            } else {
                mainHandler.postDelayed(this, TIMER_INTERVAL_MS)
            }
        }
    }

    init {
        preview.scaleType = PreviewView.ScaleType.FILL_CENTER
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        preview.clipToOutline = true
        preview.visibility = View.GONE
        timerIndicator.visibility = View.GONE
        timer.visibility = View.GONE
        progressRing.visibility = View.GONE
        progressRing.progress = 0f
    }

    fun start(file: File) {
        if (pendingStart || isRecording || isFinalizing) cancel()
        outputFile = file
        pendingStart = true
        isCancelled = false
        maxDurationCallbackSent = false
        startedAtMs = 0L
        progressRing.progress = 0f
        if (timer is AnimatedClockTickerView) timer.setTimeMs(0L) else if (timer is TextView) timer.text = formatTime(0L)
        timerIndicator.visibility = View.VISIBLE
        timer.visibility = View.VISIBLE
        progressRing.visibility = View.VISIBLE
        preview.visibility = View.VISIBLE

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (!pendingStart || isCancelled) return@addListener
            try {
                cameraProvider = providerFuture.get()
                val initialFacing = if (cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) == true) {
                    true
                } else {
                    false
                }
                bindCamera(initialFacing)
                startPersistentRecording(file)
            } catch (failure: Throwable) {
                failStart(failure)
            }
        }, mainExecutor)
    }

    private fun bindCamera(front: Boolean) {
        val provider = cameraProvider ?: error("Camera provider is not ready")
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        check(provider.hasCamera(selector)) { "Selected camera is unavailable" }

        val previewCase = previewUseCase ?: Preview.Builder().build().also {
            it.setSurfaceProvider(preview.surfaceProvider)
            previewUseCase = it
        }
        val activeRecorder = recorder ?: Recorder.Builder()
                .setQualitySelector(
                        QualitySelector.from(
                                Quality.SD,
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                )
                .build()
                .also { recorder = it }
        val capture = videoCapture ?: VideoCapture.withOutput(activeRecorder).also {
            videoCapture = it
        }

        // PersistentRecording intentionally survives this unbind/rebind sequence.
        provider.unbindAll()
        activeCamera = provider.bindToLifecycle(lifecycleOwner, selector, previewCase, capture)
        preferFrontCamera = front
        if (flashEnabled && activeCamera?.cameraInfo?.hasFlashUnit() == true) {
            activeCamera?.cameraControl?.enableTorch(true)
        }
    }

    private fun startPersistentRecording(file: File) {
        val activeRecorder = recorder ?: error("Video recorder is not initialized")
        val pendingRecording = activeRecorder
                .prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .asPersistentRecording()

        recording = pendingRecording.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    if (!isCancelled) {
                        pendingStart = false
                        isRecording = true
                        isFinalizing = false
                        startedAtMs = SystemClock.elapsedRealtime()
                        mainHandler.removeCallbacks(timerRunnable)
                        mainHandler.post(timerRunnable)
                    }
                }
                is VideoRecordEvent.Finalize -> {
                    mainHandler.removeCallbacks(timerRunnable)
                    isRecording = false
                    pendingStart = false
                    isFinalizing = false
                    val fileResult = outputFile
                    recording = null
                    cameraProvider?.unbindAll()
                    activeCamera = null
                    hidePreview()
                    val callback = stopCallback
                    stopCallback = null
                    val validFile = !isCancelled && !event.hasError() &&
                            fileResult != null && fileResult.isFile &&
                            fileResult.length() > MIN_VALID_FILE_BYTES
                    if (validFile) {
                        callback?.invoke(fileResult)
                    } else {
                        fileResult?.let { runCatching { it.delete() } }
                        if (!isCancelled && event.hasError()) {
                            onFailure(IllegalStateException("Video note recording failed (CameraX error ${event.error})."))
                        }
                        callback?.invoke(null)
                    }
                    outputFile = null
                }
                else -> Unit
            }
        }
    }

    fun stop(onComplete: (File?) -> Unit) {
        if (isFinalizing) {
            stopCallback = onComplete
            return
        }
        if (pendingStart && recording == null) {
            // CameraX provider is still initializing; do not leave the send gesture waiting.
            pendingStart = false
            isCancelled = true
            outputFile?.let { runCatching { it.delete() } }
            outputFile = null
            hidePreview()
            onComplete(null)
            return
        }
        if (!isRecording && recording == null) {
            onComplete(null)
            return
        }
        stopCallback = onComplete
        isFinalizing = true
        mainHandler.removeCallbacks(timerRunnable)
        recording?.stop()
    }

    fun cancel() {
        isCancelled = true
        pendingStart = false
        isFinalizing = false
        isRecording = false
        stopCallback = null
        mainHandler.removeCallbacks(timerRunnable)
        runCatching { recording?.stop() }
        recording = null
        cameraProvider?.unbindAll()
        activeCamera = null
        outputFile?.let { runCatching { it.delete() } }
        outputFile = null
        hidePreview()
    }

    fun release() {
        cancel()
        cameraProvider = null
        previewUseCase = null
        videoCapture = null
        recorder = null
    }

    /**
     * Switch the active lens without finalizing the MP4. CameraX's persistent recording
     * keeps the audio encoder/muxer alive while the same VideoCapture is rebound.
     */
    fun switchCameraForNextCapture(): Boolean {
        val provider = cameraProvider ?: return false
        if (recording == null) {
            val desired = !preferFrontCamera
            val selector = if (desired) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (!runCatching { provider.hasCamera(selector) }.getOrDefault(false)) return false
            preferFrontCamera = desired
            return true
        }

        val previousFacing = preferFrontCamera
        val desiredFacing = !previousFacing
        val desiredSelector = if (desiredFacing) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (!runCatching { provider.hasCamera(desiredSelector) }.getOrDefault(false)) return false

        return try {
            bindCamera(desiredFacing)
            true
        } catch (switchFailure: Throwable) {
            runCatching { bindCamera(previousFacing) }
            onFailure(IllegalStateException("Unable to switch camera while recording.", switchFailure))
            false
        }
    }

    fun toggleFlash(): Boolean? {
        val camera = activeCamera ?: return null
        if (!camera.cameraInfo.hasFlashUnit()) return null
        val desired = !flashEnabled
        return try {
            camera.cameraControl.enableTorch(desired)
            flashEnabled = desired
            flashEnabled
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun failStart(failure: Throwable) {
        pendingStart = false
        isRecording = false
        isFinalizing = false
        mainHandler.removeCallbacks(timerRunnable)
        cameraProvider?.unbindAll()
        activeCamera = null
        outputFile?.let { runCatching { it.delete() } }
        outputFile = null
        hidePreview()
        val callback = stopCallback
        stopCallback = null
        callback?.invoke(null)
        onFailure(failure)
    }

    private fun hidePreview() {
        preview.visibility = View.GONE
        timer.visibility = View.GONE
        timerIndicator.visibility = View.GONE
        progressRing.visibility = View.GONE
        progressRing.progress = 0f
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).toInt()
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    companion object {
        private const val MIN_VALID_FILE_BYTES = 1_024L
        private const val TIMER_INTERVAL_MS = 50L
    }
}
