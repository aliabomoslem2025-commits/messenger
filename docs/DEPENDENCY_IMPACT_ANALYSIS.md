# Dependency Impact Analysis: Phase 1 Evolution

This document analyzes the impact of the architectural changes proposed for Phase 1 of the Matrix Messenger Evolution.

## 1. Affected Modules & Files

The refactor primarily impacts the `:app` module, focusing on the data flow and UI state management.

| Module | File | Responsibility | Impact Level |
| :--- | :--- | :--- | :--- |
| `:app` | `MatrixClientManager.kt` | Low-level Matrix SDK wrapper | **HIGH** |
| `:app` | `MatrixRepositoryImpl.kt` | Repository Implementation | **HIGH** |
| `:app` | `ChatViewModel.kt` | UI State & Logic for Chats | **MEDIUM** |
| `:app` | `ChatScreen.kt` | UI Rendering for Chats | **MEDIUM** |
| `:app` | `AudioRecorder.kt` | Voice recording utility | **LOW** |

## 2. Impact on ViewModels

### `ChatViewModel`
- **Logic Change**: The ViewModel will no longer observe a repository-managed map. It will directly observe a flow representing the live Room timeline.
- **New State**: Integration of `VoiceRecorderState` (sealed class: `Idle`, `Recording`, etc.).
- **New Action**: `loadMore()` will be added to trigger back-pagination via the Repository.

### `HomeViewModel`
- **Minimal Impact**: While the underlying `MatrixRepository` room loading logic will be cleaned up, the `observeRooms()` interface will remain stable.

## 3. Impact on UI Components

### `ChatScreen`
- **Data Rendering**: The `reversed()` logic will be removed from the `items` block. The data source will provide messages in the correct order for the `reverseLayout = true` property of `LazyColumn`.
- **Pagination UI**: New triggers for back-pagination (e.g., reaching the top of the list).
- **Recording UI**: A new "hold-to-record" interaction layer will be added, reacting to the ViewModel's recorder state.

## 4. Migration Risks & Mitigation

| Risk | Description | Mitigation Strategy |
| :--- | :--- | :--- |
| **Data Divergence** | Potential for the UI to show stale data if the repository's old `_messagesMap` is removed incorrectly. | Establish `MatrixClientManager`'s `Timeline` as the **Single Source of Truth** immediately. Remove all intermediate maps. |
| **Memory Leaks** | Matrix SDK `Timeline` objects must be disposed of. `callbackFlow` might leak if not closed properly. | Use `awaitClose { timeline.dispose() }` in `MatrixClientManager.getTimelineEventFlow` and ensure ViewModels manage their job lifecycles correctly. |
| **Scroll Jumping** | Loading older messages can cause the scroll position to jump in `LazyColumn`. | Use unique `eventId` keys for list items and implement `maintainScrollPosition` logic using `LazyListState`. |
| **State Bloat** | Adding complex recorder states to `ChatViewModel` might make it harder to maintain. | Consider extracting recording logic into a dedicated `VoiceMessageManager` or a separate `RecorderViewModel` if the state becomes too complex. |

## 5. Architectural Constraint Compliance

- **SSOT**: The refactor explicitly removes `_messagesMap` from `MatrixRepositoryImpl`, routing all data directly from the SDK's Timeline.
- **Ordered Data**: The Repository/Mapper will be updated to provide messages in descending order (newest first) to align with the UI's `reverseLayout`.
- **Real Pagination**: `MatrixClientManager` will be updated to expose the `paginate` functionality of the Matrix SDK.
- **Recorder State Machine**: `ChatViewModel` will adopt a formal state machine for the recording flow.
- **No Duplicate Storage**: Room database will not be used for message caching, adhering to the constraint.

---

> [!IMPORTANT]
> This refactor simplifies the data flow but increases the reliance on the Matrix SDK's internal lifecycle management. Rigorous testing of session restoration and sync states is required.
