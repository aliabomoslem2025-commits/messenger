/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.composer.voice

import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Captures round video notes in-place in the chat composer.
 *
 * The output file is allocated by AttachmentsHelper so it can be converted by the
 * existing Matrix attachment pipeline (including encryption, upload progress and send state).
 */
class InlineRoundVideoRecorder(
        private val preview: TextureView,
        private val timer: TextView,
        private val timerIndicator: ImageView,
        private val onFailure: (Throwable) -> Unit,
        private val onMaximumDurationReached: () -> Unit,
) : TextureView.SurfaceTextureListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var camera: Camera? = null
    private var recorder: MediaRecorder? = null
    private var previewSurface: Surface? = null
    private var outputFile: File? = null
    private var pendingStart = false
    private var isRecording = false
    private var startedAtMs = 0L
    private var maxDurationCallbackSent = false

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            timer.text = formatTime(elapsed)
            if (elapsed >= MAX_RECORDING_MS && !maxDurationCallbackSent) {
                maxDurationCallbackSent = true
                onMaximumDurationReached()
            } else if (isRecording) {
                mainHandler.postDelayed(this, TIMER_INTERVAL_MS)
            }
        }
    }

    init {
        preview.surfaceTextureListener = this
        preview.clipToOutline = true
        preview.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        preview.visibility = View.GONE
        timerIndicator.visibility = View.GONE
        timer.visibility = View.GONE
    }

    /**
     * Starts on the existing ACTION_DOWN hold. The camera preview appears inside the composer.
     * Texture creation can be asynchronous, so start() defers camera setup when necessary.
     */
    fun start(file: File) {
        if (isRecording || pendingStart) cancel()
        outputFile = file
        pendingStart = true
        maxDurationCallbackSent = false
        timer.text = formatTime(0L)
        timerIndicator.visibility = View.VISIBLE
        timer.visibility = View.VISIBLE
        preview.visibility = View.VISIBLE
        preview.post {
            if (pendingStart && preview.isAvailable) {
                preview.surfaceTexture?.let(::openCameraAndStart)
            }
        }
    }

    fun stop(): File? {
        pendingStart = false
        if (!isRecording) {
            val file = outputFile
            outputFile = null
            cleanupCamera()
            hidePreview()
            file?.let { runCatching { it.delete() } }
            return null
        }

        val duration = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
        mainHandler.removeCallbacks(timerRunnable)
        val file = outputFile
        val valid = try {
            recorder?.stop()
            file?.isFile == true && file.length() > MIN_VALID_FILE_BYTES && duration >= MIN_DURATION_MS
        } catch (failure: RuntimeException) {
            onFailure(failure)
            false
        } finally {
            releaseRecorder()
            isRecording = false
            cleanupCamera()
            hidePreview()
            outputFile = null
        }

        if (!valid) {
            file?.let { runCatching { it.delete() } }
            if (duration < MIN_DURATION_MS) {
                onFailure(IllegalStateException("Hold a little longer to record a video note."))
            }
            return null
        }
        return file
    }

    fun cancel() {
        pendingStart = false
        mainHandler.removeCallbacks(timerRunnable)
        val file = outputFile
        if (isRecording) {
            // MediaRecorder.stop() after a cancelled/very short recording can throw.
            runCatching { recorder?.stop() }
        }
        releaseRecorder()
        isRecording = false
        cleanupCamera()
        hidePreview()
        outputFile = null
        file?.let { runCatching { it.delete() } }
    }

    fun release() {
        cancel()
        preview.surfaceTextureListener = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        if (pendingStart) openCameraAndStart(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        preview.invalidateOutline()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (isRecording || pendingStart) cancel()
        cleanupCamera()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun openCameraAndStart(surfaceTexture: SurfaceTexture) {
        if (!pendingStart || isRecording || camera != null) return
        try {
            val openedCamera = Camera.open(preferredCameraId())
            val parameters = openedCamera.parameters
            choosePreviewSize(parameters.supportedPreviewSizes)?.let { size ->
                parameters.setPreviewSize(size.width, size.height)
            }
            parameters.supportedFocusModes
                    ?.firstOrNull { it == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO }
                    ?.let { parameters.focusMode = it }
            openedCamera.parameters = parameters
            openedCamera.setDisplayOrientation(90)
            openedCamera.setPreviewTexture(surfaceTexture)
            openedCamera.startPreview()
            camera = openedCamera
            previewSurface?.release()
            previewSurface = Surface(surfaceTexture)
            startMediaRecorder(openedCamera)
        } catch (failure: Throwable) {
            pendingStart = false
            releaseRecorder()
            cleanupCamera()
            hidePreview()
            outputFile?.let { runCatching { it.delete() } }
            outputFile = null
            onFailure(failure)
        }
    }

    private fun startMediaRecorder(activeCamera: Camera) {
        val file = outputFile ?: throw IllegalStateException("Missing video note output file")
        var candidate: MediaRecorder? = null
        try {
            activeCamera.stopPreview()
            activeCamera.unlock()
            candidate = MediaRecorder().apply {
                setCamera(activeCamera)
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(VIDEO_BIT_RATE)
                setVideoFrameRate(VIDEO_FRAME_RATE)
                chooseVideoSize(activeCamera.parameters.supportedVideoSizes)?.let { size ->
                    setVideoSize(size.width, size.height)
                }
                setOrientationHint(90)
                previewSurface?.let(::setPreviewDisplay)
                setMaxDuration(MAX_RECORDING_MS.toInt())
                setOutputFile(file.absolutePath)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED && !maxDurationCallbackSent) {
                        maxDurationCallbackSent = true
                        onMaximumDurationReached()
                    }
                }
                prepare()
                start()
            }
            recorder = candidate
            pendingStart = false
            isRecording = true
            startedAtMs = SystemClock.elapsedRealtime()
            mainHandler.removeCallbacks(timerRunnable)
            mainHandler.post(timerRunnable)
        } catch (failure: Throwable) {
            runCatching { candidate?.reset() }
            runCatching { candidate?.release() }
            recorder = null
            runCatching { activeCamera.lock() }
            runCatching { activeCamera.startPreview() }
            throw failure
        }
    }

    private fun preferredCameraId(): Int {
        val info = Camera.CameraInfo()
        for (id in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(id, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return id
        }
        return 0
    }

    private fun choosePreviewSize(sizes: List<Camera.Size>?): Camera.Size? =
            sizes
                    ?.filter { it.width <= MAX_DIMENSION && it.height <= MAX_DIMENSION }
                    ?.minByOrNull {
                        abs(it.width.toFloat() / it.height.toFloat() - 4f / 3f) * 10_000f +
                                it.width * it.height / 100_000f
                    }
                    ?: sizes?.firstOrNull()

    private fun chooseVideoSize(sizes: List<Camera.Size>?): Camera.Size? =
            sizes
                    ?.filter { it.width <= MAX_DIMENSION && it.height <= MAX_DIMENSION }
                    ?.minByOrNull {
                        abs(it.width.toFloat() / it.height.toFloat() - 1f) * 10_000f +
                                abs(it.width * it.height - VIDEO_SIZE * VIDEO_SIZE).toFloat()
                    }
                    ?: sizes?.minByOrNull { abs(it.width - it.height) }

    private fun cleanupCamera() {
        runCatching { camera?.setPreviewCallback(null) }
        runCatching { camera?.stopPreview() }
        runCatching { camera?.release() }
        camera = null
        runCatching { previewSurface?.release() }
        previewSurface = null
    }

    private fun releaseRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
    }

    private fun hidePreview() {
        preview.visibility = View.GONE
        timer.visibility = View.GONE
        timerIndicator.visibility = View.GONE
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).toInt()
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    companion object {
        private const val MAX_RECORDING_MS = 60_000L
        private const val MIN_DURATION_MS = 700L
        private const val MIN_VALID_FILE_BYTES = 1_024L
        private const val VIDEO_BIT_RATE = 1_500_000
        private const val VIDEO_FRAME_RATE = 24
        private const val VIDEO_SIZE = 480
        private const val MAX_DIMENSION = 1_280
        private const val TIMER_INTERVAL_MS = 100L
    }
}
