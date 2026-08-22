# Implementation Plan - Telegram-Level Media Interaction System

This plan details the implementation of a professional, Telegram-style media interaction system, including a smart recording button, circular video notes, and a high-performance media download pipeline.

## User Review Required

> [!IMPORTANT]
> The recording system uses a robust state machine to manage complex transitions between IDLE, RECORDING, and SENDING. This replaces simple boolean flags to ensure UI consistency and error recovery.

> [!WARNING]
> Circular video note recording requires CameraX integration. This will involve high-performance `Surface` management to ensure smooth, jitter-free recording in a circular frame.

## Proposed Changes

### [Phase 1: Recording Engine & State Machine]

#### [NEW] [RecordingState.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/message/domain/RecordingState.kt)
* Define a `sealed interface RecordingState` with states: `Idle`, `PressDetected`, `VoiceRecording`, `VideoPreparing`, `VideoRecording`, `Stopping`, `Processing`, `Uploading`, `Sending`, `Failed`.

#### [NEW] [MediaPipelineManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/media/MediaPipelineManager.kt)
* Central orchestrator for recording, processing, and auto-sending.
* Coordinates `AudioRecorder` and the new `VideoNoteRecorder`.

#### [NEW] [VideoNoteRecorder.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/voice/data/recorder/VideoNoteRecorder.kt)
* CameraX-based recorder for circular video notes.
* Handles max duration (20s) and auto-finalize.

---

### [Phase 2: Smart Interaction UI]

#### [MODIFY] [MessageInput.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/chat/presentation/components/MessageInput.kt)
* Implement the Smart Button:
    - Single tap toggle between Mic and Camera modes with morph animation.
    - Long-press logic for immediate recording based on current mode.
    - Spring-based physics and haptic feedback.

#### [NEW] [RecordingOverlay.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/message/presentation/components/RecordingOverlay.kt)
* Telegram-style UI for recording:
    - Circular camera preview for video notes.
    - Waveform animation for voice notes.
    - Lock/Cancel gesture support.
    - Background dimming with premium transitions.

---

### [Phase 3: Media Download System]

#### [NEW] [MediaDownloadController.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/media/MediaDownloadController.kt)
* High-level controller managing download states and progress observation.

#### [MODIFY] [MessageContent.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/message/presentation/components/MessageContent.kt)
* Update media rendering to include:
    - Circular download button with size indicator.
    - Expanding progress ring animation during download.
    - Smooth morph transition from "Download" to "Play/Open" icon.

---

## Verification Plan

### Automated Tests (Unit & Compose)
* `RecordingStateMachineTest`: Verify every state transition and lifecycle cleanup.
* `MediaDownloadAnimationPreview`: Verify the progress ring expansion and icon morphing.

### Manual Verification
1. **Smart Button**: Verify tapping swaps between Mic and Camera with haptics and spring animations.
2. **Video Note**: Long-press in Camera mode. Verify circular overlay appears, records for up to 20s, and auto-sends on release.
3. **Voice Note**: Long-press in Mic mode. Verify waveform and timer UI. Verify lock gesture.
4. **Media Download**: Tap an unloaded image/video. Verify progress ring expansion and visual sharpening of the thumbnail.

---
**Next Step**: After approval, I will begin implementing Phase 1 and the Recording State Machine.
