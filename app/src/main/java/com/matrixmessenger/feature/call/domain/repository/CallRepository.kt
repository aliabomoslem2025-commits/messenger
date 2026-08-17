package com.matrixmessenger.feature.call.domain.repository

import com.matrixmessenger.feature.call.domain.model.CallHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Call-related operations.
 * Acts as the domain-layer contract for call functionality.
 */
interface CallRepository {
    
    /**
     * Flow of the current active call ID (if any).
     */
    val activeCallId: Flow<String?>
    
    /**
     * Start a new call in the specified room.
     * @param roomId The Matrix room ID to start the call in.
     * @param isVideoCall True for video call, false for audio-only.
     * @return Result containing the call ID if successful.
     */
    suspend fun startCall(roomId: String, isVideoCall: Boolean): Result<String>
    
    /**
     * Answer an incoming call.
     * @param callId The ID of the call to answer.
     */
    suspend fun answerCall(callId: String): Result<Unit>
    
    /**
     * Reject an incoming call.
     * @param callId The ID of the call to reject.
     */
    suspend fun rejectCall(callId: String): Result<Unit>
    
    /**
     * End an ongoing or ringing call.
     * @param callId The ID of the call to end.
     */
    suspend fun endCall(callId: String): Result<Unit>
    
    /**
     * Toggle the local microphone state.
     * @param callId The active call ID.
     * @param mute True to mute, false to unmute.
     */
    suspend fun toggleMicrophone(callId: String, mute: Boolean): Result<Unit>
    
    /**
     * Toggle the local camera state (enable/disable video track).
     * @param callId The active call ID.
     * @param enable True to enable camera, false to disable.
     */
    suspend fun toggleCamera(callId: String, enable: Boolean): Result<Unit>
    
    /**
     * Switch between front and back camera.
     * @param callId The active call ID.
     * @param useFront True to use front camera, false for back.
     */
    suspend fun switchCamera(callId: String, useFront: Boolean): Result<Unit>
    
    /**
     * Get the history of calls for the current user.
     * @return Flow of list of call history entries.
     */
    fun getCallHistory(): Flow<List<CallHistoryEntry>>
    
    /**
     * Clear the local call history.
     */
    suspend fun clearCallHistory(): Result<Unit>
}
