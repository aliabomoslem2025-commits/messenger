/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

@file:Suppress("DEPRECATION")

package im.vector.app.features.home.room.detail.composer.voice

import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.CamcorderProfile
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
        private val timer: View,
        private val timerIndicator: ImageView,
        private val progressRing: VideoNoteProgressRingView,
        private val onFailure: (Throwable) -> Unit,
        private val onMaximumDurationReached: () -> Unit,
) : TextureView.SurfaceTextureListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    // Build-time override: vector/src/main/res/values/telegram_video_note_config.xml
    private val maxRecordingDurationMs = preview.resources
            .getInteger(R.integer.telegram_video_note_max_duration_seconds)
            .coerceIn(1, 60).toLong() * 1_000L
    private var camera: Camera? = null
    private var cameraParameters: Camera.Parameters? = null
    private var preferFrontCamera = true
    private var flashEnabled = false
    private var previewOrientation = 90
    private var recordingOrientationHint = 90
    private var recorder: MediaRecorder? = null
    private var previewSurface: Surface? = null
    private var outputFile: File? = null
    private var pendingStart = false
    private var isRecording = false
    private var startedAtMs = 0L
    private var maxDurationCallbackSent = false
    private var stopRequested = false
    private var pendingStopCallback: ((File?) -> Unit)? = null
    private var pendingStopTimeout: Runnable? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
            if (timer is AnimatedClockTickerView) timer.setTimeMs(elapsed) else if (timer is TextView) timer.text = formatTime(elapsed)
            progressRing.progress = (elapsed.toFloat() / maxRecordingDurationMs).coerceIn(0f, 1f)
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
        progressRing.visibility = View.GONE
        progressRing.progress = 0f
    }

    /**
     * Starts on the existing ACTION_DOWN hold. The camera preview appears inside the composer.
     * Texture creation can be asynchronous, so start() defers camera setup when necessary.
     */
    fun start(file: File) {
        if (isRecording || pendingStart) cancel()
        outputFile = file
        startedAtMs = 0L
        pendingStart = true
        maxDurationCallbackSent = false
        if (timer is AnimatedClockTickerView) timer.setTimeMs(0L) else if (timer is TextView) timer.text = formatTime(0L)
        timerIndicator.visibility = View.VISIBLE
        timer.visibility = View.VISIBLE
        progressRing.visibility = View.VISIBLE
        progressRing.progress = 0f
        preview.visibility = View.VISIBLE
        preview.post {
            if (pendingStart && preview.isAvailable) {
                preview.surfaceTexture?.let(::openCameraAndStart)
            }
        }
    }

    /**
     * Finish immediately when recording has started. If camera initialization is still in
     * progress, retain the surface and stop as soon as MediaRecorder becomes ready.
     */
    fun stop(onComplete: (File?) -> Unit) {
        if (isRecording) {
            onComplete(stopNow())
            return
        }
        if (!pendingStart) {
            onComplete(null)
            return
        }

        stopRequested = true
        pendingStopCallback = onComplete
        pendingStopTimeout?.let(mainHandler::removeCallbacks)
        pendingStopTimeout = Runnable {
            if (stopRequested && !isRecording) {
                stopRequested = false
                pendingStart = false
                val callback = pendingStopCallback
                pendingStopCallback = null
                releaseRecorder()
                cleanupCamera()
                hidePreview()
                outputFile?.let { runCatching { it.delete() } }
                outputFile = null
                callback?.invoke(null)
            }
        }.also { mainHandler.postDelayed(it, CAMERA_START_TIMEOUT_MS) }
    }

    private fun stopNow(): File? {
        pendingStart = false
        mainHandler.removeCallbacks(timerRunnable)
        val duration = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
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
        stopRequested = false
        pendingStopCallback = null
        pendingStopTimeout?.let(mainHandler::removeCallbacks)
        pendingStopTimeout = null
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
            val cameraId = preferredCameraId()
            val cameraInfo = Camera.CameraInfo()
            Camera.getCameraInfo(cameraId, cameraInfo)
            val orientation = calculateCameraOrientations(cameraInfo)
            previewOrientation = orientation.first
            recordingOrientationHint = orientation.second
            val openedCamera = Camera.open(cameraId)
            val parameters = openedCamera.parameters
            choosePreviewSize(parameters.supportedPreviewSizes)?.let { size ->
                parameters.setPreviewSize(size.width, size.height)
            }
            parameters.supportedFocusModes
                    ?.firstOrNull { it == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO }
                    ?.let { parameters.focusMode = it }
            // Read supported recording sizes while the camera still owns its parameters.
            // MediaRecorder takes ownership after Camera.unlock(); querying parameters
            // after that point can return an empty parameter set on several camera drivers.
            val videoSize = (chooseVideoSize(parameters.supportedVideoSizes)
                    ?: choosePreviewSize(parameters.supportedPreviewSizes))
                    ?.let { it.width to it.height }
            if (flashEnabled && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK &&
                    parameters.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) == true) {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            }
            openedCamera.parameters = parameters
            cameraParameters = parameters
            openedCamera.setDisplayOrientation(previewOrientation)
            openedCamera.setPreviewTexture(surfaceTexture)
            openedCamera.startPreview()
            camera = openedCamera
            previewSurface?.release()
            previewSurface = Surface(surfaceTexture)
            startMediaRecorder(openedCamera, videoSize)
            if (stopRequested && isRecording) {
                pendingStopTimeout?.let(mainHandler::removeCallbacks)
                pendingStopTimeout = null
                stopRequested = false
                val callback = pendingStopCallback
                pendingStopCallback = null
                mainHandler.post { callback?.invoke(stopNow()) }
            }
        } catch (failure: Throwable) {
            pendingStart = false
            releaseRecorder()
            cleanupCamera()
            hidePreview()
            outputFile?.let { runCatching { it.delete() } }
            outputFile = null
            pendingStopTimeout?.let(mainHandler::removeCallbacks)
            pendingStopTimeout = null
            stopRequested = false
            val stopCallback = pendingStopCallback
            pendingStopCallback = null
            stopCallback?.invoke(null)
            onFailure(failure)
        }
    }

    private fun startMediaRecorder(activeCamera: Camera, videoSize: Pair<Int, Int>?) {
        val file = outputFile ?: throw IllegalStateException("Missing video note output file")
        var candidate: MediaRecorder? = null
        try {
            activeCamera.stopPreview()
            activeCamera.unlock()
            val cameraId = preferredCameraId()
            val profile = runCatching {
                when {
                    CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P) ->
                        CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                    CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_LOW) ->
                        CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW)
                    else -> null
                }
            }.getOrNull()
            candidate = MediaRecorder().apply {
                setCamera(activeCamera)
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                if (profile != null) {
                    setProfile(profile)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoEncodingBitRate(VIDEO_BIT_RATE)
                    setVideoFrameRate(VIDEO_FRAME_RATE)
                    videoSize?.let { size -> setVideoSize(size.first, size.second) }
                }
                setOrientationHint(recordingOrientationHint)
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
            if (startedAtMs == 0L) startedAtMs = SystemClock.elapsedRealtime()
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

    /** Changes the preferred lens for the next video note. */
    fun switchCameraForNextCapture(): Boolean {
        val desiredFacing = if (preferFrontCamera) Camera.CameraInfo.CAMERA_FACING_BACK else Camera.CameraInfo.CAMERA_FACING_FRONT
        val available = (0 until Camera.getNumberOfCameras()).any { id ->
            val info = Camera.CameraInfo()
            Camera.getCameraInfo(id, info)
            info.facing == desiredFacing
        }
        if (!available) return false
        preferFrontCamera = !preferFrontCamera
        return true
    }

    /**
     * Toggle torch using the parameter snapshot captured before Camera.unlock().
     * Some legacy camera HALs return empty parameters after MediaRecorder takes
     * ownership; never query camera.parameters again in that state.
     */
    fun toggleFlash(): Boolean? {
        val parameters = cameraParameters ?: return null
        val modes = parameters.supportedFlashModes.orEmpty()
        if (Camera.Parameters.FLASH_MODE_TORCH !in modes) return null
        val desired = !flashEnabled
        parameters.flashMode = if (desired) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
        val applied = runCatching { camera?.parameters = parameters }.isSuccess
        if (applied) flashEnabled = desired
        return if (applied) flashEnabled else null
    }

    private fun preferredCameraId(): Int {
        val desiredFacing = if (preferFrontCamera) Camera.CameraInfo.CAMERA_FACING_FRONT else Camera.CameraInfo.CAMERA_FACING_BACK
        val info = Camera.CameraInfo()
        for (id in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(id, info)
            if (info.facing == desiredFacing) return id
        }
        return 0
    }

    /**
     * Preview and encoded-video orientation are not the same for a front camera:
     * preview is mirrored for the user, while the recorded MP4 must not inherit
     * that mirror-compensation rotation.
     */
    private fun calculateCameraOrientations(info: Camera.CameraInfo): Pair<Int, Int> {
        val displayRotation = preview.display?.rotation ?: Surface.ROTATION_0
        val displayDegrees = when (displayRotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            val sensorAndDisplay = (info.orientation + displayDegrees) % 360
            val previewDegrees = (360 - sensorAndDisplay) % 360
            previewDegrees to sensorAndDisplay
        } else {
            val degrees = (info.orientation - displayDegrees + 360) % 360
            degrees to degrees
        }
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
        cameraParameters = null
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
        private const val CAMERA_START_TIMEOUT_MS = 2_500L
        private const val MIN_DURATION_MS = 700L
        private const val MIN_VALID_FILE_BYTES = 1_024L
        private const val VIDEO_BIT_RATE = 1_500_000
        private const val VIDEO_FRAME_RATE = 24
        private const val VIDEO_SIZE = 480
        private const val MAX_DIMENSION = 1_280
        private const val TIMER_INTERVAL_MS = 100L
    }
}
