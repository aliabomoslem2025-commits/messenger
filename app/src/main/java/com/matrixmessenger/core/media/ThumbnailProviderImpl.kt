package com.matrixmessenger.core.media

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThumbnailProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ThumbnailProvider {

    override suspend fun getThumbnail(file: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val thumbnailFile = File(context.cacheDir, "thumb_${file.name}.jpg")
            if (thumbnailFile.exists()) return@runCatching thumbnailFile

            val bitmap = if (file.extension.lowercase() in listOf("mp4", "mkv", "mov")) {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
            } else {
                // For images, we can just load and scale or use ThumbnailUtils if available
                @Suppress("DEPRECATION")
                ThumbnailUtils.extractThumbnail(android.graphics.BitmapFactory.decodeFile(file.absolutePath), 512, 512)
            }

            if (bitmap == null) throw Exception("Failed to generate thumbnail")

            FileOutputStream(thumbnailFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            thumbnailFile
        }
    }
}
