package com.matrixmessenger.domain.repository

import android.net.Uri
import com.matrixmessenger.domain.model.MediaItem
import java.io.File

/**
 * Repository interface for media operations
 */
interface MediaRepository {
    /**
     * Upload media file to homeserver
     */
    suspend fun uploadMedia(
        file: File,
        mimeType: String,
        fileName: String? = null
    ): Result<String>
    
    /**
     * Download media from URL to file
     */
    suspend fun downloadMedia(url: String, destinationFile: File): Result<Unit>
    
    /**
     * Get cached media file if available
     */
    suspend fun getCachedMedia(url: String): File?
    
    /**
     * Get media thumbnail URL
     */
    suspend fun getThumbnailUrl(mediaUrl: String, width: Int, height: Int): String?
    
    /**
     * Clear media cache
     */
    fun clearCache()
    
    /**
     * Get cache size in bytes
     */
    suspend fun getCacheSize(): Long
    
    /**
     * Create local media item from URI
     */
    suspend fun createMediaItem(uri: Uri): Result<MediaItem>
    
    /**
     * Create local media item from file
     */
    suspend fun createMediaItem(file: File, mimeType: String): Result<MediaItem>
    
    /**
     * Resize image before upload
     */
    suspend fun resizeImage(file: File, maxWidth: Int, maxHeight: Int): Result<File>
    
    /**
     * Compress image before upload
     */
    suspend fun compressImage(file: File, targetSizeKB: Int): Result<File>
    
    /**
     * Generate thumbnail for video
     */
    suspend fun generateVideoThumbnail(videoFile: File): Result<File>
    
    /**
     * Get MIME type from file
     */
    fun getMimeType(file: File): String
}
