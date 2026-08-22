package com.matrixmessenger.core.media

import com.matrixmessenger.data.matrix.MatrixClientManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixMediaEngine @Inject constructor(
    override val loader: MatrixMediaLoader,
    override val cache: MatrixMediaCache,
    override val player: Media3Player,
    override val thumbnailProvider: ThumbnailProviderImpl,
    private val matrixClientManager: MatrixClientManager
) : MediaEngine {
    
    override val uploader: MediaUploader = object : MediaUploader {
        override suspend fun uploadMedia(file: java.io.File, mimeType: String): Result<String> {
            val hasSession = matrixClientManager.getCurrentSession() != null
            if (!hasSession) return Result.failure(Exception("No active session"))
            
            return runCatching {
                throw Exception("Standalone uploader not available; use room.sendService() or profile.updateAvatar()")
            }
        }
    }
}
