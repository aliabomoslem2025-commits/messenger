package com.matrixmessenger.core.media

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * The central orchestrator for all media-related tasks.
 */
interface MediaEngine {
    val loader: MediaLoader
    val cache: MediaCache
    val player: MediaPlayer
    val uploader: MediaUploader
    val thumbnailProvider: ThumbnailProvider
}

interface MediaLoader {
    /**
     * Resolves an MXC URL to a local file, downloading it if necessary.
     */
    suspend fun loadMedia(mxcUrl: String, isThumbnail: Boolean = false): Result<File>
    
    /**
     * Observes the loading progress of a media item.
     */
    fun observeProgress(mxcUrl: String): Flow<Float>
}

interface MediaCache {
    /**
     * Gets a cached file for a given MXC URL if it exists.
     */
    fun getCachedFile(mxcUrl: String): File?
    
    /**
     * Clears the media cache.
     */
    fun clear()
    
    /**
     * Gets the total size of the cache in bytes.
     */
    fun getCacheSize(): Long
}

interface MediaPlayer {
    /**
     * Prepares and starts playback of a media file.
     */
    fun play(uri: Uri)
    
    /**
     * Pauses playback.
     */
    fun pause()
    
    /**
     * Stops playback and releases resources.
     */
    fun stop()
    
    /**
     * Seeks to a specific position in milliseconds.
     */
    fun seekTo(positionMs: Long)
    
    /**
     * Observes the current playback state.
     */
    fun observePlaybackState(): Flow<PlaybackState>
}

data class PlaybackState(
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val isBuffering: Boolean
)

interface MediaUploader {
    /**
     * Uploads a local file to the Matrix homeserver.
     */
    suspend fun uploadMedia(file: File, mimeType: String): Result<String>
}

interface ThumbnailProvider {
    /**
     * Generates or extracts a thumbnail for a given media file.
     */
    suspend fun getThumbnail(file: File): Result<File>
}
