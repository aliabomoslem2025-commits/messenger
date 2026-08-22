package com.matrixmessenger.domain.model

import java.io.File

sealed class VideoNoteMediaState {
    object NotAvailable : VideoNoteMediaState()
    object Requested : VideoNoteMediaState()
    data class Downloading(val progress: Float) : VideoNoteMediaState()
    object Decrypting : VideoNoteMediaState()
    data class Ready(val file: File) : VideoNoteMediaState()
    data class Failed(val error: String) : VideoNoteMediaState()
}
