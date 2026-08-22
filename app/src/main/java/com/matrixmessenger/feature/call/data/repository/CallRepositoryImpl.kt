package com.matrixmessenger.feature.call.data.repository

import com.matrixmessenger.core.webrtc.WebRtcManager
import com.matrixmessenger.feature.call.data.CallSignalEvent
import com.matrixmessenger.feature.call.data.CallSignalingHandler
import com.matrixmessenger.feature.call.domain.model.*
import com.matrixmessenger.feature.call.domain.repository.CallRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.matrix.android.sdk.api.session.call.MxCall
import org.matrix.android.sdk.api.session.room.model.call.EndCallReason
import org.webrtc.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepositoryImpl @Inject constructor(
    private val webRtcManager: WebRtcManager,
    private val signalingHandler: CallSignalingHandler
) : CallRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentMxCall: MxCall? = null
    
    private val _activeCallId = MutableStateFlow<String?>(null)
    override val activeCallId: Flow<String?> = _activeCallId.asStateFlow()

    private val _callHistory = MutableStateFlow<List<CallHistoryEntry>>(emptyList())

    init {
        observeSignaling()
    }

    private fun observeSignaling() {
        signalingHandler.callEvents.filterNotNull().onEach { event ->
            when (event) {
                is CallSignalEvent.Incoming -> handleIncomingCall(event.call)
                is CallSignalEvent.Answered -> handleAnswerReceived(event.answer.answer.sdp)
                is CallSignalEvent.IceCandidates -> handleIceCandidatesReceived(event.candidates.candidates)
                is CallSignalEvent.HungUp -> handleHangupReceived()
                is CallSignalEvent.Rejected -> handleRejectReceived()
            }
        }.launchIn(repositoryScope)
    }

    override suspend fun startCall(roomId: String, isVideoCall: Boolean): Result<String> {
        return runCatching {
            webRtcManager.initialize()
            
            // In a real app, we'd find the other member in the room
            val otherUserId = "@mock_user:matrix.org" 
            val mxCall = signalingHandler.createCall(roomId, otherUserId, isVideoCall)
                ?: throw Exception("Failed to create Matrix call")
            
            currentMxCall = mxCall
            _activeCallId.value = mxCall.callId
            
            val offer = createOffer()
            mxCall.offerSdp(offer.description)
            
            mxCall.callId
        }
    }

    private suspend fun createOffer(): SessionDescription = suspendCancellableCoroutine { cont ->
        webRtcManager.createPeerConnection(createPcObserver())
        webRtcManager.createOffer(MediaConstraints(), object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                webRtcManager.setLocalDescription(desc, this)
                cont.resume(desc) {}
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { cont.cancel(Exception(p0)) }
            override fun onSetFailure(p0: String?) {}
        })
    }

    private fun createPcObserver() = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            currentMxCall?.sendLocalCallCandidates(listOf(org.matrix.android.sdk.api.session.room.model.call.CallCandidate(
                candidate.sdpMid,
                candidate.sdpMLineIndex,
                candidate.sdp
            )))
        }
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
    }

    override suspend fun answerCall(callId: String): Result<Unit> {
        return runCatching {
            val mxCall = currentMxCall ?: throw Exception("No active call to answer")
            webRtcManager.initialize()
            webRtcManager.createPeerConnection(createPcObserver())
            
            // We need to set the remote description first if it's an incoming call
            // In a real flow, handleIncomingCall would have set the remote description
            
            val answer = createAnswer()
            mxCall.accept(answer.description)
        }
    }

    private suspend fun createAnswer(): SessionDescription = suspendCancellableCoroutine { cont ->
        webRtcManager.createAnswer(MediaConstraints(), object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                webRtcManager.setLocalDescription(desc, this)
                cont.resume(desc) {}
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { cont.cancel(Exception(p0)) }
            override fun onSetFailure(p0: String?) {}
        })
    }

    override suspend fun rejectCall(callId: String): Result<Unit> {
        return runCatching {
            currentMxCall?.reject()
            cleanup()
        }
    }

    override suspend fun endCall(callId: String): Result<Unit> {
        return runCatching {
            currentMxCall?.hangUp(EndCallReason.USER_HANGUP)
            cleanup()
        }
    }

    private fun handleIncomingCall(call: MxCall) {
        currentMxCall = call
        _activeCallId.value = call.callId
        
        // In a real app, you'd trigger an incoming call UI here
    }

    private fun handleAnswerReceived(sdp: String) {
        webRtcManager.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp), object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(p0: String?) {}
        })
    }

    private fun handleIceCandidatesReceived(candidates: List<org.matrix.android.sdk.api.session.room.model.call.CallCandidate>) {
        candidates.forEach {
            webRtcManager.addIceCandidate(IceCandidate(it.sdpMid, it.sdpMLineIndex, it.candidate))
        }
    }

    private fun handleHangupReceived() {
        cleanup()
    }

    private fun handleRejectReceived() {
        cleanup()
    }

    private fun cleanup() {
        webRtcManager.release()
        _activeCallId.value = null
        currentMxCall = null
    }

    override suspend fun toggleMicrophone(callId: String, mute: Boolean): Result<Unit> = runCatching { webRtcManager.toggleMicrophone(mute) }
    override suspend fun toggleCamera(callId: String, enable: Boolean): Result<Unit> = runCatching { webRtcManager.toggleCamera(enable) }
    override suspend fun switchCamera(callId: String, useFront: Boolean): Result<Unit> = runCatching { webRtcManager.switchCamera() }
    override suspend fun toggleSpeaker(callId: String, speakerOn: Boolean): Result<Unit> = runCatching { webRtcManager.toggleSpeaker(speakerOn) }

    override fun getCallHistory(): Flow<List<CallHistoryEntry>> = _callHistory.asStateFlow()
    override suspend fun clearCallHistory(): Result<Unit> = runCatching { _callHistory.value = emptyList() }
}
