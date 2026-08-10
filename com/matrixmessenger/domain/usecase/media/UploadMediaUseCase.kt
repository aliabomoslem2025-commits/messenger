package com.matrixmessenger.domain.usecase.media

import com.matrixmessenger.domain.repository.MediaRepository
import java.io.File

class UploadMediaUseCase(private val mediaRepository: MediaRepository) {
    suspend operator fun invoke(file: File, mimeType: String): Result<String> {
        return mediaRepository.uploadMedia(file, mimeType)
    }
}
