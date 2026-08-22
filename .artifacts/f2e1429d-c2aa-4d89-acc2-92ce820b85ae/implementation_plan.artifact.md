# Implementation Plan - Fixing Kotlin Compilation Errors

Fixing the remaining Kotlin compilation errors across the project to achieve a successful build.

## Proposed Changes

### 1. Animation Engine Fixes
#### [MODIFY] [AnimationEngine.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/animation/engine/AnimationEngine.kt)
- Fix the `sequence` method to pass `this` to the animation lambdas.
- Fix the `parallel` method similarly.

### 2. WebRTC Fixes
#### [MODIFY] [WebRtcManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/core/webrtc/WebRtcManager.kt)
- Ensure all WebRTC classes are correctly imported from `org.webrtc`.

### 3. Matrix SDK Integration Fixes
#### [MODIFY] [MatrixClientManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/MatrixClientManager.kt)
- Update API calls to match Matrix SDK `1.6.36`:
    - `leaveRoom` on `RoomService`.
    - `sendTextMessage`, `editTextMessage`, `replyToMessage` on `SendService`.
    - `uploadFile` on `FileService`.
    - `setDisplayName`, `setAvatarUrl` on `ProfileService`.
- Fix `TimelineEvent` vs `Event` type mismatches.
- Fix missing imports for `flow`.

#### [MODIFY] [MatrixRepositoryImpl.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/repository/MatrixRepositoryImpl.kt)
- Align with the same SDK changes as `MatrixClientManager`.
- Fix `UserIdentifier` and `Credentials` usage.

### 4. Mapper and Model Fixes
#### [MODIFY] [MessageMapper.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/mapper/MessageMapper.kt)
- Fix unresolved references to SDK message content and status types.
- Ensure all domain model fields are correctly populated.

#### [MODIFY] [UserMapper.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/mapper/UserMapper.kt)
- Align with SDK user and presence types.

### 5. UI and Theme Fixes
#### [MODIFY] [Color.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/ui/theme/Color.kt)
- Change `MatrixTypography()` call to `MatrixTypography.toTypography()`.

#### [MODIFY] [MatrixMotion.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/ui/theme/MatrixMotion.kt)
- Fix type inference in `tweenSpec`.

#### [MODIFY] [SearchScreen.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/search/presentation/screen/SearchScreen.kt)
- Fix icon references and typography property access.

### 6. Voice and Audio Fixes
#### [MODIFY] [AudioRecorder.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/voice/data/recorder/AudioRecorder.kt)
- Fix `MediaRecorder` usage for older Android versions where `Builder` might not be available or differs.

## Verification Plan
### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify all Kotlin files compile.
- Run `./gradlew :app:assembleDebug` for a full build.

### Manual Verification
- Deploy to device/emulator to ensure animations and theme are working as expected.
