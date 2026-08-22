package com.matrixmessenger.core.designsystem.components

/**
 * Explicit state model for the Messenger Composer.
 */
sealed class ComposerState {
    object Idle : ComposerState()
    data class Typing(val text: String) : ComposerState()
    data class Replying(val text: String, val replyToEventId: String) : ComposerState()
    data class RecordingVoice(val durationMs: Long, val amplitude: Float) : ComposerState()
    data class RecordingVideo(val durationMs: Long) : ComposerState()
    data class Uploading(val progress: Float) : ComposerState()
    object Sending : ComposerState()
    data class Failed(val error: String) : ComposerState()
}
