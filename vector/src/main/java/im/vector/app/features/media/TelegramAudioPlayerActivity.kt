/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.databinding.ActivityTelegramAudioPlayerBinding
import im.vector.app.features.home.room.detail.composer.AudioPlaybackService
import im.vector.app.features.home.room.detail.timeline.helper.AudioMessagePlaybackTracker
import im.vector.app.features.themes.ThemeUtils
import im.vector.lib.strings.CommonStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.room.getTimelineEvent
import org.matrix.android.sdk.api.session.room.model.message.MessageAudioContent
import java.io.File
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Expanded single-track player for Matrix m.audio events.
 *
 * The decoder and foreground MediaSession remain owned by AudioPlaybackService, so
 * leaving this screen never stops playback. Embedded audio artwork is used when present.
 */
@AndroidEntryPoint
class TelegramAudioPlayerActivity : AppCompatActivity() {

    @Inject lateinit var activeSessionHolder: ActiveSessionHolder
    @Inject lateinit var playbackTracker: AudioMessagePlaybackTracker

    private lateinit var views: ActivityTelegramAudioPlayerBinding
    private var roomId: String? = null
    private var eventId: String? = null
    private var audioFile: File? = null
    private var title: String = ""
    private var durationMs: Int = 0
    private var isUserSeeking = false
    private var queueEventIds: List<String> = emptyList()
    private var queueIndex = -1
    private var shuffleEnabled = false
    private var repeatMode = 0
    private var trackReceiverRegistered = false

