/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

@file:Suppress("DEPRECATION")

package im.vector.app.features.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import im.vector.app.R
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Telegram-like in-app story camera. Photo and Video are intentionally the only
 * capture modes; the Live mode is not part of this client.
 *
 * Files are written under files/media so the existing MultiPicker FileProvider
 * can grant the Matrix upload pipeline access without exposing file:// URIs.
 */
class TelegramStoryCameraActivity : AppCompatActivity(), TextureView.SurfaceTextureListener {

    companion object {
        const val EXTRA_STORY_URI = "im.vector.app.extra.STORY_MEDIA_URI"
        const val EXTRA_STORY_MIME = "im.vector.app.extra.STORY_MEDIA_MIME"
        private const val REQUEST_CAMERA = 4101
        private const val REQUEST_AUDIO = 4102
        private const val MAX_VIDEO_MS = 60_000L
        private const val MIN_VIDEO_MS = 700L
    }

    private enum class CaptureMode { PHOTO, VIDEO }

    private lateinit var preview: TextureView
    private lateinit var timer: TextView
    private lateinit var shutter: View
    private lateinit var photoMode: TextView
    private lateinit var videoMode: TextView
    private lateinit var gridOverlay: GridOverlayView

    private val handler = Handler(Looper.getMainLooper())
    private var camera: Camera? = null
    private var recorder: MediaRecorder? = null
    private var recorderSurface: Surface? = null
    private var currentCameraId = 0
    private var mode = CaptureMode.PHOTO
    private var showGrid = false
    private var isRecording = false
    private var recordingStartedAt = 0L
    private var recordingFile: File? = null

