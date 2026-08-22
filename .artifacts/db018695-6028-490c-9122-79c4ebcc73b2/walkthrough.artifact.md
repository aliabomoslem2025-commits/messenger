# Walkthrough: Video Note (Telegram Style)

I have implemented the Video Note feature, allowing users to record and send circular video messages similar to Telegram.

## Changes Made

### 1. Dependencies and Infrastructure
- **[libs.versions.toml](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/gradle/libs.versions.toml)** & **[build.gradle.kts](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/build.gradle.kts)**: Added CameraX Video dependencies (`camera-video`).
- **[AndroidManifest.xml](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/AndroidManifest.xml)**: Configured `FileProvider` with `grantUriPermissions="true"` to allow secure sharing of temporary video files with the Matrix SDK.
- **[file_paths.xml](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/res/xml/file_paths.xml)**: Defined cache paths for the `FileProvider`.

### 2. Hardware Layer
- **[VideoNoteRecorder.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/voice/data/recorder/VideoNoteRecorder.kt)**: A new class leveraging CameraX to handle circular previews and video recording. It defaults to the front camera and captures in SD quality for efficiency.

### 3. Data and Protocol
- **[MatrixClientManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/MatrixClientManager.kt)**: Added `sendVideoNote` which uses the `FileProvider` to convert local file paths to URIs accessible by the SDK and handles the upload.
- **[MessageMapper.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/mapper/MessageMapper.kt)**: Updated to detect incoming video notes using the `org.matrix.msc2457.video_note` tag.

### 4. UI and UX
- **[ChatScreen.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/chat/presentation/ChatScreen.kt)**:
    - Added a circular camera preview overlay that appears when recording a video note.
    - Implemented a circular progress ring to visualize the recording duration.
- **[VideoNoteBubble.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/message/presentation/components/VideoNoteBubble.kt)**: A custom circular message bubble for displaying and playing video notes using `ExoPlayer` (Media3).
- **[MessageInput.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/chat/presentation/components/MessageInput.kt)**: Updated recording callbacks to distinguish between voice and video modes.

## Verification
- **Architecture**: Followed the provided documentation for MSC2457 compatibility and circular UI design.
- **Permissions**: Integrated Camera and Audio permission checks.
- **Resource Management**: Ensured camera and player resources are properly released when the UI is disposed.

> [!TIP]
> Users can switch between Voice and Video modes by tapping the microphone icon, and then long-press to record just like in Telegram.
