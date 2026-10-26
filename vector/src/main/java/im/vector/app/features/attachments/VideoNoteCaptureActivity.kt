/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.attachments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import im.vector.app.R
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Captures a short video note with an in-app circular camera preview.
 *
 * The resulting local file is not sent here. It is returned through the existing
 * AttachmentResult/AttachmentsHelper flow, which retains Matrix upload, encryption,
 * preview, retry, and error handling.
 */
@Suppress("DEPRECATION")
class VideoNoteCaptureActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    companion object {
        const val EXTRA_OUTPUT_PATH = "im.vector.app.extra.VIDEO_NOTE_OUTPUT_PATH"
        private const val MAX_RECORDING_MS = 60_000L
    }

    private lateinit var preview: TextureView
    private lateinit var timer: TextView
    private lateinit var recordButton: MaterialButton
    private lateinit var sendButton: MaterialButton
    private lateinit var cancelButton: MaterialButton

    private val handler = Handler(Looper.getMainLooper())
    private var outputPath: String? = null
    private var camera: Camera? = null
    private var recorder: MediaRecorder? = null
    private var previewSurface: Surface? = null
    private var recordingStartedAt = 0L
    private var isRecording = false
    private var hasRecording = false
    private var handedOffFile = false

    private val updateTimer = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = (SystemClock.elapsedRealtime() - recordingStartedAt).coerceAtLeast(0L)
            timer.text = formatTime(elapsed)
            if (elapsed >= MAX_RECORDING_MS) {
                stopRecording()
            } else {
                handler.postDelayed(this, 200L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telegram_video_note_capture)

        outputPath = intent.getStringExtra(EXTRA_OUTPUT_PATH)?.takeIf { it.isNotBlank() }
        if (outputPath == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        preview = findViewById(R.id.telegramVideoNotePreview)
        timer = findViewById(R.id.telegramVideoNoteTimer)
        recordButton = findViewById(R.id.telegramVideoNoteRecord)
        sendButton = findViewById(R.id.telegramVideoNoteSend)
        cancelButton = findViewById(R.id.telegramVideoNoteCancel)

        preview.clipToOutline = true
        preview.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        preview.surfaceTextureListener = this

        recordButton.setOnClickListener {
            if (isRecording) stopRecording() else if (!hasRecording) startRecording()
        }
        sendButton.setOnClickListener {
            if (hasRecording && outputFileExists()) {
                handedOffFile = true
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_OUTPUT_PATH, outputPath))
                finish()
            }
        }
        cancelButton.setOnClickListener { cancelAndFinish() }

        renderReadyState()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.telegram_video_note_permission_error, Toast.LENGTH_LONG).show()
            cancelAndFinish()
        } else if (preview.isAvailable) {
            preview.surfaceTexture?.let(::openCameraPreview)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        openCameraPreview(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        preview.invalidateOutline()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (isRecording) stopRecording()
        releaseCamera()
        previewSurface?.release()
        previewSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    private fun openCameraPreview(surfaceTexture: SurfaceTexture) {
        if (camera != null || isFinishing) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            cancelAndFinish()
            return
        }
        try {
            val cameraId = preferredCameraId()
            val openedCamera = Camera.open(cameraId)
            val parameters = openedCamera.parameters
            choosePreviewSize(parameters.supportedPreviewSizes)?.let { size ->
                parameters.setPreviewSize(size.width, size.height)
            }
            parameters.focusMode = parameters.supportedFocusModes
                    ?.firstOrNull { it == Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO }
                    ?: parameters.focusMode
            openedCamera.parameters = parameters
            openedCamera.setDisplayOrientation(90)
            openedCamera.setPreviewTexture(surfaceTexture)
            openedCamera.startPreview()
            camera = openedCamera
            previewSurface?.release()
            previewSurface = Surface(surfaceTexture)
        } catch (failure: Exception) {
            Toast.makeText(this, R.string.telegram_video_note_camera_error, Toast.LENGTH_LONG).show()
            cancelAndFinish()
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

    private fun choosePreviewSize(sizes: List<Camera.Size>?): Camera.Size? {
        return sizes
                ?.filter { it.width <= 1280 && it.height <= 1280 }
                ?.minByOrNull { abs(it.width.toFloat() / it.height.toFloat() - 4f / 3f) * 10_000f + it.width * it.height / 100_000f }
                ?: sizes?.firstOrNull()
    }

    private fun chooseVideoSize(sizes: List<Camera.Size>?): Camera.Size? {
        return sizes
                ?.filter { it.width <= 1280 && it.height <= 1280 }
                ?.minByOrNull {
                    abs(it.width.toFloat() / it.height.toFloat() - 1f) * 10_000f +
                            abs(it.width * it.height - 480 * 480).toFloat()
                }
                ?: sizes?.minByOrNull { abs(it.width - it.height) }
    }

    private fun startRecording() {
        val activeCamera = camera
        val path = outputPath
        if (activeCamera == null || path.isNullOrBlank()) {
            Toast.makeText(this, R.string.telegram_video_note_camera_error, Toast.LENGTH_LONG).show()
            return
        }

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
                setVideoEncodingBitRate(1_500_000)
                setVideoFrameRate(24)
                chooseVideoSize(activeCamera.parameters.supportedVideoSizes)?.let { size ->
                    setVideoSize(size.width, size.height)
                }
                setOrientationHint(90)
                previewSurface?.let(::setPreviewDisplay)
                setOutputFile(path)
                prepare()
                start()
            }
            recorder = candidate
            isRecording = true
            recordingStartedAt = SystemClock.elapsedRealtime()
            timer.text = formatTime(0L)
            renderRecordingState()
            handler.removeCallbacks(updateTimer)
            handler.post(updateTimer)
        } catch (failure: Exception) {
            runCatching { candidate?.reset() }
            runCatching { candidate?.release() }
            recorder = null
            runCatching { activeCamera.lock() }
            runCatching { activeCamera.startPreview() }
            Toast.makeText(this, R.string.telegram_video_note_recording_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        handler.removeCallbacks(updateTimer)
        var stoppedSuccessfully = false
        try {
            recorder?.stop()
            stoppedSuccessfully = outputFileExists()
        } catch (_: RuntimeException) {
            stoppedSuccessfully = false
        } finally {
            runCatching { recorder?.reset() }
            runCatching { recorder?.release() }
            recorder = null
            runCatching { camera?.lock() }
            runCatching { camera?.startPreview() }
            isRecording = false
        }

        if (stoppedSuccessfully) {
            hasRecording = true
            renderReadyState()
        } else {
            outputPath?.let { runCatching { File(it).delete() } }
            timer.text = formatTime(0L)
            Toast.makeText(this, R.string.telegram_video_note_recording_failed, Toast.LENGTH_LONG).show()
            renderReadyState()
        }
    }

    private fun renderRecordingState() {
        recordButton.text = getString(R.string.telegram_video_note_stop)
        sendButton.visibility = View.GONE
        cancelButton.isEnabled = true
    }

    private fun renderReadyState() {
        recordButton.text = getString(R.string.telegram_video_note_record)
        recordButton.isEnabled = !hasRecording
        sendButton.visibility = if (hasRecording) View.VISIBLE else View.GONE
        timer.text = if (hasRecording) timer.text else formatTime(0L)
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1_000L).toInt()
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    private fun outputFileExists(): Boolean {
        val path = outputPath ?: return false
        return runCatching { File(path).let { it.isFile && it.length() > 0L } }.getOrDefault(false)
    }

    private fun cancelAndFinish() {
        if (isRecording) stopRecording()
        releaseRecorder()
        releaseCamera()
        outputPath?.let { runCatching { File(it).delete() } }
        handedOffFile = false
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun releaseRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        isRecording = false
        handler.removeCallbacks(updateTimer)
    }

    private fun releaseCamera() {
        runCatching { camera?.stopPreview() }
        runCatching { camera?.release() }
        camera = null
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) cancelAndFinish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateTimer)
        releaseRecorder()
        releaseCamera()
        previewSurface?.release()
        previewSurface = null
        if (!handedOffFile) outputPath?.let { runCatching { File(it).delete() } }
        super.onDestroy()
    }
}