    private val galleryPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            returnMedia(uri, contentResolver.getType(uri) ?: "application/octet-stream")
        }
    }

    private val updateRecordingTimer = object : Runnable {
        override fun run() {
            if (!isRecording) return
            val elapsed = (SystemClock.elapsedRealtime() - recordingStartedAt).coerceAtLeast(0L)
            timer.text = formatDuration(elapsed)
            if (elapsed >= MAX_VIDEO_MS) {
                stopVideo(send = true)
            } else {
                handler.postDelayed(this, 150L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.decorView.systemUiVisibility = 0
        buildCameraUi()

        if (hasPermission(Manifest.permission.CAMERA)) {
            preview.surfaceTexture?.let(::openCameraPreview)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    private fun buildCameraUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = TextureView(this).apply {
            surfaceTextureListener = this@TelegramStoryCameraActivity
        }
        root.addView(preview, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ))

        gridOverlay = GridOverlayView()
        gridOverlay.visibility = View.GONE
        root.addView(gridOverlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ))

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(16), dp(12), dp(8))
            background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x99000000.toInt(), Color.TRANSPARENT)
            )
        }
        topBar.addView(textButton("‹", 30f) { finish() }, LinearLayout.LayoutParams(dp(52), dp(52)))
        timer = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
        topBar.addView(timer, LinearLayout.LayoutParams(0, dp(48), 1f))
        topBar.addView(textButton("▦", 23f) {
            showGrid = !showGrid
            gridOverlay.visibility = if (showGrid) View.VISIBLE else View.GONE
        }, LinearLayout.LayoutParams(dp(52), dp(52)))
        root.addView(topBar, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
        ))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(28), dp(16), dp(18))
            background = android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(0xCC000000.toInt(), 0x00000000)
            )
        }
        val captureRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        captureRow.addView(textButton("▧", 26f) { openGallery() }, LinearLayout.LayoutParams(0, dp(72), 1f))
        shutter = ShutterButton()
        shutter.contentDescription = getString(R.string.telegram_story_take_photo)
        shutter.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (mode == CaptureMode.VIDEO) startVideo()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (mode == CaptureMode.PHOTO) takePhoto() else stopVideo(send = true)
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (mode == CaptureMode.VIDEO) stopVideo(send = false)
                    true
                }
                else -> true
            }
        }
        captureRow.addView(shutter, LinearLayout.LayoutParams(dp(82), dp(82)).apply {
            gravity = Gravity.CENTER
        })
        captureRow.addView(textButton("↻", 27f) { switchCamera() }, LinearLayout.LayoutParams(0, dp(72), 1f))
        controls.addView(captureRow)

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(4))
        }
        photoMode = modeLabel(getString(R.string.telegram_story_photo))
        videoMode = modeLabel(getString(R.string.telegram_story_video))
        photoMode.setOnClickListener { setMode(CaptureMode.PHOTO) }
        videoMode.setOnClickListener { setMode(CaptureMode.VIDEO) }
        modeRow.addView(photoMode, LinearLayout.LayoutParams(dp(92), dp(34)))
        modeRow.addView(videoMode, LinearLayout.LayoutParams(dp(92), dp(34)))
        controls.addView(modeRow)
        root.addView(controls, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        ))

        setContentView(root)
        setMode(CaptureMode.PHOTO)
    }

    private fun modeLabel(label: String) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(Color.WHITE)
        isFocusable = true
        isClickable = true
    }

    private fun setMode(newMode: CaptureMode) {
        if (isRecording) return
        mode = newMode
        fun style(view: TextView, selected: Boolean) {
            view.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (selected) 0xFF555555.toInt() else Color.TRANSPARENT)
            }
            view.setTextColor(if (selected) Color.WHITE else 0xFFBBBBBB.toInt())
        }
        style(photoMode, mode == CaptureMode.PHOTO)
        style(videoMode, mode == CaptureMode.VIDEO)
        shutter.contentDescription = getString(
                if (mode == CaptureMode.PHOTO) R.string.telegram_story_take_photo else R.string.telegram_story_record_video
        )
        (shutter as? ShutterButton)?.recordMode = mode == CaptureMode.VIDEO
        shutter.invalidate()
    }

    private fun textButton(label: String, size: Float, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }

    private fun openGallery() {
        if (isRecording) return
        galleryPicker.launch(arrayOf("image/*", "video/*"))
    }

    private fun takePhoto() {
        val activeCamera = camera ?: return
        if (isRecording || isFinishing) return
        try {
            activeCamera.takePicture(null, null) { bytes, deviceCamera ->
                try {
                    val file = createOutputFile(".jpg")
                    file.writeBytes(bytes)
                    returnMedia(createFileUri(file), "image/jpeg")
                } catch (_: Exception) {
                    Toast.makeText(this, R.string.telegram_story_camera_error, Toast.LENGTH_LONG).show()
                    runCatching { deviceCamera.startPreview() }
                }
            }
        } catch (_: Exception) {
            Toast.makeText(this, R.string.telegram_story_camera_error, Toast.LENGTH_LONG).show()
            runCatching { activeCamera.startPreview() }
        }
    }

    private fun startVideo() {
        if (isRecording || isFinishing) return
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            Toast.makeText(this, R.string.telegram_story_microphone_permission, Toast.LENGTH_SHORT).show()
            return
        }
        val activeCamera = camera ?: return
        var candidate: MediaRecorder? = null
        try {
            val file = createOutputFile(".mp4")
            activeCamera.stopPreview()
            activeCamera.unlock()
            val previewTexture = preview.surfaceTexture ?: error("Camera preview is unavailable")
            recorderSurface?.release()
            recorderSurface = Surface(previewTexture)
            candidate = MediaRecorder().apply {
                setCamera(activeCamera)
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.CAMERA)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(4_000_000)
                setVideoFrameRate(30)
                chooseVideoSize(activeCamera.parameters.supportedVideoSizes)?.let { size ->
                    setVideoSize(size.width, size.height)
                }
                setOrientationHint(90)
                setPreviewDisplay(recorderSurface)
                setMaxDuration(MAX_VIDEO_MS.toInt())
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = candidate
            recordingFile = file
            isRecording = true
            recordingStartedAt = SystemClock.elapsedRealtime()
            timer.text = formatDuration(0L)
            handler.removeCallbacks(updateRecordingTimer)
            handler.post(updateRecordingTimer)
            shutter.isEnabled = true
            (shutter as? ShutterButton)?.isRecording = true
            photoMode.isEnabled = false
            videoMode.isEnabled = false
        } catch (_: Exception) {
            runCatching { candidate?.reset() }
            runCatching { candidate?.release() }
            recorder = null
            runCatching { activeCamera.lock() }
            runCatching { activeCamera.startPreview() }
            recorderSurface?.release()
            recorderSurface = null
            recordingFile?.delete()
            recordingFile = null
            Toast.makeText(this, R.string.telegram_story_recording_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVideo(send: Boolean) {
        if (!isRecording) return
        val elapsed = (SystemClock.elapsedRealtime() - recordingStartedAt).coerceAtLeast(0L)
        handler.removeCallbacks(updateRecordingTimer)
        val stopped = try {
            recorder?.stop()
            recordingFile?.let { it.isFile && it.length() > 0L } == true
        } catch (_: RuntimeException) {
            false
        } finally {
            runCatching { recorder?.reset() }
            runCatching { recorder?.release() }
            recorder = null
            runCatching { camera?.lock() }
            runCatching { camera?.startPreview() }
            recorderSurface?.release()
            recorderSurface = null
            isRecording = false
            (shutter as? ShutterButton)?.isRecording = false
            photoMode.isEnabled = true
            videoMode.isEnabled = true
            timer.text = ""
        }

        val file = recordingFile
        recordingFile = null
        if (send && stopped && elapsed >= MIN_VIDEO_MS && file != null) {
            returnMedia(createFileUri(file), "video/mp4")
        } else {
            file?.delete()
            if (send && elapsed < MIN_VIDEO_MS) {
                Toast.makeText(this, R.string.telegram_story_recording_too_short, Toast.LENGTH_SHORT).show()
            } else if (send) {
                Toast.makeText(this, R.string.telegram_story_recording_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun switchCamera() {
        if (isRecording || !hasPermission(Manifest.permission.CAMERA)) return
        val cameras = Camera.getNumberOfCameras()
        if (cameras < 2) {
            Toast.makeText(this, R.string.telegram_video_note_camera_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        currentCameraId = (currentCameraId + 1) % cameras
        releaseCamera()
        preview.surfaceTexture?.let(::openCameraPreview)
    }

    private fun openCameraPreview(surfaceTexture: SurfaceTexture) {
        if (camera != null || isFinishing || !hasPermission(Manifest.permission.CAMERA)) return
        try {
            val opened = Camera.open(currentCameraId.coerceIn(0, (Camera.getNumberOfCameras() - 1).coerceAtLeast(0)))
            val parameters = opened.parameters
            choosePreviewSize(parameters.supportedPreviewSizes)?.let { size ->
                parameters.setPreviewSize(size.width, size.height)
            }
            parameters.focusMode = parameters.supportedFocusModes
                    ?.firstOrNull { it == Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE }
                    ?: parameters.focusMode
            opened.parameters = parameters
            opened.setDisplayOrientation(90)
            opened.setPreviewTexture(surfaceTexture)
            opened.startPreview()
            camera = opened
        } catch (_: Exception) {
            Toast.makeText(this, R.string.telegram_story_camera_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun choosePreviewSize(sizes: List<Camera.Size>?): Camera.Size? {
        return sizes
                ?.filter { it.width <= 1920 && it.height <= 1920 }
                ?.minByOrNull {
                    abs(it.width.toFloat() / it.height.toFloat() - 4f / 3f) * 10_000f +
                            it.width * it.height / 100_000f
                }
                ?: sizes?.firstOrNull()
    }

    private fun chooseVideoSize(sizes: List<Camera.Size>?): Camera.Size? {
        return sizes
                ?.filter { it.width <= 1920 && it.height <= 1920 }
                ?.minByOrNull {
                    abs(it.width.toFloat() / it.height.toFloat() - 9f / 16f) * 10_000f +
                            abs(it.width * it.height - 1080 * 1920).toFloat()
                }
                ?: sizes?.firstOrNull()
    }

    private fun createOutputFile(extension: String): File {
        val directory = File(filesDir, "media").apply { mkdirs() }
        return File.createTempFile("story_", extension, directory)
    }

    private fun createFileUri(file: File): Uri {
        return FileProvider.getUriForFile(this, packageName + ".multipicker.fileprovider", file)
    }

    private fun returnMedia(uri: Uri, mimeType: String) {
        setResult(Activity.RESULT_OK, Intent()
                .putExtra(EXTRA_STORY_URI, uri.toString())
                .putExtra(EXTRA_STORY_MIME, mimeType)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        finish()
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CAMERA -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    preview.surfaceTexture?.let(::openCameraPreview)
                } else {
                    Toast.makeText(this, R.string.telegram_story_camera_permission, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
            REQUEST_AUDIO -> {
                if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
                    setMode(CaptureMode.PHOTO)
                    Toast.makeText(this, R.string.telegram_story_microphone_permission, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        openCameraPreview(surface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        gridOverlay.invalidate()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        if (isRecording) stopVideo(send = false)
        releaseCamera()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    override fun onResume() {
        super.onResume()
        // OpenDocument backgrounds this activity. Recreate the preview when the
        // user returns from the picker or grants camera access in Settings.
        if (!isFinishing && camera == null && hasPermission(Manifest.permission.CAMERA) && preview.isAvailable) {
            preview.surfaceTexture?.let(::openCameraPreview)
        }
    }

    override fun onPause() {
        super.onPause()
        if (isRecording) stopVideo(send = false)
        releaseCamera()
    }

    override fun onDestroy() {
        handler.removeCallbacks(updateRecordingTimer)
        if (isRecording) stopVideo(send = false)
        releaseRecorder()
        releaseCamera()
        recorderSurface?.release()
        recorderSurface = null
        super.onDestroy()
    }

    private fun releaseRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        isRecording = false
    }

    private fun releaseCamera() {
        runCatching { camera?.stopPreview() }
        runCatching { camera?.release() }
        camera = null
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = (milliseconds / 1_000L).toInt()
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class GridOverlayView : View(this@TelegramStoryCameraActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF
            strokeWidth = dp(1).toFloat()
            style = Paint.Style.STROKE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!showGrid) return
            val thirdX = width / 3f
            val thirdY = height / 3f
            canvas.drawLine(thirdX, 0f, thirdX, height.toFloat(), paint)
            canvas.drawLine(thirdX * 2, 0f, thirdX * 2, height.toFloat(), paint)
            canvas.drawLine(0f, thirdY, width.toFloat(), thirdY, paint)
            canvas.drawLine(0f, thirdY * 2, width.toFloat(), thirdY * 2, paint)
        }
    }

    private inner class ShutterButton : View(this@TelegramStoryCameraActivity) {
        var recordMode = false
        var isRecording = false
        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(4).toFloat()
        }
        private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val centerX = width / 2f
            val centerY = height / 2f
            val outerRadius = minOf(width, height) / 2f - dp(5)
            canvas.drawCircle(centerX, centerY, outerRadius, outerPaint)
            val radius = outerRadius - dp(8)
            if (recordMode || isRecording) {
                innerPaint.color = 0xFFFF3B30.toInt()
                if (isRecording) {
                    val rect = android.graphics.RectF(
                            centerX - radius * 0.55f,
                            centerY - radius * 0.55f,
                            centerX + radius * 0.55f,
                            centerY + radius * 0.55f
                    )
                    canvas.drawRoundRect(rect, dp(6).toFloat(), dp(6).toFloat(), innerPaint)
                } else {
                    canvas.drawCircle(centerX, centerY, radius, innerPaint)
                }
            } else {
                innerPaint.color = Color.WHITE
                canvas.drawCircle(centerX, centerY, radius, innerPaint)
            }
        }

        override fun setPressed(pressed: Boolean) {
            super.setPressed(pressed)
            invalidate()
        }
    }
}
