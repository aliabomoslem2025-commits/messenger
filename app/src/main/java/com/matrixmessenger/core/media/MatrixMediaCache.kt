package com.matrixmessenger.core.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MatrixMediaCache @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaCache {

    private val cacheDir = File(context.cacheDir, "matrix_media_cache").apply {
        if (!exists()) mkdirs()
    }

    override fun getCachedFile(mxcUrl: String): File? {
        val fileName = hashMxcUrl(mxcUrl)
        val file = File(cacheDir, fileName)
        return if (file.exists()) file else null
    }

    override fun clear() {
        cacheDir.deleteRecursively()
        cacheDir.mkdirs()
    }

    override fun getCacheSize(): Long {
        return getDirSize(cacheDir)
    }

    private fun hashMxcUrl(mxcUrl: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(mxcUrl.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getDirSize(dir: File): Long {
        var size: Long = 0
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }
    
    fun getFileForMxc(mxcUrl: String): File {
        return File(cacheDir, hashMxcUrl(mxcUrl))
    }
}
