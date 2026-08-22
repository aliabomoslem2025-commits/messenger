# Implementation Plan: Video Note (Telegram Style)

Implement circular video notes using CameraX, following the provided architectural documentation.

## User Review Required

> [!IMPORTANT]
> - This feature requires `androidx.camera:camera-video` which will be added to the project.
> - A `FileProvider` will be configured in `AndroidManifest.xml` to allow the Matrix SDK to access and clean up temporary video files safely.
> - The video quality is set to `Quality.SD` for optimal performance.

## Proposed Changes

### Dependencies & Configuration

#### [MODIFY] [libs.versions.toml](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/gradle/libs.versions.toml)
- Add `androidx-camera-video` library definition.

#### [MODIFY] [build.gradle.kts](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/build.gradle.kts)
- Add `libs.androidx.camera.video` to dependencies.

#### [MODIFY] [AndroidManifest.xml](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/AndroidManifest.xml)
- Add `androidx.core.content.FileProvider` declaration.
- Add `grantUriPermissions="true"` as requested.

#### [NEW] [file_paths.xml](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/res/xml/file_paths.xml)
- Define cache paths for `FileProvider`.

---

### Hardware Layer

#### [NEW] [VideoNoteRecorder.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/voice/data/recorder/VideoNoteRecorder.kt)
- Implement video recording using CameraX `VideoCapture` and `Recorder`.
- Support front camera by default.
- Provide a `PreviewView` binding for the circular UI.

---

### Matrix SDK & Data Layer

#### [MODIFY] [MatrixClientManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/MatrixClientManager.kt)
- Add `sendVideoNote` method.
- Implement MSC2457 metadata tagging (`org.matrix.msc2457.video_note`).

#### [MODIFY] [MatrixRepository.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/domain/repository/MatrixRepository.kt)
- Add `sendVideoNote` signature.

#### [MODIFY] [MatrixRepositoryImpl.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/repository/MatrixRepositoryImpl.kt)
- Implement `sendVideoNote` delegating to `MatrixClientManager`.

---

### Presentation Layer

#### [MODIFY] [ChatViewModel.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/chat/presentation/ChatViewModel.kt)
- Integrate `VideoNoteRecorder`.
- Manage video recording lifecycle (start, stop, cancel).
- Handle `RecordingMode.VIDEO` in `startRecording` and `stopAndSendRecording`.

---

### UI Layer

#### [MODIFY] [ChatScreen.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/chat/presentation/ChatScreen.kt)
- Show circular camera preview overlay during video recording.
- Implement circular progress ring around the preview.

#### [NEW] [VideoNoteBubble.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/message/presentation/components/VideoNoteBubble.kt)
- Create a circular bubble component for displaying video notes in the chat.
- Use `ExoPlayer` for playback.

#### [MODIFY] [MessageContent.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/message/presentation/components/MessageContent.kt)
- Route video notes to `VideoNoteBubble`.

## Verification Plan

### Automated Tests
- Unit tests for `ChatViewModel` to verify state transitions during video recording.

### Manual Verification
1. Open Chat.
2. Tap the mic icon to switch to Video mode.
3. Long-press the video icon.
4. Verify circular preview appears and recording starts.
5. Verify progress ring advances.
6. Release to send.
7. Verify the video note appears as a circular bubble in the chat and plays correctly.
8. Verify it can be resent if failed (using the previously fixed resend logic).
