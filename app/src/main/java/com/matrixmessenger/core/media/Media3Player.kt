package com.matrixmessenger.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3Player @Inject constructor(
    @ApplicationContext private val context: Context
) : MediaPlayer {

    private val player = ExoPlayer.Builder(context).build()
    private val playbackStateFlow = MutableStateFlow(PlaybackState(false, 0, 0, false))
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var updateJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlaybackState()
                if (isPlaying) startTimer() else stopTimer()
            }

            override fun onPlaybackStateChanged(state: Int) {
                updatePlaybackState()
            }
        })
    }

    override fun play(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    override fun pause() {
        player.pause()
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    override fun observePlaybackState(): Flow<PlaybackState> = playbackStateFlow.asStateFlow()

    private fun updatePlaybackState() {
        playbackStateFlow.value = PlaybackState(
            isPlaying = player.isPlaying,
            currentPositionMs = player.currentPosition,
            durationMs = player.duration.coerceAtLeast(0),
            isBuffering = player.playbackState == Player.STATE_BUFFERING
        )
    }

    private fun startTimer() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (true) {
                updatePlaybackState()
                delay(500)
            }
        }
    }

    private fun stopTimer() {
        updateJob?.cancel()
    }
}
