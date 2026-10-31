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
import im.vector.app.features.home.room.detail.timeline.helper.AudioMessagePlaybackTracker
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

    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var playbackId: String? = null
    private var playbackTitle: String = "Audio message"
    private var prepared = false
    private var pendingSeekMs = 0
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
                stopPlaybackAndService()
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
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
        mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                        .setActions(actions)
                        .setState(state, positionMs.toLong(), if (state == PlaybackStateCompat.STATE_PLAYING) 1f else 0f)
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
        val notificationTitle = if (playbackId?.startsWith(VIDEO_PLAYBACK_PREFIX) == true) {
            getString(R.string.telegram_video_playing_notification_title)
        } else {
            getString(R.string.telegram_audio_playing_notification_title)
        }
        val mediaTitle = playbackTitle.ifBlank { "Audio message" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_pause_play)
                .setContentTitle(notificationTitle)
                .setContentText(mediaTitle)
                .setContentIntent(contentIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(toggleIcon, toggleLabel, toggleIntent)
                .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0))
                .build()
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
        id?.let(playbackTracker::stopPlaybackOrRecorder)
        abandonAudioFocus()
        pausedByAudioFocus = false
        updateSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED, 0, 0)
        getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply()
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
        private const val KEY_ID = "playback_id"
        private const val KEY_PATH = "playback_path"
        private const val KEY_TITLE = "playback_title"
        private const val KEY_POSITION = "playback_position"
        private const val KEY_DURATION = "playback_duration"
        private const val KEY_IS_PLAYING = "playback_is_playing"
        private const val PROGRESS_INTERVAL_MS = 250L

        private const val ACTION_PLAY = "im.vector.app.action.AUDIO_PLAY"
        private const val ACTION_PAUSE = "im.vector.app.action.AUDIO_PAUSE"
        private const val ACTION_SEEK = "im.vector.app.action.AUDIO_SEEK"
        private const val ACTION_STOP = "im.vector.app.action.AUDIO_STOP"
        private const val ACTION_STOP_VIDEO = "im.vector.app.action.STOP_BACKGROUND_VIDEO"
        private const val VIDEO_PLAYBACK_PREFIX = "video:"
        private const val EXTRA_PLAYBACK_ID = "playback_id"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_TITLE = "playback_title"
        private const val EXTRA_START_POSITION = "start_position"
        private const val EXTRA_SEEK_POSITION = "seek_position"

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
