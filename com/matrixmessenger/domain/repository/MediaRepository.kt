package com.matrixmessenger.domain.repository

import java.io.File

interface MediaRepository {
    suspend fun uploadMedia(file: File, mimeType: String): Result<String>
    suspend fun downloadMedia(mediaUrl: String, destinationFile: File): Result<File>
    suspend fun getMediaThumbnail(mediaUrl: String, width: Int, height: Int): Result<String>
    suspend fun deleteMedia(localPath: String): Result<Unit>
}
