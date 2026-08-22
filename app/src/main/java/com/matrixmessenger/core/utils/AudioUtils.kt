package com.matrixmessenger.core.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Utility functions for audio operations
 */

object AudioUtils {

    /**
     * Get audio duration from file
     */
    fun getAudioDuration(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Get audio duration from file path
     */
    fun getAudioDuration(context: Context, filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) {
            getAudioDuration(context, Uri.fromFile(file))
        } else {
            0L
        }
    }

    /**
     * Check if URI is playable audio
     */
    fun isPlayableAudio(context: Context, uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            mimeType?.startsWith("audio/") == true || 
            mimeType?.startsWith("video/") == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Format audio duration to MM:SS
     */
    fun formatAudioDuration(durationMs: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
        val remainingSeconds = seconds % 60
        
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    /**
     * Get waveform data from audio file (simplified)
     * Returns array of amplitude values for visualization
     */
    fun getWaveformData(context: Context, uri: Uri, sampleCount: Int = 50): FloatArray {
        val waveformData = FloatArray(sampleCount)
        
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            
            if (duration > 0) {
                val sampleInterval = duration / sampleCount
                
                for (i in 0 until sampleCount) {
                    val timeUs = (i * sampleInterval) * 1000 // Convert to microseconds
                    val frame = retriever.getFrameAtTime(timeUs)
                    
                    // Simplified amplitude calculation based on frame brightness
                    val amplitude = if (frame != null) {
                        calculateFrameBrightness(frame)
                    } else {
                        0f
                    }
                    
                    waveformData[i] = amplitude
                }
            }
            
            retriever.release()
            waveformData
        } catch (e: Exception) {
            waveformData
        }
    }

    /**
     * Calculate brightness of a frame as a proxy for audio amplitude
     */
    private fun calculateFrameBrightness(bitmap: android.graphics.Bitmap): Float {
        val width = bitmap.width.coerceAtMost(10)
        val height = bitmap.height.coerceAtMost(10)
        
        var totalBrightness = 0.0
        var pixelCount = 0
        
        for (x in 0 until width step 2) {
            for (y in 0 until height step 2) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                // Luminance formula
                val brightness = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                totalBrightness += brightness
                pixelCount++
            }
        }
        
        return if (pixelCount > 0) {
            (totalBrightness / pixelCount).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /**
     * Check if recording permissions are available
     */
    fun hasRecordingPermission(context: Context): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Get max recording duration in milliseconds
     */
    const val MAX_RECORDING_DURATION_MS = 300_000L // 5 minutes

    /**
     * Get min recording duration in milliseconds
     */
    const val MIN_RECORDING_DURATION_MS = 1_000L // 1 second
}
