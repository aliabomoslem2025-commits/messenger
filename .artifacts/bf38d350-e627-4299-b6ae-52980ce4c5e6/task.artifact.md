# Task: Fix Compilation Errors in MatrixMessenger

- `[ ]` Fix UI & Theme naming inconsistencies
    - `[ ]` Update `CallScreen.kt` (imports and theme access)
    - `[ ]` Update `SearchScreen.kt` (icon access)
    - `[ ]` Update `Color.kt` (MatrixTypography invocation)
    - `[ ]` Update `MatrixMotion.kt` (type inference)
- `[ ]` Align Domain Models and Mappers
    - `[ ]` Update `RoomMapper.kt` (`joinedMembersCount`)
    - `[ ]` Update `UserMapper.kt` (`MatrixUser` model changes)
    - `[ ]` Update `MessageMapper.kt` (`MatrixMessage` model and SDK changes)
- `[ ]` Align Matrix SDK API Usage
    - `[ ]` Fix `MatrixClientManager.kt` (imports and sub-services)
    - `[ ]` Fix `MatrixRepositoryImpl.kt` (auth logic and sub-services)
- `[ ]` Fix `AudioRecorder.kt` (`MediaRecorder` initialization)
- `[ ]` Verify build with `gradlew :app:assembleDebug`
