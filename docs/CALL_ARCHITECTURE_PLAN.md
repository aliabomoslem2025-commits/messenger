# Call Architecture Plan

This document outlines the strategy for implementing Matrix VoIP (Voice over IP) signaling and WebRTC media integration.

## 1. Matrix VoIP Signaling (MSC compliance)

Matrix calls use specific event types for signaling:
- `m.call.invite`: Initiates a call, contains the Offer SDP.
- `m.call.candidates`: Exchanges ICE candidates.
- `m.call.answer`: Responds to an invite, contains the Answer SDP.
- `m.call.hangup`: Ends or rejects a call.
- `m.call.select_answer`: Used in multi-device scenarios.

### Proposed Implementation
- **Signaling Layer**: `CallSignalingHandler` (new component) will listen for these events via a Matrix `Timeline` or global event listener.
- **State Machine**: A `CallStateMachine` will manage transitions between `IDLE`, `DIALING`, `RINGING`, `CONNECTING`, `CONNECTED`, and `ENDED`.

## 2. WebRTC Media Integration

### Components
- **`WebRtcManager`**: (Already exists) Handles `PeerConnectionFactory`, `PeerConnection`, and media tracks (Audio/Video).
- **SurfaceViewRenderer**: Standard WebRTC view for displaying local and remote video streams.

### Workflow
1. **Outgoing Call**:
   - `WebRtcManager` creates an Offer SDP.
   - `CallSignalingHandler` sends `m.call.invite` with the Offer.
2. **Incoming Call**:
   - `CallSignalingHandler` receives `m.call.invite`.
   - `WebRtcManager` sets Remote Description and creates an Answer SDP.
   - `CallSignalingHandler` sends `m.call.answer`.
3. **ICE Negotiation**:
   - Both sides exchange `m.call.candidates` as they are discovered by their respective `PeerConnection` objects.

## 3. Dependency Injection & Ownership

- `CallRepositoryImpl` will orchestrate both `CallSignalingHandler` and `WebRtcManager`.
- The `CallViewModel` will interact only with the `CallRepository`.

## 4. Next Steps (Post-Architecture Hardening)

1. Implement `CallSignalingHandler` using the Matrix Android SDK's VoIP service if available, or manual event sending.
2. Integrate `WebRtcManager` ICE candidate discovery with the signaling layer.
3. Replace placeholder UI in `CallScreen.kt` with actual video rendering components once signaling is functional.

> [!IMPORTANT]
> This plan focuses on 1-to-1 calls. Group calls (Matrix MSC3401) will be considered in a future phase.
