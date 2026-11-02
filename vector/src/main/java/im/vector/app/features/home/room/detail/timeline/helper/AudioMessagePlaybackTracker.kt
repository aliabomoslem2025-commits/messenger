/*
 * Copyright 2021-2024 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial
 * Please see LICENSE files in the repository root for full details.
 */

package im.vector.app.features.home.room.detail.timeline.helper

import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioMessagePlaybackTracker @Inject constructor() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = mutableMapOf<String, Listener>()
    private val activityListeners = mutableListOf<ActivityListener>()
    private val states = mutableMapOf<String, Listener.State>()

    @Synchronized
    fun trackActivity(listener: ActivityListener) {
        activityListeners.add(listener)
    }

    @Synchronized
    fun untrackActivity(listener: ActivityListener) {
        activityListeners.remove(listener)
    }

    @Synchronized
    fun track(id: String, listener: Listener) {
        listeners[id] = listener

        val currentState = states[id] ?: Listener.State.Idle
        mainHandler.post {
            listener.onUpdate(currentState)
        }
    }

    @Synchronized
    fun untrack(id: String) {
        listeners.remove(id)
    }

    @Synchronized
    fun unregisterListeners() {
        val currentListeners = listeners.values.toList()
        listeners.clear()
        mainHandler.post {
            currentListeners.forEach { it.onUpdate(Listener.State.Idle) }
        }
    }

    /**
     * Set state and notify the listeners.
     */
    @Synchronized
    private fun setState(key: String, state: Listener.State) {
        states[key] = state
        val listener = listeners[key]
        val activityListenerSnapshot = activityListeners.toList()
        val isPlayingOrRecording = states.values.any { it is Listener.State.Playing || it is Listener.State.Recording }
        mainHandler.post {
            listener?.onUpdate(state)
            activityListenerSnapshot.forEach { it.onUpdate(isPlayingOrRecording) }
        }
    }

    @Synchronized
    fun startPlayback(id: String) {
        val currentPlaybackTime = getPlaybackTime(id) ?: 0
        val currentPercentage = getPercentage(id) ?: 0f
        val currentState = Listener.State.Playing(currentPlaybackTime, currentPercentage)
        setState(id, currentState)
        // Pause any active playback
        states
                .filter { it.key != id }
                .keys
                .forEach { key ->
                    val state = states[key]
                    if (state is Listener.State.Playing) {
                        // Paused(state.playbackTime) state should also be considered later.
                        setState(key, Listener.State.Idle)
                    }
                }
    }

    @Synchronized
    fun pauseAllPlaybacks() {
        listeners.keys.forEach(::pausePlayback)
    }

    @Synchronized
    fun pausePlayback(id: String) {
        val state = getPlaybackState(id)
        if (state is Listener.State.Playing) {
            val currentPlaybackTime = state.playbackTime
            val currentPercentage = state.percentage
            setState(id, Listener.State.Paused(currentPlaybackTime, currentPercentage))
        }
    }

    @Synchronized
    fun stopPlaybackOrRecorder(id: String) {
        val state = getPlaybackState(id)
        if (state !is Listener.State.Error) {
            setState(id, Listener.State.Idle)
        }
    }

    @Synchronized
    fun onError(id: String, error: Throwable) {
        setState(id, Listener.State.Error(error))
    }

    @Synchronized
    fun updatePlayingAtPlaybackTime(id: String, time: Int, percentage: Float) {
        setState(id, Listener.State.Playing(time, percentage))
    }

    @Synchronized
    fun updatePausedAtPlaybackTime(id: String, time: Int, percentage: Float) {
        setState(id, Listener.State.Paused(time, percentage))
    }

    @Synchronized
    fun updateCurrentRecording(id: String, amplitudeList: List<Int>) {
        setState(id, Listener.State.Recording(amplitudeList))
    }

    @Synchronized
    fun getPlaybackState(id: String) = states[id]

    @Synchronized
    fun getPlaybackTime(id: String): Int? {
        return when (val state = states[id]) {
            is Listener.State.Playing -> state.playbackTime
            is Listener.State.Paused -> state.playbackTime
            is Listener.State.Recording,
            is Listener.State.Error,
            Listener.State.Idle,
            null -> null
        }
    }

    @Synchronized
    fun getPercentage(id: String): Float? {
        return when (val state = states[id]) {
            is Listener.State.Playing -> state.percentage
            is Listener.State.Paused -> state.percentage
            is Listener.State.Recording,
            is Listener.State.Error,
            Listener.State.Idle,
            null -> null
        }
    }

    companion object {
        const val RECORDING_ID = "RECORDING_ID"
    }

    fun interface Listener {

        fun onUpdate(state: State)

        sealed class State {
            object Idle : State()
            data class Error(val failure: Throwable) : State()
            data class Playing(val playbackTime: Int, val percentage: Float) : State()
            data class Paused(val playbackTime: Int, val percentage: Float) : State()
            data class Recording(val amplitudeList: List<Int>) : State()
        }
    }

    fun interface ActivityListener {
        fun onUpdate(isPlayingOrRecording: Boolean)
    }
}
