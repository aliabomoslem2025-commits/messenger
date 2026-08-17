package com.matrixmessenger.feature.voice.domain.model

/**
 * Represents the current state of voice message recording.
 */
sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(
        val durationMillis: Long,
        val amplitude: Float = 0f
    ) : RecordingState
    data object Locked : RecordingState // Slide-to-lock mode
    data object Cancelling : RecordingState // Slide-to-cancel mode
    data class Completed(val audioFilePath: String) : RecordingState
    data class Error(val message: String) : RecordingState
}

/**
 * UI model for a voice message.
 */
data class VoiceMessageUiModel(
    val id: String,
    val durationMillis: Long,
    val audioUrl: String?,
    val waveform: List<Float>, // Normalized amplitude values
    val isPlaying: Boolean = false,
    val playbackProgress: Float = 0f,
    val isLoading: Boolean = false
)

/**
 * Configuration for voice recording.
 */
data class RecordingConfig(
    val sampleRate: Int = 44100,
    val channelCount: Int = 1, // Mono
    val audioFormat: String = "aac",
    val bitRate: Int = 64000, // 64 kbps
    val maxDurationMillis: Long = 300_000L // 5 minutes max
)
