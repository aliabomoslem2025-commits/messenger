package com.matrixmessenger.feature.voice.data.recorder

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.io.IOException
import timber.log.Timber

/**
 * Handles audio recording using Android MediaRecorder.
 */
class AudioRecorder(private val context: Context) {
    
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0
    
    val isRecording: Boolean
        get() = mediaRecorder != null
    
    fun prepare(outputFile: File): Result<Unit> {
        return runCatching {
            this.outputFile = outputFile
            
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
            }
            Unit
        }.onFailure {
            Timber.e(it, "Failed to prepare MediaRecorder")
        }
    }
    
    fun start(): Result<Unit> {
        return runCatching {
            mediaRecorder?.start()
            startTime = System.currentTimeMillis()
            Unit
        }.onFailure {
            Timber.e(it, "Failed to start MediaRecorder")
        }
    }
    
    fun stop(): String? {
        val path = outputFile?.absolutePath
        val duration = System.currentTimeMillis() - startTime
        
        try {
            if (duration < 500) {
                // Too short, cancel instead
                cancel()
                return null
            }
            
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error stopping MediaRecorder")
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } finally {
            mediaRecorder = null
            startTime = 0
        }
        
        return path
    }
    
    fun cancel() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
            }
        } catch (e: Exception) {
            Timber.d("Recorder already stopped or error during cancel")
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } finally {
            mediaRecorder = null
            outputFile?.delete()
            outputFile = null
            startTime = 0
        }
    }
    
    fun getDurationMillis(): Long {
        return if (startTime > 0) {
            System.currentTimeMillis() - startTime
        } else {
            0L
        }
    }
    
    fun getAmplitude(): Float {
        return try {
            mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }
    
    fun getNormalizedAmplitude(): Float {
        return (getAmplitude() / 32767f).coerceIn(0f, 1f)
    }
}
