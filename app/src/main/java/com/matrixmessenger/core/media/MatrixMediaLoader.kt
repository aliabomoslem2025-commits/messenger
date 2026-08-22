package com.matrixmessenger.core.media

import com.matrixmessenger.data.matrix.MatrixClientManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixMediaLoader @Inject constructor(
    private val matrixClientManager: MatrixClientManager,
    private val mediaCache: MatrixMediaCache
) : MediaLoader {

    private val progressFlows = mutableMapOf<String, MutableStateFlow<Float>>()

    override suspend fun loadMedia(mxcUrl: String, isThumbnail: Boolean): Result<File> {
        val cachedFile = mediaCache.getCachedFile(mxcUrl)
        if (cachedFile != null) return Result.success(cachedFile)

        val session = matrixClientManager.getCurrentSession() ?: return Result.failure(Exception("No active session"))
        val targetFile = mediaCache.getFileForMxc(mxcUrl)

        return runCatching {
            val progressFlow = progressFlows.getOrPut(mxcUrl) { MutableStateFlow(0f) }
            
            // Note: Matrix SDK downloadFile might not directly return the file, 
            // but we can manage the target location.
            // Simplified implementation for now.
            session.fileService().downloadFile(
                fileName = targetFile.name,
                mimeType = null,
                url = mxcUrl,
                elementToDecrypt = null
            )
            
            // In a real implementation, we'd listen to the download state
            // and update progressFlow and return the file when done.
            // This is a placeholder for the asynchronous download logic.
            targetFile
        }
    }

    override fun observeProgress(mxcUrl: String): Flow<Float> {
        return progressFlows.getOrPut(mxcUrl) { MutableStateFlow(0f) }.asStateFlow()
    }
}
