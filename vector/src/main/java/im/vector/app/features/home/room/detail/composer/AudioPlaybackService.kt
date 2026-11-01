/*
 * Copyright 2026 The Messenger Contributors.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 */

package im.vector.app.features.home.room.detail.composer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import dagger.hilt.android.AndroidEntryPoint
import im.vector.app.R
import im.vector.app.core.di.ActiveSessionHolder
import im.vector.app.features.home.room.detail.timeline.helper.AudioMessagePlaybackTracker
import im.vector.app.features.session.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.matrix.android.sdk.api.session.events.model.toModel
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.room.model.message.MessageAudioContent
import java.io.File
import javax.inject.Inject
import kotlin.math.max

/**
 * Owns message-audio playback independently of the chat Fragment/ViewModel lifecycle.
 *
 * The persisted file path and playback position let Android recreate the service after
 * a process kill. A user force-stop is intentionally not worked around (Android stops
 * foreground services in that case).
 */
@AndroidEntryPoint
class AudioPlaybackService : Service() {

    @Inject lateinit var playbackTracker: AudioMessagePlaybackTracker
    @Inject lateinit var activeSessionHolder: ActiveSessionHolder

    private val handler = Handler(Looper.getMainLooper())
    private var queueRoomId: String? = null
    private var queueEventIds: MutableList<String> = mutableListOf()
    private var queueOriginalEventIds: MutableList<String> = mutableListOf()
    private var queueIndex = -1
    private var queueShuffleEnabled = false
    private var queueRepeatMode = REPEAT_OFF
    private var queueLoadingJob: Job? = null
    private var queueRequestGeneration = 0
    private var mediaPlayer: MediaPlayer? = null
    private var playbackId: String? = null
    private var playbackTitle: String = "Audio message"
    private var prepared = false
    private var pendingSeekMs = 0
    private var playbackSpeed = 1f
    private var pausedByAudioFocus = false
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private lateinit var mediaSession: MediaSessionCompat

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                val player = mediaPlayer
                if (player != null && prepared && pausedByAudioFocus) {
                    pausedByAudioFocus = false
                    runCatching {
                        player.setVolume(1f, 1f)
                        player.start()
                        val id = playbackId
                        if (id != null) persistPlayback(id, getPersistedPath(), player.currentPosition)
                        if (id != null) {
                            val duration = max(player.duration, 1)
                            playbackTracker.startPlayback(id)
                            updateSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, player.currentPosition, duration)
                            updateNotification(isPlaying = true)
                            handler.removeCallbacks(progressTicker)
                            handler.post(progressTicker)
                        }
                    }
                } else {
                    runCatching { player?.setVolume(1f, 1f) }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (mediaPlayer?.isPlaying == true) {
                    pausedByAudioFocus = true
                    pausePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                runCatching { mediaPlayer?.setVolume(0.2f, 0.2f) }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByAudioFocus = false
                stopPlaybackAndService()
            }
        }
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val player = mediaPlayer
            val id = playbackId
            if (player != null && prepared && id != null && player.isPlaying) {
                val position = player.currentPosition.coerceAtLeast(0)
                val duration = max(player.duration, 1)
                playbackTracker.updatePlayingAtPlaybackTime(id, position, position.toFloat() / duration)
                savePlaybackPosition(position)
                updateSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, position, duration)
                handler.postDelayed(this, PROGRESS_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        playbackSpeed = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getFloat(KEY_SPEED, 1f).coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        loadQueueFromPreferences()
        mediaSession = MediaSessionCompat(this, "MessengerMessageAudio").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = resumePersistedPlayback(forcePlay = true)
                override fun onPause() {
                    pausedByAudioFocus = false
                    pausePlayback()
                }
                override fun onStop() = stopPlaybackAndService()
                override fun onSeekTo(pos: Long) = seekTo(pos.toInt())
                override fun onSkipToNext() = playNextQueueTrack()
                override fun onSkipToPrevious() = playPreviousQueueTrack()
                override fun onSetShuffleMode(shuffleMode: Int) {
                    setShuffleModeInternal(shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP)
                }
                override fun onSetRepeatMode(repeatMode: Int) {
                    setRepeatModeInternal(
                            when (repeatMode) {
                                PlaybackStateCompat.REPEAT_MODE_ONE -> REPEAT_ONE
                                PlaybackStateCompat.REPEAT_MODE_ALL,
                                PlaybackStateCompat.REPEAT_MODE_GROUP -> REPEAT_ALL
                                else -> REPEAT_OFF
                            }
                    )
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val id = intent.getStringExtra(EXTRA_PLAYBACK_ID)
                val path = intent.getStringExtra(EXTRA_FILE_PATH)
                val startAt = intent.getIntExtra(EXTRA_START_POSITION, 0).coerceAtLeast(0)
                if (id.isNullOrBlank() || path.isNullOrBlank()) {
                    // The notification's Resume command intentionally carries no file path;
                    // it resumes from the persisted playback state.
                    resumePersistedPlayback(forcePlay = true)
                } else if (!File(path).isFile) {
                    stopPlaybackAndService()
                } else {
                    startForegroundCompat(buildNotification(isPlaying = true))
                    val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                            ?: File(path).nameWithoutExtension
                    startPlayback(id, path, startAt, title)
                }
            }
            ACTION_PAUSE -> {
                pausedByAudioFocus = false
                pausePlayback()
            }
            ACTION_SEEK -> seekTo(intent.getIntExtra(EXTRA_SEEK_POSITION, 0))
            ACTION_SET_SPEED -> setPlaybackSpeed(intent.getFloatExtra(EXTRA_SPEED, 1f))
            ACTION_RELOAD_QUEUE -> loadQueueFromPreferences()
            ACTION_NEXT -> playNextQueueTrack()
            ACTION_PREVIOUS -> playPreviousQueueTrack()
            ACTION_SET_SHUFFLE -> setShuffleModeInternal(intent.getBooleanExtra(EXTRA_SHUFFLE, false))
            ACTION_SET_REPEAT -> setRepeatModeInternal(intent.getIntExtra(EXTRA_REPEAT, REPEAT_OFF))
            ACTION_STOP -> stopPlaybackAndService()
            ACTION_STOP_VIDEO -> {
                val activeId = playbackId ?: getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                        .getString(KEY_ID, null)
                if (activeId?.startsWith(VIDEO_PLAYBACK_PREFIX) == true) {
                    stopPlaybackAndService()
                }
            }
            null -> resumePersistedPlayback(forcePlay = false)
            else -> Unit
        }
        return START_STICKY
    }

    private fun startPlayback(id: String, path: String, startAt: Int, title: String) {
        if (queueEventIds.isNotEmpty()) {
            val index = queueEventIds.indexOf(id)
            if (index >= 0) {
                queueIndex = index
                persistQueueState()
            } else if (!id.startsWith(VIDEO_PLAYBACK_PREFIX)) {
                clearQueueState()
            }
        }
        releasePlayer()
        pausedByAudioFocus = false
        playbackId = id
        playbackTitle = title.ifBlank { File(path).nameWithoutExtension }
        pendingSeekMs = startAt.coerceAtLeast(0)
        prepared = false
        persistPlayback(id, path, pendingSeekMs)

        try {
            val next = MediaPlayer()
            mediaPlayer = next
            next.setAudioAttributes(
                    AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            )
            next.setOnPreparedListener { preparedPlayer ->
                if (mediaPlayer !== preparedPlayer) return@setOnPreparedListener
                prepared = true
                if (!requestAudioFocus()) {
                    stopPlaybackAndService()
                    return@setOnPreparedListener
                }
                val duration = max(preparedPlayer.duration, 1)
                val seekPosition = pendingSeekMs.coerceAtMost(duration - 1)
                if (seekPosition > 0) preparedPlayer.seekTo(seekPosition)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    runCatching {
                        preparedPlayer.playbackParams = preparedPlayer.playbackParams.setSpeed(playbackSpeed)
                    }
                }
                preparedPlayer.start()
                persistPlayback(id, path, seekPosition)
                playbackTracker.startPlayback(id)
                updateSessionMetadata(id, duration)
                updateSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, seekPosition, duration)
                updateNotification(isPlaying = true)
                handler.removeCallbacks(progressTicker)
                handler.post(progressTicker)
            }
            next.setOnCompletionListener {
                playbackTracker.stopPlaybackOrRecorder(id)
                when {
                    id.startsWith(VIDEO_PLAYBACK_PREFIX) -> stopPlaybackAndService()
                    queueRepeatMode == REPEAT_ONE && queueIndex >= 0 -> {
                        startPlayback(id, path, 0, playbackTitle)
                    }
                    !playNextQueueTrack() -> stopPlaybackAndService()
                }
            }
            next.setOnErrorListener { _, what, extra ->
                playbackTracker.onError(id, IllegalStateException("Audio playback failed ($what/$extra)"))
                stopPlaybackAndService()
                true
            }
            next.setDataSource(path)
            next.prepareAsync()
            updateSessionMetadata(id, 0)
        } catch (failure: Throwable) {
            playbackTracker.onError(id, failure)
            stopPlaybackAndService()
        }
    }

    private fun loadQueueFromPreferences() {
        val preferences = getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE)
        queueRoomId = preferences.getString(KEY_QUEUE_ROOM_ID, null)
        queueEventIds = parseIds(preferences.getString(KEY_QUEUE_IDS, null))
        queueOriginalEventIds = parseIds(preferences.getString(KEY_QUEUE_ORIGINAL_IDS, null))
        if (queueOriginalEventIds.isEmpty()) queueOriginalEventIds = queueEventIds.toMutableList()
        queueIndex = preferences.getInt(KEY_QUEUE_INDEX, -1)
                .coerceIn(-1, (queueEventIds.size - 1).coerceAtLeast(-1))
        queueShuffleEnabled = preferences.getBoolean(KEY_QUEUE_SHUFFLE, false)
        queueRepeatMode = preferences.getInt(KEY_QUEUE_REPEAT, REPEAT_OFF).coerceIn(REPEAT_OFF, REPEAT_ONE)
    }

    private fun persistQueueState() {
        getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_QUEUE_ROOM_ID, queueRoomId)
                .putString(KEY_QUEUE_IDS, JSONArray(queueEventIds).toString())
                .putString(KEY_QUEUE_ORIGINAL_IDS, JSONArray(queueOriginalEventIds).toString())
                .putInt(KEY_QUEUE_INDEX, queueIndex)
                .putBoolean(KEY_QUEUE_SHUFFLE, queueShuffleEnabled)
                .putInt(KEY_QUEUE_REPEAT, queueRepeatMode)
                .apply()
    }

    private fun clearQueueState() {
        queueLoadingJob?.cancel()
        queueLoadingJob = null
        queueRequestGeneration++
        queueRoomId = null
        queueEventIds.clear()
        queueOriginalEventIds.clear()
        queueIndex = -1
        queueShuffleEnabled = false
        queueRepeatMode = REPEAT_OFF
        getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun setShuffleModeInternal(enabled: Boolean) {
        if (queueEventIds.size < 2) {
            queueShuffleEnabled = false
            return
        }
        if (queueShuffleEnabled == enabled) return

        val currentId = playbackId ?: queueEventIds.getOrNull(queueIndex)
        val original = queueOriginalEventIds.ifEmpty { queueEventIds }
        queueEventIds = if (enabled) {
            val remaining = original.filterNot { it == currentId }.shuffled()
            if (currentId == null) remaining.toMutableList() else (listOf(currentId) + remaining).toMutableList()
        } else {
            original.toMutableList()
        }
        queueIndex = currentId?.let(queueEventIds::indexOf) ?: 0
        queueShuffleEnabled = enabled
        persistQueueState()
        updateSessionPlaybackState(
                if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                mediaPlayer?.currentPosition ?: pendingSeekMs,
                mediaPlayer?.let { max(it.duration, 1) } ?: 0
        )
        updateNotification(isPlaying = mediaPlayer?.isPlaying == true)
    }

    private fun setRepeatModeInternal(mode: Int) {
        queueRepeatMode = mode.coerceIn(REPEAT_OFF, REPEAT_ONE)
        persistQueueState()
        updateSessionPlaybackState(
                if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                mediaPlayer?.currentPosition ?: pendingSeekMs,
                mediaPlayer?.let { max(it.duration, 1) } ?: 0
        )
        updateNotification(isPlaying = mediaPlayer?.isPlaying == true)
    }

    private fun nextQueueIndex(): Int? {
        if (queueEventIds.isEmpty() || queueIndex !in queueEventIds.indices) return null
        if (queueIndex < queueEventIds.lastIndex) return queueIndex + 1
        return if (queueRepeatMode == REPEAT_ALL) 0 else null
    }

    private fun previousQueueIndex(): Int? {
        if (queueEventIds.isEmpty() || queueIndex !in queueEventIds.indices) return null
        if (queueIndex > 0) return queueIndex - 1
        return if (queueRepeatMode == REPEAT_ALL) queueEventIds.lastIndex else null
    }

    private fun playNextQueueTrack(): Boolean {
        val nextIndex = nextQueueIndex() ?: return false
        playQueueTrack(nextIndex, queueEventIds.size)
        return true
    }

    private fun playPreviousQueueTrack(): Boolean {
        val previousIndex = previousQueueIndex() ?: return false
        val player = mediaPlayer
        if (player != null && prepared && player.currentPosition > PREVIOUS_RESTART_THRESHOLD_MS) {
            seekTo(0)
            return true
        }
        playQueueTrack(previousIndex, queueEventIds.size)
        return true
    }

    private fun playQueueTrack(index: Int, attemptsRemaining: Int) {
        val roomId = queueRoomId
        val eventId = queueEventIds.getOrNull(index)
        if (roomId.isNullOrBlank() || eventId.isNullOrBlank()) {
            stopPlaybackAndService()
            return
        }

        queueIndex = index
        persistQueueState()
        queueLoadingJob?.cancel()
        val generation = ++queueRequestGeneration
        handler.removeCallbacks(progressTicker)
        val previousId = playbackId
        releasePlayer()
        prepared = false
        playbackId = null
        previousId?.let(playbackTracker::stopPlaybackOrRecorder)
        playbackTitle = "Loading audio"
        updateNotification(isPlaying = false)

        val session = activeSessionHolder.getSafeActiveSession()
        if (session == null) {
            stopPlaybackAndService()
            return
        }

        queueLoadingJob = session.coroutineScope.launch {
            val result = runCatching {
                val room = session.getRoom(roomId)
                        ?: throw IllegalStateException("Audio playlist room is unavailable")
                val event = withContext(Dispatchers.IO) {
                    room.timelineService().getTimelineEvent(eventId)
                } ?: throw IllegalStateException("Audio playlist event is unavailable")
                val content = event.root.getClearContent().toModel<MessageAudioContent>()
                        ?: throw IllegalStateException("Playlist item is not an audio message")
                val file = session.fileService().downloadFile(content)
                file to content.body
            }

            if (generation != queueRequestGeneration) return@launch
            result.fold(
                    onSuccess = { (file, title) ->
                        startPlayback(eventId, file.absolutePath, 0, title)
                    },
                    onFailure = { failure ->
                        playbackTracker.onError(eventId, failure)
                        if (attemptsRemaining > 1 && playNextQueueTrack()) {
                            // Skip a broken/unavailable item and keep the queue moving.
                        } else {
                            stopPlaybackAndService()
                        }
                    }
            )
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                mediaPlayer?.takeIf { prepared }?.let { player ->
                    player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
                }
            }
        }
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putFloat(KEY_SPEED, playbackSpeed)
                .apply()
        val id = playbackId
        if (id != null && prepared) {
            persistPlayback(id, getPersistedPath(), pendingSeekMs)
            val duration = max(mediaPlayer?.duration ?: 1, 1)
            updateSessionPlaybackState(
                    if (mediaPlayer?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    mediaPlayer?.currentPosition ?: pendingSeekMs,
                    duration
            )
        }
    }

    private fun pausePlayback() {
        val player = mediaPlayer
        val id = playbackId
        if (player == null || !prepared || id == null) return
        runCatching {
            if (player.isPlaying) player.pause()
            pendingSeekMs = player.currentPosition.coerceAtLeast(0)
            persistPlayback(id, getPersistedPath(), pendingSeekMs)
            val duration = max(player.duration, 1)
            playbackTracker.updatePausedAtPlaybackTime(id, pendingSeekMs, pendingSeekMs.toFloat() / duration)
            updateSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED, pendingSeekMs, duration)
            updateNotification(isPlaying = false)
        }
        handler.removeCallbacks(progressTicker)
    }

    private fun seekTo(positionMs: Int) {
        val player = mediaPlayer
        val id = playbackId
        val target = positionMs.coerceAtLeast(0)
        pendingSeekMs = target
        if (player != null && prepared && id != null) {
            runCatching {
                val duration = max(player.duration, 1)
                val safePosition = target.coerceAtMost(duration - 1)
                player.seekTo(safePosition)
                val percentage = safePosition.toFloat() / duration
                if (player.isPlaying) {
                    playbackTracker.updatePlayingAtPlaybackTime(id, safePosition, percentage)
                } else {
                    playbackTracker.updatePausedAtPlaybackTime(id, safePosition, percentage)
                }
                persistPlayback(id, getPersistedPath(), safePosition)
                updateSessionPlaybackState(
                        if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                        safePosition,
                        duration
                )
            }
        }
    }

    private fun resumePersistedPlayback(forcePlay: Boolean) {
        val prefs = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val path = prefs.getString(KEY_PATH, null)
        val id = prefs.getString(KEY_ID, null)
        val position = prefs.getInt(KEY_POSITION, 0).coerceAtLeast(0)
        val duration = prefs.getInt(KEY_DURATION, 0).coerceAtLeast(0)
        val wasPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)
        playbackSpeed = prefs.getFloat(KEY_SPEED, 1f).coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        playbackTitle = prefs.getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() }
                ?: path?.let { File(it).nameWithoutExtension }
                ?: "Audio message"
        if (path.isNullOrBlank() || id.isNullOrBlank() || !File(path).isFile) {
            stopPlaybackAndService()
            return
        }

        if (forcePlay || wasPlaying) {
            startForegroundCompat(buildNotification(isPlaying = true))
            startPlayback(id, path, position, playbackTitle)
        } else {
            // Android may recreate a sticky service after the process was reclaimed.
            // A user-paused item must remain paused; only an explicit Play action resumes it.
            playbackId = id
            pendingSeekMs = position
            prepared = false
            updateSessionMetadata(id, duration)
            updateSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED, position, duration)
            startForegroundCompat(buildNotification(isPlaying = false))
            playbackTracker.updatePausedAtPlaybackTime(
                    id,
                    position,
                    if (duration > 0) position.toFloat() / duration else 0f
            )
        }
    }

    private fun updateSessionMetadata(id: String, durationMs: Int) {
        val title = playbackTitle.ifBlank { "Audio message" }
        mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs.toLong())
                        .build()
        )
    }

    private fun updateSessionPlaybackState(state: Int, positionMs: Int, durationMs: Int) {
        var actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
        if (queueEventIds.size > 1 && queueIndex >= 0) {
            if (previousQueueIndex() != null) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            if (nextQueueIndex() != null) actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            actions = actions or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        }
        mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setRepeatMode(
                                when (queueRepeatMode) {
                                    REPEAT_ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
                                    REPEAT_ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                                }
                        )
                        .setShuffleMode(if (queueShuffleEnabled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
                        .setState(state, positionMs.toLong(), if (state == PlaybackStateCompat.STATE_PLAYING) playbackSpeed else 0f)
                        .setBufferedPosition(durationMs.toLong())
                        .build()
        )
    }

    private fun buildNotification(isPlaying: Boolean): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(this, 10, it, pendingIntentFlags())
        }
        val toggleAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        val toggleIcon = if (isPlaying) R.drawable.ic_play_pause_pause else R.drawable.ic_play_pause_play
        val toggleLabel = if (isPlaying) "Pause" else "Resume"
        val toggleIntent = PendingIntent.getService(
                this,
                if (isPlaying) 11 else 12,
                Intent(this, AudioPlaybackService::class.java).setAction(toggleAction),
                pendingIntentFlags()
        )
        val showQueueControls = queueEventIds.size > 1 && queueIndex >= 0 &&
                playbackId?.startsWith(VIDEO_PLAYBACK_PREFIX) != true
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        if (showQueueControls) {
            val previousIntent = PendingIntent.getService(
                    this,
                    13,
                    Intent(this, AudioPlaybackService::class.java).setAction(ACTION_PREVIOUS),
                    pendingIntentFlags()
            )
            val nextIntent = PendingIntent.getService(
                    this,
                    14,
                    Intent(this, AudioPlaybackService::class.java).setAction(ACTION_NEXT),
                    pendingIntentFlags()
            )
            builder.addAction(R.drawable.ic_telegram_skip_previous, "Previous", previousIntent)
        }
        val notificationTitle = if (playbackId?.startsWith(VIDEO_PLAYBACK_PREFIX) == true) {
            getString(R.string.telegram_video_playing_notification_title)
        } else {
            getString(R.string.telegram_audio_playing_notification_title)
        }
        val mediaTitle = playbackTitle.ifBlank { "Audio message" }
        builder
                .setSmallIcon(R.drawable.ic_play_pause_play)
                .setContentTitle(notificationTitle)
                .setContentText(mediaTitle)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(isPlaying)
                .addAction(toggleIcon, toggleLabel, toggleIntent)
        if (showQueueControls) {
            val nextIntent = PendingIntent.getService(
                    this,
                    14,
                    Intent(this, AudioPlaybackService::class.java).setAction(ACTION_NEXT),
                    pendingIntentFlags()
            )
            builder.addAction(R.drawable.ic_telegram_skip_next, "Next", nextIntent)
            builder.setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
        } else {
            builder.setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0))
        }
        return builder.build()
    }

    private fun updateNotification(isPlaying: Boolean) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(isPlaying))
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Message audio", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Controls for audio messages playing in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun stopPlaybackAndService() {
        val id = playbackId
        handler.removeCallbacks(progressTicker)
        releasePlayer()
        playbackId = null
        prepared = false
        pendingSeekMs = 0
        playbackSpeed = 1f
        id?.let(playbackTracker::stopPlaybackOrRecorder)
        abandonAudioFocus()
        pausedByAudioFocus = false
        updateSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED, 0, 0)
        val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val savedSpeed = preferences.getFloat(KEY_SPEED, playbackSpeed).coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        preferences.edit().clear().putFloat(KEY_SPEED, savedSpeed).apply()
        stopForeground(true)
        stopSelf()
    }

    private fun requestAudioFocus(): Boolean {
        val manager = getSystemService(AudioManager::class.java) ?: return false
        audioManager = manager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                            AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener, handler)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
            audioFocusRequest = request
            manager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            manager.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let(manager::abandonAudioFocusRequest)
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(audioFocusListener)
        }
    }

    private fun releasePlayer() {
        handler.removeCallbacks(progressTicker)
        runCatching { mediaPlayer?.setOnCompletionListener(null) }
        runCatching { mediaPlayer?.setOnErrorListener(null) }
        runCatching { mediaPlayer?.reset() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        prepared = false
    }

    private fun persistPlayback(id: String, path: String?, positionMs: Int) {
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_ID, id)
                .putString(KEY_PATH, path)
                .putString(KEY_TITLE, playbackTitle)
                .putInt(KEY_POSITION, positionMs.coerceAtLeast(0))
                .putInt(KEY_DURATION, if (prepared) runCatching { mediaPlayer?.duration ?: 0 }.getOrDefault(0) else {
                    getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getInt(KEY_DURATION, 0)
                })
                .putBoolean(KEY_IS_PLAYING, mediaPlayer?.isPlaying == true)
                .putFloat(KEY_SPEED, playbackSpeed)
                .apply()
    }

    private fun savePlaybackPosition(positionMs: Int) {
        val id = playbackId ?: return
        persistPlayback(id, getPersistedPath(), positionMs)
    }

    private fun getPersistedPath(): String? =
            getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_PATH, null)

    private fun pendingIntentFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(progressTicker)
        releasePlayer()
        abandonAudioFocus()
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "message_audio_playback"
        private const val NOTIFICATION_ID = 4821
        private const val PREFERENCES = "message_audio_playback_state"
        private const val QUEUE_PREFERENCES = "message_audio_queue_state"
        private const val KEY_QUEUE_ROOM_ID = "queue_room_id"
        private const val KEY_QUEUE_IDS = "queue_event_ids"
        private const val KEY_QUEUE_ORIGINAL_IDS = "queue_original_event_ids"
        private const val KEY_QUEUE_INDEX = "queue_index"
        private const val KEY_QUEUE_SHUFFLE = "queue_shuffle"
        private const val KEY_QUEUE_REPEAT = "queue_repeat"
        private const val KEY_ID = "playback_id"
        private const val KEY_PATH = "playback_path"
        private const val KEY_TITLE = "playback_title"
        private const val KEY_POSITION = "playback_position"
        private const val KEY_DURATION = "playback_duration"
        private const val KEY_IS_PLAYING = "playback_is_playing"
        private const val KEY_SPEED = "playback_speed"
        private const val MIN_PLAYBACK_SPEED = 0.5f
        private const val MAX_PLAYBACK_SPEED = 2.0f
        private const val PROGRESS_INTERVAL_MS = 250L

        private const val ACTION_PLAY = "im.vector.app.action.AUDIO_PLAY"
        private const val ACTION_PAUSE = "im.vector.app.action.AUDIO_PAUSE"
        private const val ACTION_SEEK = "im.vector.app.action.AUDIO_SEEK"
        private const val ACTION_SET_SPEED = "im.vector.app.action.AUDIO_SET_SPEED"
        private const val ACTION_STOP = "im.vector.app.action.AUDIO_STOP"
        private const val ACTION_STOP_VIDEO = "im.vector.app.action.STOP_BACKGROUND_VIDEO"
        private const val ACTION_RELOAD_QUEUE = "im.vector.app.action.AUDIO_RELOAD_QUEUE"
        private const val ACTION_NEXT = "im.vector.app.action.AUDIO_NEXT"
        private const val ACTION_PREVIOUS = "im.vector.app.action.AUDIO_PREVIOUS"
        private const val ACTION_SET_SHUFFLE = "im.vector.app.action.AUDIO_SET_SHUFFLE"
        private const val ACTION_SET_REPEAT = "im.vector.app.action.AUDIO_SET_REPEAT"
        private const val EXTRA_SHUFFLE = "queue_shuffle"
        private const val EXTRA_REPEAT = "queue_repeat"
        private const val REPEAT_OFF = 0
        private const val REPEAT_ALL = 1
        private const val REPEAT_ONE = 2
        private const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000
        private const val VIDEO_PLAYBACK_PREFIX = "video:"
        private const val EXTRA_PLAYBACK_ID = "playback_id"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_TITLE = "playback_title"
        private const val EXTRA_START_POSITION = "start_position"
        private const val EXTRA_SEEK_POSITION = "seek_position"
        private const val EXTRA_SPEED = "playback_speed"

        fun isActiveFor(context: Context, id: String): Boolean {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            return preferences.getString(KEY_ID, null) == id
        }

        fun isPlayingFor(context: Context, id: String): Boolean {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            return preferences.getString(KEY_ID, null) == id && preferences.getBoolean(KEY_IS_PLAYING, false)
        }

        fun persistedPosition(context: Context, id: String): Int? {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            return if (preferences.getString(KEY_ID, null) == id) preferences.getInt(KEY_POSITION, 0) else null
        }

        fun play(context: Context, id: String, file: File, startPositionMs: Int, title: String? = null) {
            val intent = Intent(context, AudioPlaybackService::class.java)
                    .setAction(ACTION_PLAY)
                    .putExtra(EXTRA_PLAYBACK_ID, id)
                    .putExtra(EXTRA_FILE_PATH, file.absolutePath)
                    .putExtra(EXTRA_TITLE, title?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension)
                    .putExtra(EXTRA_START_POSITION, startPositionMs.coerceAtLeast(0))
            ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_PAUSE))
        }

        fun seek(context: Context, positionMs: Int) {
            context.startService(
                    Intent(context, AudioPlaybackService::class.java)
                            .setAction(ACTION_SEEK)
                            .putExtra(EXTRA_SEEK_POSITION, positionMs.coerceAtLeast(0))
            )
        }

        fun currentPlaybackSpeed(context: Context, id: String? = null): Float {
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            if (id != null && preferences.getString(KEY_ID, null) != id) return 1f
            return preferences.getFloat(KEY_SPEED, 1f).coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
        }

        fun setPlaybackSpeed(context: Context, speed: Float) {
            val safeSpeed = speed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)
            val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            preferences.edit().putFloat(KEY_SPEED, safeSpeed).apply()

            // Do not start a background Service just to save a preference when nothing is
            // playing. An active service receives the command and updates MediaPlayer live.
            if (preferences.getString(KEY_ID, null) != null) {
                context.startService(
                        Intent(context, AudioPlaybackService::class.java)
                                .setAction(ACTION_SET_SPEED)
                                .putExtra(EXTRA_SPEED, safeSpeed)
                )
            }
        }

        fun setQueue(context: Context, roomId: String, eventIds: List<String>, currentEventId: String) {
            val uniqueIds = eventIds.distinct()
            if (uniqueIds.isEmpty()) return
            val queuePreferences = context.getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE)
            val currentIndex = uniqueIds.indexOf(currentEventId).coerceAtLeast(0)
            queuePreferences.edit()
                    .putString(KEY_QUEUE_ROOM_ID, roomId)
                    .putString(KEY_QUEUE_IDS, JSONArray(uniqueIds).toString())
                    .putString(KEY_QUEUE_ORIGINAL_IDS, JSONArray(uniqueIds).toString())
                    .putInt(KEY_QUEUE_INDEX, currentIndex)
                    .putBoolean(KEY_QUEUE_SHUFFLE, false)
                    .putInt(KEY_QUEUE_REPEAT, REPEAT_OFF)
                    .apply()
            val playbackPreferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            if (playbackPreferences.getString(KEY_ID, null) != null) {
                context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_RELOAD_QUEUE))
            }
        }

        fun skipToNext(context: Context) {
            context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_NEXT))
        }

        fun skipToPrevious(context: Context) {
            context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_PREVIOUS))
        }

        fun isShuffleEnabled(context: Context): Boolean =
                context.getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE)
                        .getBoolean(KEY_QUEUE_SHUFFLE, false)

        fun repeatMode(context: Context): Int =
                context.getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE)
                        .getInt(KEY_QUEUE_REPEAT, REPEAT_OFF)

        fun setShuffleEnabled(context: Context, enabled: Boolean) {
            val queuePreferences = context.getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE)
            val ids = parseIds(queuePreferences.getString(KEY_QUEUE_IDS, null))
            val original = parseIds(queuePreferences.getString(KEY_QUEUE_ORIGINAL_IDS, null))
            val currentId = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(KEY_ID, null)
                    ?: ids.getOrNull(queuePreferences.getInt(KEY_QUEUE_INDEX, 0))
            val reordered = if (enabled && ids.size > 1) {
                val history = ids.take(queuePreferences.getInt(KEY_QUEUE_INDEX, 0) + 1)
                val current = currentId?.takeIf { it in ids }
                val remaining = (original.ifEmpty { ids }).filterNot { it in history || it == current }.shuffled()
                (history.filterNot { it == current } + listOfNotNull(current) + remaining)
            } else {
                original.ifEmpty { ids }
            }
            val index = currentId?.let(reordered::indexOf)?.takeIf { it >= 0 } ?: 0
            queuePreferences.edit()
                    .putString(KEY_QUEUE_IDS, JSONArray(reordered).toString())
                    .putInt(KEY_QUEUE_INDEX, index)
                    .putBoolean(KEY_QUEUE_SHUFFLE, enabled && ids.size > 1)
                    .apply()
            if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_ID, null) != null) {
                context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_RELOAD_QUEUE))
            }
        }

        fun setRepeatMode(context: Context, mode: Int) {
            val safeMode = mode.coerceIn(REPEAT_OFF, REPEAT_ONE)
            context.getSharedPreferences(QUEUE_PREFERENCES, Context.MODE_PRIVATE).edit()
                    .putInt(KEY_QUEUE_REPEAT, safeMode)
                    .apply()
            if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY_ID, null) != null) {
                context.startService(
                        Intent(context, AudioPlaybackService::class.java)
                                .setAction(ACTION_SET_REPEAT)
                                .putExtra(EXTRA_REPEAT, safeMode)
                )
            }
        }

        private fun parseIds(serialized: String?): MutableList<String> {
            if (serialized.isNullOrBlank()) return mutableListOf()
            return runCatching {
                val json = JSONArray(serialized)
                MutableList(json.length()) { index -> json.getString(index) }
            }.getOrDefault(mutableListOf())
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_STOP))
        }

        fun stopBackgroundVideoIfActive(context: Context) {
            val activeId = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(KEY_ID, null)
            if (activeId?.startsWith(VIDEO_PLAYBACK_PREFIX) == true) {
                context.startService(Intent(context, AudioPlaybackService::class.java).setAction(ACTION_STOP_VIDEO))
            }
        }

        fun playVideoAudioInBackground(context: Context, file: File, positionMs: Int) {
            if (!file.isFile) return
            play(context, VIDEO_PLAYBACK_PREFIX + file.absolutePath, file, positionMs, "Video")
        }
    }
}
