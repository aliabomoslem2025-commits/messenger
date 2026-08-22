package com.matrixmessenger.data.repository

import android.net.Uri
import com.matrixmessenger.domain.model.MediaItem
import com.matrixmessenger.domain.repository.MediaRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor() : MediaRepository {
    override suspend fun uploadMedia(file: File, mimeType: String, fileName: String?): Result<String> = Result.failure(Exception("Not implemented"))
    override suspend fun downloadMedia(url: String, destinationFile: File): Result<Unit> = Result.failure(Exception("Not implemented"))
    override suspend fun getCachedMedia(url: String): File? = null
    override suspend fun getThumbnailUrl(mediaUrl: String, width: Int, height: Int): String? = null
    override fun clearCache() {}
    override suspend fun getCacheSize(): Long = 0L
    override suspend fun createMediaItem(uri: Uri): Result<MediaItem> = Result.failure(Exception("Not implemented"))
    override suspend fun createMediaItem(file: File, mimeType: String): Result<MediaItem> = Result.failure(Exception("Not implemented"))
    override suspend fun resizeImage(file: File, maxWidth: Int, maxHeight: Int): Result<File> = Result.failure(Exception("Not implemented"))
    override suspend fun compressImage(file: File, targetSizeKB: Int): Result<File> = Result.failure(Exception("Not implemented"))
    override suspend fun generateVideoThumbnail(videoFile: File): Result<File> = Result.failure(Exception("Not implemented"))
    override fun getMimeType(file: File): String = "application/octet-stream"
}
