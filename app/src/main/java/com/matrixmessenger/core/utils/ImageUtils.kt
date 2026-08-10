package com.matrixmessenger.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Utility functions for image operations
 */

object ImageUtils {

    /**
     * Get image dimensions from URI
     */
    suspend fun getImageDimensions(context: Context, uri: Uri): Pair<Int, Int> {
        return try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .build()
            
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                Pair(result.drawable.intrinsicWidth, result.drawable.intrinsicHeight)
            } else {
                Pair(0, 0)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    /**
     * Get image dimensions from file path
     */
    fun getImageDimensions(filePath: String): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }

    /**
     * Resize image to max dimensions while maintaining aspect ratio
     */
    fun resizeImage(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        val scale = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )
        
        if (scale >= 1.0f) {
            return bitmap
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Compress image to target size in KB
     */
    fun compressImage(bitmap: Bitmap, targetSizeKB: Int): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        var quality = 100
        
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        
        while (outputStream.size() / 1024 > targetSizeKB && quality > 10) {
            outputStream.reset()
            quality -= 10
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        }
        
        return outputStream.toByteArray()
    }

    /**
     * Rotate bitmap according to EXIF orientation
     */
    fun rotateBitmap(bitmap: Bitmap, filePath: String): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                else -> return bitmap
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }

    /**
     * Create thumbnail from image URI
     */
    suspend fun createThumbnail(context: Context, uri: Uri, size: Int = 512): Bitmap? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(size)
                .build()
            
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save bitmap to file
     */
    fun saveBitmapToFile(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 90): Boolean {
        return try {
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(format, quality, outputStream)
                outputStream.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if URI is an image
     */
    fun isImageUri(context: Context, uri: Uri): Boolean {
        return try {
            val mimeType = context.contentResolver.getType(uri)
            mimeType?.startsWith("image/") == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get image MIME type from URI
     */
    fun getImageMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: "image/jpeg"
    }

    /**
     * Calculate sample size for loading bitmap efficiently
     */
    fun calculateSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var sampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                sampleSize *= 2
            }
        }
        
        return sampleSize
    }

    /**
     * Load bitmap with sample size for memory efficiency
     */
    fun loadBitmapWithSampleSize(filePath: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(filePath, options)
            
            options.inSampleSize = calculateSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            
            BitmapFactory.decodeFile(filePath, options)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Default max image dimension for uploads
     */
    const val MAX_IMAGE_DIMENSION = 1920

    /**
     * Default thumbnail size
     */
    const val THUMBNAIL_SIZE = 512

    /**
     * Default image quality for compression
     */
    const val DEFAULT_IMAGE_QUALITY = 85
}
