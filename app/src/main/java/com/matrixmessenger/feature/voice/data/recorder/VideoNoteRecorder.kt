package com.matrixmessenger.feature.voice.data.recorder

import android.content.Context
import android.util.Size
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoNoteRecorder(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    fun startPreview(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onReady: () -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(480, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.SD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                    )
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                onReady()
            } catch (e: Exception) {
                Timber.e(e, "Use case binding failed")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(outputFile: File, onFinished: (File?) -> Unit): Result<Unit> {
        Timber.d("VIDEO_NOTE_RECORDER: startRecording to $outputFile")
        return runCatching {
            val capture = videoCapture ?: throw IllegalStateException("VideoCapture not initialized")
            
            val outputOptions = FileOutputOptions.Builder(outputFile).build()
            
            recording = capture.output
                .prepareRecording(context, outputOptions)
                .withAudioEnabled()
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            Timber.d("VIDEO_NOTE_RECORDER: Recording started")
                        }
                        is VideoRecordEvent.Finalize -> {
                            Timber.d("VIDEO_NOTE_RECORDER: Recording finalized. Error: ${event.error}")
                            if (event.hasError()) {
                                onFinished(null)
                            } else {
                                onFinished(outputFile)
                            }
                        }
                    }
                }
        }.onFailure {
            Timber.e(it, "VIDEO_NOTE_RECORDER: Failed to start recording")
        }
    }

    fun stopRecording() {
        Timber.d("VIDEO_NOTE_RECORDER: stopRecording requested")
        recording?.stop()
        recording = null
    }

    fun stopPreview() {
        cameraProvider?.unbindAll()
        videoCapture = null
    }

    fun release() {
        cameraExecutor.shutdown()
    }
}
