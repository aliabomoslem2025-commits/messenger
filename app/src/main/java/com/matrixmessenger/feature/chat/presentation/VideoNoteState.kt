package com.matrixmessenger.feature.chat.presentation

/**
 * Production-grade state machine for video note recording, 
 * following Telegram-style lifecycle.
 */
sealed class VideoNoteState {
    object Idle : VideoNoteState()
    object Pressing : VideoNoteState()
    object Preparing : VideoNoteState()
    data class Recording(val durationMs: Long, val progress: Float) : VideoNoteState()
    object StopRequested : VideoNoteState()
    object Finalizing : VideoNoteState()
    object Sending : VideoNoteState()
    object Done : VideoNoteState()
    object Cancelled : VideoNoteState()
    data class Failed(val error: String) : VideoNoteState()
}
