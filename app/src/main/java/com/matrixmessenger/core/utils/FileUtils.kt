package com.matrixmessenger.core.utils

import android.content.Context
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Utility functions for file operations
 */

object FileUtils {

    /**
     * Get file extension from filename
     */
    fun getFileExtension(filename: String): String {
        return filename.substringAfterLast('.', "").lowercase()
    }

    /**
     * Get MIME type from file extension
     */
    fun getMimeTypeFromExtension(extension: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
    }

    /**
     * Get MIME type from file path
     */
    fun getMimeType(filePath: String): String {
        val extension = getFileExtension(filePath)
        return getMimeTypeFromExtension(extension)
    }

    /**
     * Get file size in bytes
     */
    fun getFileSize(file: File): Long {
        return if (file.exists()) {
            file.length()
        } else {
            0L
        }
    }

    /**
     * Get formatted file size string
     */
    fun getFormattedFileSize(context: Context, file: File): String {
        val size = getFileSize(file)
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }

    /**
     * Check if file is an image
     */
    fun isImageFile(filename: String): Boolean {
        val extension = getFileExtension(filename).lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic")
    }

    /**
     * Check if file is a video
     */
    fun isVideoFile(filename: String): Boolean {
        val extension = getFileExtension(filename).lowercase()
        return extension in listOf("mp4", "mkv", "avi", "webm", "mov", "3gp")
    }

    /**
     * Check if file is an audio file
     */
    fun isAudioFile(filename: String): Boolean {
        val extension = getFileExtension(filename).lowercase()
        return extension in listOf("mp3", "wav", "ogg", "flac", "aac", "m4a")
    }

    /**
     * Check if file is a document
     */
    fun isDocumentFile(filename: String): Boolean {
        val extension = getFileExtension(filename).lowercase()
        return extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf")
    }

    /**
     * Copy file from source to destination
     */
    @Throws(IOException::class)
    fun copyFile(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Create temporary file from URI
     */
    fun createTempFile(context: Context, uri: android.net.Uri): File? {
        return try {
            val tempFile = File.createTempFile(
                "temp_",
                ".${getFileExtension(uri.toString())}",
                context.cacheDir
            )
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete file safely
     */
    fun deleteFile(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.deleteRecursively()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get cache directory for Matrix media
     */
    fun getMatrixMediaCacheDir(context: Context): File {
        val cacheDir = File(context.cacheDir, "matrix_media")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir
    }

    /**
     * Clear Matrix media cache
     */
    fun clearMatrixMediaCache(context: Context): Boolean {
        return try {
            val cacheDir = getMatrixMediaCacheDir(context)
            cacheDir.deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Generate unique filename with timestamp
     */
    fun generateUniqueFilename(prefix: String = "", extension: String = ""): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = (1000..9999).random()
        return buildString {
            if (prefix.isNotEmpty()) {
                append("${prefix}_")
            }
            append("${timestamp}_${randomSuffix}")
            if (extension.isNotEmpty() && !extension.startsWith(".")) {
                append(".$extension")
            } else if (extension.isNotEmpty()) {
                append(extension)
            }
        }
    }
}
