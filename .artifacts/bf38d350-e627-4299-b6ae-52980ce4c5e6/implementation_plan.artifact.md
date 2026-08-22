# Fix Compilation Errors in MatrixMessenger

This plan addresses the widespread compilation errors caused by Matrix SDK API changes, domain model refactoring, and UI/Theme naming inconsistencies.

## User Review Required

> [!IMPORTANT]
> The domain models `MatrixMessage` and `MatrixUser` have undergone significant changes that simplified their structure. I will update the mappers to match these new models, which might involve consolidating some media fields into the `attachments` list.

## Proposed Changes

### 1. Matrix SDK API Alignment

I will update all Matrix SDK related code to match version 1.6.36.

#### [MODIFY] [MatrixClientManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/MatrixClientManager.kt)
- Fix imports for `MessageContent` and `MessageType`.
- Update `joinRoom`, `leaveRoom`, `sendTextMessage`, `sendReply`, `editMessage`, `deleteMessage` to use the correct sub-services (`membershipService()`, `sendService()`, etc.).
- Fix `uploadFromUri` and other media related methods.

#### [MODIFY] [MatrixRepositoryImpl.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/repository/MatrixRepositoryImpl.kt)
- Update authentication logic to use `directAuthentication` instead of `login`.
- Fix `UserIdentifier` and `AuthenticationService` imports.
- Align all repository methods with the correct Matrix SDK sub-services.

### 2. Domain Model & Mapper Alignment

I will synchronize the mappers with the latest domain models defined in `Models.kt`.

#### [MODIFY] [MessageMapper.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/mapper/MessageMapper.kt)
- Update constructor call for `MatrixMessage` to match parameters in `Models.kt`.
- Map media fields to the `attachments` list.
- Fix `PresenceStatus` to `PresenceState`.
- Use extension functions like `hasBeenEdited()` for `TimelineEvent`.

#### [MODIFY] [UserMapper.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/mapper/UserMapper.kt)
- Update `MatrixUser` creation to match `Models.kt` (using `presence` instead of `presenceStatus` and `isOnline`).
- Map `Long` timestamps to `Date`.

#### [MODIFY] [RoomMapper.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/mapper/RoomMapper.kt)
- Fix `joinedMemberCount` to `joinedMembersCount`.

### 3. UI & Theme Alignment

I will fix the naming convention mismatches between UI components and the theme definitions.

#### [MODIFY] [CallScreen.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/call/presentation/screen/CallScreen.kt)
- Update imports from `com.matrixmessenger.core.designsystem` to `com.matrixmessenger.ui.theme`.
- Fix `MatrixTypography` and `MatrixColors` property access (e.g., `MatrixTypography.HeadlineLarge` instead of `MatrixTypography.Headline.Large`).

#### [MODIFY] [SearchScreen.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/search/presentation/screen/SearchScreen.kt)
- Fix `MatrixIcons` property access (e.g., `MatrixIcons.Back` instead of `ArrowBack`).

#### [MODIFY] [Color.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/ui/theme/Color.kt)
- Fix `MatrixTypography` invocation error.

### 4. Miscellaneous Fixes

#### [MODIFY] [AudioRecorder.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/feature/voice/data/recorder/AudioRecorder.kt)
- Fix `MediaRecorder` initialization to use standard Android API (avoiding non-existent `Builder`).

#### [MODIFY] [MatrixMotion.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/ui/theme/MatrixMotion.kt)
- Fix type inference issue in animation definitions.

## Verification Plan

### Automated Tests
- Run `gradlew :app:assembleDebug` to ensure all compilation errors are resolved.
- Run existing unit tests for mappers if available.

### Manual Verification
- Deploy to a device/emulator and verify that the app launches and basic Matrix features (login, room list, chat) work as expected.
- Verify that the theme (colors, typography) is correctly applied in the `CallScreen` and `SearchScreen`.
