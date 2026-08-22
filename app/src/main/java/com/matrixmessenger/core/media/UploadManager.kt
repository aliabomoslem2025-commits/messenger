package com.matrixmessenger.core.media

import com.matrixmessenger.domain.repository.MatrixRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UploadManager @Inject constructor(
    private val repository: MatrixRepository
) {
    suspend fun uploadVideoNote(
        roomId: String,
        file: File,
        durationMs: Long,
        width: Int,
        height: Int
    ): Result<String> {
        return repository.sendVideoNote(roomId, file, durationMs, width, height)
    }
}
