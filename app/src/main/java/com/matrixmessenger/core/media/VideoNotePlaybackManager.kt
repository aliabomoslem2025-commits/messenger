package com.matrixmessenger.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoNotePlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _currentMessageId = MutableStateFlow<String?>(null)
    val currentMessageId: StateFlow<String?> = _currentMessageId.asStateFlow()
    
    private val playbackPositions = mutableMapOf<String, Long>()
    
    private val _playbackState = MutableStateFlow<PlaybackInfo>(PlaybackInfo.Idle)
    
    data class PlaybackInfo(
        val isPlaying: Boolean = false,
        val currentPositionMs: Long = 0,
        val durationMs: Long = 0,
        val messageId: String? = null
    ) {
        companion object {
            val Idle = PlaybackInfo()
        }
    }

    fun play(messageId: String, uri: Uri) {
        if (_currentMessageId.value == messageId && exoPlayer?.isPlaying == true) {
            pause()
            return
        }

        stop()
        _currentMessageId.value = messageId
        
        val player = getOrInitPlayer()
        val savedPosition = playbackPositions[messageId] ?: 0L
        
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(savedPosition)
        player.play()
        
        Timber.d("VIDEO_NOTE_PLAYBACK: Playing $messageId from $savedPosition ms")
    }

    fun pause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                val msgId = _currentMessageId.value
                if (msgId != null) {
                    playbackPositions[msgId] = it.currentPosition
                }
                it.pause()
                updateState()
            }
        }
    }

    fun stop() {
        exoPlayer?.let {
            val msgId = _currentMessageId.value
            if (msgId != null) {
                playbackPositions[msgId] = it.currentPosition
            }
            it.stop()
            it.clearMediaItems()
        }
        _currentMessageId.value = null
        updateState()
    }

    fun getPlaybackState(messageId: String): Flow<PlaybackInfo> {
        return _playbackState.map { 
            if (it.messageId == messageId) it else PlaybackInfo.Idle.copy(
                currentPositionMs = playbackPositions[messageId] ?: 0L
            )
        }
    }

    private fun getOrInitPlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    updateState()
                }
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    updateState()
                }
            })
            exoPlayer = this
            startProgressTracker()
        }
    }

    private fun startProgressTracker() {
        scope.launch {
            while (isActive) {
                if (exoPlayer?.isPlaying == true) {
                    updateState()
                }
                delay(16)
            }
        }
    }

    private fun updateState() {
        val player = exoPlayer ?: return
        _playbackState.value = PlaybackInfo(
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0),
            messageId = _currentMessageId.value
        )
    }

    fun release() {
        scope.cancel()
        exoPlayer?.release()
        exoPlayer = null
    }

    fun attachTo(playerView: PlayerView, messageId: String) {
        if (_currentMessageId.value == messageId) {
            playerView.player = getOrInitPlayer()
        } else {
            playerView.player = null
        }
    }
}
