package com.matrixmessenger.feature.call.data

import com.matrixmessenger.data.matrix.MatrixClientManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.matrix.android.sdk.api.session.call.*
import org.matrix.android.sdk.api.session.room.model.call.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges Matrix signaling events with the Call system.
 */
@Singleton
class CallSignalingHandler @Inject constructor(
    private val matrixClientManager: MatrixClientManager
) : CallListener {

    private val _callEvents = MutableStateFlow<CallSignalEvent?>(null)
    val callEvents = _callEvents.asStateFlow()

    init {
        matrixClientManager.getCurrentSession()?.callSignalingService()?.addCallListener(this)
    }

    suspend fun getTurnServers(): TurnServerResponse? {
        return matrixClientManager.getCurrentSession()?.callSignalingService()?.getTurnServer()
    }

    fun createCall(roomId: String, otherUserId: String, isVideo: Boolean): MxCall? {
        return matrixClientManager.getCurrentSession()?.callSignalingService()?.createOutgoingCall(
            roomId = roomId,
            otherUserId = otherUserId,
            isVideoCall = isVideo
        )
    }

    override fun onCallInviteReceived(mxCall: MxCall, callInviteContent: CallInviteContent) {
        _callEvents.value = CallSignalEvent.Incoming(mxCall, callInviteContent)
    }

    override fun onCallIceCandidateReceived(mxCall: MxCall, iceCandidatesContent: CallCandidatesContent) {
        _callEvents.value = CallSignalEvent.IceCandidates(mxCall, iceCandidatesContent)
    }

    override fun onCallAnswerReceived(callAnswerContent: CallAnswerContent) {
        _callEvents.value = CallSignalEvent.Answered(callAnswerContent)
    }

    override fun onCallHangupReceived(callHangupContent: CallHangupContent) {
        _callEvents.value = CallSignalEvent.HungUp(callHangupContent)
    }

    override fun onCallRejectReceived(callRejectContent: CallRejectContent) {
        _callEvents.value = CallSignalEvent.Rejected(callRejectContent)
    }

    override fun onCallSelectAnswerReceived(callSelectAnswerContent: CallSelectAnswerContent) {}
    override fun onCallNegotiateReceived(callNegotiateContent: CallNegotiateContent) {}
    override fun onCallManagedByOtherSession(callId: String) {}
    override fun onCallAssertedIdentityReceived(callAssertedIdentityContent: CallAssertedIdentityContent) {}
}

sealed interface CallSignalEvent {
    data class Incoming(val call: MxCall, val invite: CallInviteContent) : CallSignalEvent
    data class Answered(val answer: CallAnswerContent) : CallSignalEvent
    data class HungUp(val hangup: CallHangupContent) : CallSignalEvent
    data class Rejected(val reject: CallRejectContent) : CallSignalEvent
    data class IceCandidates(val call: MxCall, val candidates: CallCandidatesContent) : CallSignalEvent
}
