# Walkthrough - Restored Buildability

I have fixed multiple compilation errors across the project, including issues with Matrix SDK 2 property names, missing repository methods, use case mismatches, and UI component errors.

## Changes Made

### Matrix Data Layer

#### [MessageMapper.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/mapper/MessageMapper.kt)
- Corrected media info property names to match Matrix SDK 2:
    - `info` -> `videoInfo` for `MessageVideoContent`.
    - `info` -> `audioInfo` for `MessageAudioContent`.
    - `fileInfo` -> `info` for `MessageFileContent`.
- All `mimeType` properties correctly map to SDK model properties.

#### [MatrixRepositoryImpl.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/repository/MatrixRepositoryImpl.kt)
- Fixed `mapMatrixError` to accept `Throwable` instead of `Exception`, ensuring compatibility with the SDK's `Failure` type.

---

### Domain Layer & Use Cases

#### [MatrixRepository.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/domain/repository/MatrixRepository.kt)
- Added missing `observeRoomMembers` and `sendTextMessage` method declarations to the interface to match its implementation.

#### [Use Cases]
- **Auth**: Updated `GetCurrentSessionUseCase` to return `MatrixUser?` correctly.
- **Media**: Updated `DownloadMediaUseCase` return type to `Result<Unit>`.
- **Messages**:
    - Updated `SendMediaMessageUseCase` to accept `Uri` and return `Result<Unit>`.
    - Updated `SendTextMessageUseCase` return type to `Result<Unit>`.
- **Room**: Fixed `CreateGroupUseCase` topic nullability.

---

### Call (VoIP) Feature

#### [CallRepository.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/call/domain/repository/CallRepository.kt) & [CallRepositoryImpl.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/call/data/repository/CallRepositoryImpl.kt)
- Added and implemented `toggleSpeaker` functionality.
- Fixed `MatrixUser` package references to point to the correct domain model.

#### [WebRtcManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/webrtc/WebRtcManager.kt)
- Added `toggleSpeaker` low-level implementation using `AudioManager`.

#### [CallViewModel.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/call/presentation/viewModel/CallViewModel.kt)
- Added `ToggleSpeaker` event and integrated `ToggleSpeakerUseCase`.

---

### UI & Animation

#### [ChatScreen.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/ui/screens/chat/ChatScreen.kt)
- Fixed a typo where `replyTo` was used instead of `replyingTo`.
- Removed the `enabled` parameter from `FloatingActionButton` (which is not supported in Material 3) and moved the logic to the `onClick` handler.

#### [MatrixMotion.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/ui/theme/MatrixMotion.kt)
- Explicitly provided the type parameter `T` to the `tween` function to fix a type inference error.

## Verification Results

### Automated Tests
- Ran `./gradlew :app:compileDebugKotlin`.
- **Result**: `Build finished successfully.`
