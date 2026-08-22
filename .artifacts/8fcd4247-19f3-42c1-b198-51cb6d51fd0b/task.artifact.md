# Tasks - Project Completion & Calling System

- [x] **Phase 1: Production Hardening**
    - [x] Fix `RoomMapper.kt` (Pinned/Muted state)
    - [x] Fix `MessageMapper.kt` (Forward info/Redaction)
    - [x] Refactor `HomeScreen.kt` to use `MatrixColors`
    - [x] Refactor `ChatScreen.kt` with `AnimationEngine`
    - [x] Complete `MatrixMediaEngine.kt`
- [x] **Phase 2: Calling Foundation**
    - [x] Update `WebRtcManager.kt` (Dynamic IceServers, Signaling hooks)
    - [x] Create `CallSignalingHandler.kt` (MSC Signaling)
    - [x] Update `CallRepositoryImpl.kt` (Real signaling)
- [x] **Phase 3: Calling UI & Service**
    - [x] Create `CallService.kt` (Foreground Service)
    - [x] Implement `CallScreen.kt` (Video rendering, Controls)
    - [x] Create `IncomingCallScreen.kt`
- [x] **Phase 4: Missing Feature Activation**
    - [x] Implement Search Module (Global/In-chat)
    - [x] Implement Voice Message Module (Recording/Playback)
    - [x] Implement `SyncWorker.kt` (WorkManager)
