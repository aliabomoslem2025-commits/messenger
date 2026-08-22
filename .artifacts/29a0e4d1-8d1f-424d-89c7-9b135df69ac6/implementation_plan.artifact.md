# Implementation Plan - Fix Unresolved References in MatrixClientManager

The goal is to fix compilation errors in `MatrixClientManager.kt` related to incorrect usage of Matrix SDK 2 APIs.

## User Review Required

> [!IMPORTANT]
> The `searchMessages` function in `MatrixClientManager` was changed to require a `roomId`. Previously it was optional (`String? = null`), but the Matrix SDK 2 `SearchService` requires a non-nullable `roomId`. Global search (across all rooms) is not directly supported by this SDK method.

## Proposed Changes

### Matrix Data Layer

#### [MODIFY] [MatrixClientManager.kt](file:///home/phantom/AndroidStudioProjects/Messenger2/messenger2/app/src/main/java/com/matrixmessenger/data/matrix/MatrixClientManager.kt)

1.  **Fix `pinMessage` and `unpinMessage`**:
    - Replace `QueryStateEventValue.Any` with `QueryStringValue.Equals("")`. Matrix state events for room settings typically use an empty string as the state key.
2.  **Fix `searchMessages`**:
    - Change `roomId: String? = null` to `roomId: String` to match the SDK's `SearchService.search` signature.
3.  **Fix `enableEncryption`**:
    - Change `requireRoom(roomId).stateService().enableEncryption()` to `requireRoom(roomId).roomCryptoService().enableEncryption()`. The encryption management API is located in `RoomCryptoService`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify that the unresolved reference errors are resolved.

### Manual Verification
- N/A (Build fix)
