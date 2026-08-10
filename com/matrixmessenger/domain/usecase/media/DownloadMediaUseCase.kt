package com.matrixmessenger.domain.usecase.media

import com.matrixmessenger.domain.repository.MediaRepository
import java.io.File

class DownloadMediaUseCase(private val mediaRepository: MediaRepository) {
    suspend operator fun invoke(mediaUrl: String, destinationFile: File): Result<File> {
        return mediaRepository.downloadMedia(mediaUrl, destinationFile)
    }
}
