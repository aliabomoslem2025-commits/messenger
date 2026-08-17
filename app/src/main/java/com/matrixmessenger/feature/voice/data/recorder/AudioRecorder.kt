package com.matrixmessenger.feature.voice.data.recorder

import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

/**
 * Handles audio recording using Android MediaRecorder.
 * Manages recording lifecycle, amplitude monitoring, and file output.
 */
class AudioRecorder {
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0
    
    val isRecording: Boolean
        get() = mediaRecorder != null
    
    /**
     * Prepare the recorder with a new output file.
     */
    fun prepare(outputFile: File): Result<Unit> {
        try {
            this.outputFile = outputFile
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                createMediaRecorderV31()
            } else {
                createMediaRecorderLegacy()
            }
            
            mediaRecorder?.setOutputFile(outputFile.absolutePath)
            mediaRecorder?.prepare()
            
            return Result.success(Unit)
        } catch (e: IOException) {
            return Result.failure(e)
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.S)
    private fun createMediaRecorderV31(): MediaRecorder {
        return MediaRecorder.Builder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64000)
            setAudioSamplingRate(44100)
        }.build()
    }
    
    @Suppress("DEPRECATION")
    private fun createMediaRecorderLegacy(): MediaRecorder {
        return MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(64000)
            setAudioSamplingRate(44100)
        }
    }
    
    /**
     * Start recording.
     */
    fun start(): Result<Unit> {
        return try {
            mediaRecorder?.start()
            startTime = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stop recording and release resources.
     * @return The path to the recorded file, or null if failed.
     */
    fun stop(): String? {
        val path = outputFile?.absolutePath
        
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: RuntimeException) {
            // Sometimes stop() throws if recording was very short
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } finally {
            mediaRecorder = null
        }
        
        return path
    }
    
    /**
     * Cancel recording and delete the output file.
     */
    fun cancel() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            // Ignore errors during cancel
        } finally {
            mediaRecorder = null
            outputFile?.delete()
            outputFile = null
        }
    }
    
    /**
     * Get current recording duration in milliseconds.
     */
    fun getDurationMillis(): Long {
        return if (startTime > 0) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }
    }
    
    /**
     * Get current audio amplitude (0-32767).
     * Useful for waveform visualization.
     */
    fun getAmplitude(): Float {
        return try {
            mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }
    
    /**
     * Get normalized amplitude (0.0 - 1.0).
     */
    fun getNormalizedAmplitude(): Float {
        return (getAmplitude() / 32767f).coerceIn(0f, 1f)
    }
}