    private val trackChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val changedEventId = intent.getStringExtra("track_id") ?: return
            if (changedEventId == eventId) return
            eventId?.let(playbackTracker::untrack)
            eventId = changedEventId
            queueIndex = queueEventIds.indexOf(changedEventId)
            playbackTracker.track(changedEventId) { state ->
                renderPlaybackState(state)
            }
            renderQueueControls()
            loadAudio(targetEventId = changedEventId, initializeQueue = false)
        }
    }

    private val speeds = floatArrayOf(1f, 1.25f, 1.5f, 2f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        views = ActivityTelegramAudioPlayerBinding.inflate(layoutInflater)
        setContentView(views.root)

        roomId = intent.getStringExtra(EXTRA_ROOM_ID)
        eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (roomId.isNullOrBlank() || eventId.isNullOrBlank()) {
            finish()
            return
        }

        views.audioPlayerCloseButton.setOnClickListener { finish() }
        views.audioPlayerPlayPauseButton.setOnClickListener { togglePlayback() }
        views.audioPlayerRewindButton.setOnClickListener { seekRelative(-15_000) }
        views.audioPlayerForwardButton.setOnClickListener { seekRelative(15_000) }
        views.audioPlayerSpeedButton.setOnClickListener { cyclePlaybackSpeed() }
        views.audioPlayerPreviousButton.setOnClickListener { AudioPlaybackService.skipToPrevious(this) }
        views.audioPlayerNextButton.setOnClickListener { AudioPlaybackService.skipToNext(this) }
        views.audioPlayerShuffleButton.setOnClickListener {
            shuffleEnabled = !shuffleEnabled
            AudioPlaybackService.setShuffleEnabled(this, shuffleEnabled)
            renderQueueControls()
        }
        views.audioPlayerRepeatButton.setOnClickListener {
            repeatMode = (repeatMode + 1) % 3
            AudioPlaybackService.setRepeatMode(this, repeatMode)
            renderQueueControls()
        }

        ContextCompat.registerReceiver(
                this,
                trackChangedReceiver,
                IntentFilter(AudioPlaybackService.ACTION_TRACK_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
        )
        trackReceiverRegistered = true

        views.audioPlayerSeekBar.max = 1_000
        views.audioPlayerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && durationMs > 0) {
                    views.audioPlayerCurrentTime.text = formatTime(durationMs * progress / 1_000)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                val position = durationMs * (seekBar?.progress ?: 0) / 1_000
                eventId?.let { id ->
                    val wasPlaying = playbackTracker.getPlaybackState(id) is AudioMessagePlaybackTracker.Listener.State.Playing ||
                            AudioPlaybackService.isPlayingFor(this@TelegramAudioPlayerActivity, id)
                    AudioPlaybackService.seek(this@TelegramAudioPlayerActivity, position)
                    val percentage = if (durationMs > 0) position.toFloat() / durationMs else 0f
                    if (wasPlaying) {
                        playbackTracker.updatePlayingAtPlaybackTime(id, position, percentage)
                    } else {
                        playbackTracker.updatePausedAtPlaybackTime(id, position, percentage)
                    }
                }
            }
        })

        eventId?.let { id ->
            playbackTracker.track(id) { state ->
                renderPlaybackState(state)
            }
        }
        updateSpeedLabel()
        shuffleEnabled = AudioPlaybackService.isShuffleEnabled(this)
        repeatMode = AudioPlaybackService.repeatMode(this)
        loadAudio(initializeQueue = true)
    }

    private fun loadAudio(
            targetEventId: String = eventId ?: return,
            initializeQueue: Boolean = false
    ) {
        val targetRoomId = roomId ?: return
        setLoading(true, getString(R.string.telegram_audio_player_loading))

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val session = activeSessionHolder.getSafeActiveSession()
                            ?: throw IllegalStateException("No active Matrix session")
                    val room = session.getRoom(targetRoomId)
                            ?: throw IllegalStateException("The room is no longer available")
                    val event = room.getTimelineEvent(targetEventId)
                            ?: throw IllegalStateException("The audio event is no longer available")
                    val content = event.root.getClearContent().toModel<MessageAudioContent>()
                            ?: throw IllegalStateException("The event is not an audio message")
                    val audioQueue = if (initializeQueue) {
                        room.timelineService().getAudioMessages().map { it.eventId }.distinct()
                    } else {
                        emptyList()
                    }
                    val normalizedQueue = if (initializeQueue && targetEventId !in audioQueue) {
                        audioQueue + targetEventId
                    } else {
                        audioQueue
                    }
                    val file = session.fileService().downloadFile(content)
                    // Metadata extraction is optional. Some codecs/containers can be played
                    // by MediaPlayer but are not fully understood by MediaMetadataRetriever.
                    val metadata = runCatching { readMetadata(file) }.getOrElse {
                        AudioMetadata(null, null, null, 0, null)
                    }

                    LoadedAudio(
                            file = file,
                            title = metadata.title?.takeIf { it.isNotBlank() } ?: content.body,
                            artist = metadata.artist?.takeIf { it.isNotBlank() },
                            album = metadata.album?.takeIf { it.isNotBlank() },
                            duration = metadata.durationMs.takeIf { it > 0 }
                                    ?: content.audioInfo?.duration
                                    ?: 0,
                            artwork = metadata.artwork,
                            queueEventIds = normalizedQueue
                    )
                }
            }

            result.fold(
                    onSuccess = { loaded ->
                        audioFile = loaded.file
                        title = loaded.title
                        durationMs = loaded.duration.coerceAtLeast(0)
                        if (initializeQueue) {
                            queueEventIds = loaded.queueEventIds
                            queueIndex = queueEventIds.indexOf(targetEventId)
                            AudioPlaybackService.setQueue(
                                    this@TelegramAudioPlayerActivity,
                                    targetRoomId,
                                    queueEventIds,
                                    targetEventId
                            )
                            shuffleEnabled = AudioPlaybackService.isShuffleEnabled(this@TelegramAudioPlayerActivity)
                            repeatMode = AudioPlaybackService.repeatMode(this@TelegramAudioPlayerActivity)
                            renderQueueControls()
                        } else if (queueIndex < 0) {
                            queueIndex = queueEventIds.indexOf(targetEventId)
                            renderQueueControls()
                        }
                        views.audioPlayerTitle.text = loaded.title
                        views.audioPlayerArtist.text = listOfNotNull(
                                loaded.artist,
                                loaded.album
                        ).joinToString(" • ").ifBlank {
                            getString(R.string.telegram_audio_player_artist_fallback)
                        }
                        if (loaded.artwork != null) {
                            BitmapFactory.decodeByteArray(loaded.artwork, 0, loaded.artwork.size)?.let { bitmap ->
                                views.audioPlayerCoverImage.setImageBitmap(bitmap)
                                views.audioPlayerCoverImage.isVisible = true
                                views.audioPlayerCoverFallback.isVisible = false
                            }
                        }
                        views.audioPlayerDuration.text = formatTime(durationMs)
                        views.audioPlayerSeekBar.isEnabled = durationMs > 0
                        setLoading(false)
                        updateSpeedLabel()
                        renderCurrentPlaybackState()
                    },
                    onFailure = { failure ->
                        setLoading(false)
                        views.audioPlayerStatus.isVisible = true
                        views.audioPlayerStatus.text = getString(R.string.telegram_audio_player_unavailable)
                        Toast.makeText(
                                this@TelegramAudioPlayerActivity,
                                failure.localizedMessage ?: getString(R.string.telegram_audio_player_unavailable),
                                Toast.LENGTH_LONG
                        ).show()
                    }
            )
        }
    }

    private fun togglePlayback() {
        val id = eventId ?: return
        val file = audioFile ?: return
        val state = playbackTracker.getPlaybackState(id)
        val isPlaying = state is AudioMessagePlaybackTracker.Listener.State.Playing ||
                AudioPlaybackService.isPlayingFor(this, id)
        if (isPlaying) {
            AudioPlaybackService.pause(this)
            playbackTracker.pausePlayback(id)
        } else {
            val resumeAt = playbackTracker.getPlaybackTime(id)
                    ?: AudioPlaybackService.persistedPosition(this, id)
                    ?: 0
            AudioPlaybackService.play(this, id, file, resumeAt, title)
        }
    }

    private fun seekRelative(deltaMs: Int) {
        val id = eventId ?: return
        val current = playbackTracker.getPlaybackTime(id)
                ?: AudioPlaybackService.persistedPosition(this, id)
                ?: 0
        val wasPlaying = playbackTracker.getPlaybackState(id) is AudioMessagePlaybackTracker.Listener.State.Playing ||
                AudioPlaybackService.isPlayingFor(this, id)
        val target = (current + deltaMs).coerceIn(0, (durationMs - 1).coerceAtLeast(0))
        AudioPlaybackService.seek(this, target)
        val percentage = if (durationMs > 0) target.toFloat() / durationMs else 0f
        if (wasPlaying) {
            playbackTracker.updatePlayingAtPlaybackTime(id, target, percentage)
        } else {
            playbackTracker.updatePausedAtPlaybackTime(id, target, percentage)
        }
    }

    private fun cyclePlaybackSpeed() {
        val currentSpeed = AudioPlaybackService.currentPlaybackSpeed(this, eventId)
        val currentIndex = speeds.indexOfFirst { abs(it - currentSpeed) < 0.01f }
                .let { if (it < 0) 0 else it }
        val nextSpeed = speeds[(currentIndex + 1) % speeds.size]
        AudioPlaybackService.setPlaybackSpeed(this, nextSpeed)
        updateSpeedLabel(nextSpeed)
    }

    private fun renderQueueControls() {
        val hasQueue = queueEventIds.size > 1
        views.audioPlayerPreviousButton.isEnabled = hasQueue && (shuffleEnabled || queueIndex > 0 || repeatMode == 1)
        views.audioPlayerNextButton.isEnabled = hasQueue &&
                (shuffleEnabled || queueIndex in 0 until queueEventIds.lastIndex || repeatMode == 1)
        views.audioPlayerShuffleButton.isEnabled = hasQueue
        views.audioPlayerRepeatButton.isEnabled = hasQueue
        val activeTint = ThemeUtils.getColor(this, im.vector.lib.ui.styles.R.attr.vctr_telegram_send_accent)
        val idleTint = ThemeUtils.getColor(this, im.vector.lib.ui.styles.R.attr.vctr_content_secondary)
        views.audioPlayerShuffleButton.imageTintList = android.content.res.ColorStateList.valueOf(
                if (shuffleEnabled) activeTint else idleTint
        )
        views.audioPlayerRepeatButton.imageTintList = android.content.res.ColorStateList.valueOf(
                if (repeatMode == 0) idleTint else activeTint
        )
        views.audioPlayerRepeatButton.contentDescription = getString(
                when (repeatMode) {
                    1 -> R.string.telegram_audio_repeat_all
                    2 -> R.string.telegram_audio_repeat_one
                    else -> R.string.telegram_audio_repeat_off
                }
        )
    }

    private fun updateSpeedLabel(speed: Float = AudioPlaybackService.currentPlaybackSpeed(this, eventId)) {
        val text = if (speed % 1f == 0f) {
            "${speed.toInt()}×"
        } else {
            "${speed}×"
        }
        views.audioPlayerSpeedButton.text = text
        views.audioPlayerSpeedButton.contentDescription =
                getString(R.string.telegram_audio_player_speed) + " " + text
    }

    private fun renderCurrentPlaybackState() {
        val id = eventId ?: return
        when (val state = playbackTracker.getPlaybackState(id)) {
            is AudioMessagePlaybackTracker.Listener.State.Playing -> renderPlaybackState(state)
            is AudioMessagePlaybackTracker.Listener.State.Paused -> renderPlaybackState(state)
            else -> {
                val position = AudioPlaybackService.persistedPosition(this, id) ?: 0
                renderProgress(position, false)
            }
        }
    }

    private fun renderPlaybackState(state: AudioMessagePlaybackTracker.Listener.State) {
        when (state) {
            is AudioMessagePlaybackTracker.Listener.State.Playing -> {
                views.audioPlayerPlayPauseButton.setImageResource(R.drawable.ic_play_pause_pause)
                views.audioPlayerPlayPauseButton.contentDescription = getString(CommonStrings.a11y_pause_audio_message, title)
                renderProgress(state.playbackTime, true, state.percentage)
            }
            is AudioMessagePlaybackTracker.Listener.State.Paused -> {
                views.audioPlayerPlayPauseButton.setImageResource(R.drawable.ic_play_pause_play)
                views.audioPlayerPlayPauseButton.contentDescription = getString(CommonStrings.a11y_play_audio_message, title)
                renderProgress(state.playbackTime, false, state.percentage)
            }
            is AudioMessagePlaybackTracker.Listener.State.Error -> {
                views.audioPlayerPlayPauseButton.setImageResource(R.drawable.ic_play_pause_play)
                views.audioPlayerStatus.isVisible = true
                views.audioPlayerStatus.text = state.failure.localizedMessage
                        ?: getString(R.string.telegram_audio_player_unavailable)
            }
            AudioMessagePlaybackTracker.Listener.State.Idle -> {
                views.audioPlayerPlayPauseButton.setImageResource(R.drawable.ic_play_pause_play)
                renderProgress(0, false)
            }
            is AudioMessagePlaybackTracker.Listener.State.Recording -> Unit
        }
    }

    private fun renderProgress(positionMs: Int, playing: Boolean, percentage: Float? = null) {
        if (isUserSeeking) return
        val duration = durationMs.coerceAtLeast(0)
        val position = positionMs.coerceIn(0, duration)
        views.audioPlayerCurrentTime.text = formatTime(position)
        if (duration > 0) {
            val value = ((percentage ?: position.toFloat() / duration) * 1_000)
                    .roundToInt().coerceIn(0, 1_000)
            views.audioPlayerSeekBar.progress = value
        } else {
            views.audioPlayerSeekBar.progress = 0
        }
        if (playing) {
            views.audioPlayerStatus.isVisible = false
        }
    }

    private fun setLoading(loading: Boolean, status: String? = null) {
        views.audioPlayerLoadingProgress.isVisible = loading
        views.audioPlayerStatus.isVisible = loading && !status.isNullOrBlank()
        if (!status.isNullOrBlank()) {
            views.audioPlayerStatus.text = status
        }
        views.audioPlayerPlayPauseButton.isEnabled = !loading || audioFile != null
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds.coerceAtLeast(0) / 1_000).toLong()
        val hours = seconds / 3_600
        val minutes = seconds / 60 % 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
        } else {
            "%d:%02d".format(seconds / 60, remainingSeconds)
        }
    }

    private fun readMetadata(file: File): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            AudioMetadata(
                    title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0,
                    artwork = retriever.embeddedPicture
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun onDestroy() {
        eventId?.let(playbackTracker::untrack)
        if (trackReceiverRegistered) {
            unregisterReceiver(trackChangedReceiver)
            trackReceiverRegistered = false
        }
        super.onDestroy()
    }

    private data class AudioMetadata(
            val title: String?,
            val artist: String?,
            val album: String?,
            val durationMs: Int,
            val artwork: ByteArray?
    )

    private data class LoadedAudio(
            val file: File,
            val title: String,
            val artist: String?,
            val album: String?,
            val duration: Int,
            val artwork: ByteArray?,
            val queueEventIds: List<String>
    )

    companion object {
        private const val EXTRA_ROOM_ID = "audio_player_room_id"
        private const val EXTRA_EVENT_ID = "audio_player_event_id"

        fun newIntent(context: Context, roomId: String, eventId: String): Intent =
                Intent(context, TelegramAudioPlayerActivity::class.java)
                        .putExtra(EXTRA_ROOM_ID, roomId)
                        .putExtra(EXTRA_EVENT_ID, eventId)
    }
}
