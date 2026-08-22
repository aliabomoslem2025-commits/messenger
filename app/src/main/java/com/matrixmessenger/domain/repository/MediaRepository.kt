package com.matrixmessenger.domain.repository

import android.net.Uri
import com.matrixmessenger.domain.model.MediaItem
import java.io.File

interface MediaRepository {
    suspend fun uploadMedia(file: File, mimeType: String, fileName: String? = null): Result<String>
    suspend fun downloadMedia(url: String, destinationFile: File): Result<Unit>
    suspend fun getCachedMedia(url: String): File?
    suspend fun getThumbnailUrl(mediaUrl: String, width: Int, height: Int): String?
    fun clearCache()
    suspend fun getCacheSize(): Long
    suspend fun createMediaItem(uri: Uri): Result<MediaItem>
    suspend fun createMediaItem(file: File, mimeType: String): Result<MediaItem>
    suspend fun resizeImage(file: File, maxWidth: Int, maxHeight: Int): Result<File>
    suspend fun compressImage(file: File, targetSizeKB: Int): Result<File>
    suspend fun generateVideoThumbnail(videoFile: File): Result<File>
    fun getMimeType(file: File): String
}
