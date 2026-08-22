# Technical Baseline Report: Matrix Messenger

This report details the current technical state of the application, identifying architectural patterns, state management strategies, and potential risks before proceeding with the evolution refactor.

## 1. Current Message Flow

The data flow from the Matrix network to the UI follows a typical Clean Architecture path but with some redundant in-memory caching:

1.  **Matrix Event**: `TimelineEvent` is received from the Matrix SDK via a `Timeline.Listener`.
2.  **Mapper**: `MatrixRepositoryImpl` has a private `TimelineEvent.toDomainMessage()` method that converts the SDK event into a `MatrixMessage` (Domain Model).
    *   *Note*: A standalone `MessageMapper` class exists but is underutilized in the primary message flow.
3.  **Repository Cache**: The `MatrixMessage` is added to an in-memory `_messagesMap` (`MutableMap<String, MutableStateFlow<List<MatrixMessage>>>`) within `MatrixRepositoryImpl`.
4.  **ViewModel**: `ChatViewModel` observes the flow from the repository and maps `MatrixMessage` to a UI-level `Message` model, which is then emitted as part of `ChatUiState`.
5.  **Compose UI**: `ChatScreen` collects the `ChatUiState` and renders the list using `LazyColumn`.

## 2. State Management Problems

- **Redundant Caching**: `MatrixRepositoryImpl` maintains its own `StateFlow` for messages in each room. Since the Matrix SDK already uses a Realm database for caching, this in-memory map is redundant and can lead to synchronization issues.
- **UI Logic in Composable**: The `ChatScreen` calls `uiState.messages.reversed()` directly inside the `items` block. This forces a list reversal on every recomposition, impacting performance.
- **Manual Room Loading**: The repository manually loads room summaries and sets up listeners instead of fully leveraging the SDK's `RoomService` and `Timeline` abstractions.

## 3. Database & Cache Strategy

- **Room Database**: An `AppDatabase` exists but is currently **unused** for message or room caching.
- **SDK Internal Cache**: The app relies on the Matrix SDK's internal Realm storage.
- **Persistence**: While the SDK persists data, the repository's `_messagesMap` is transient. Restarting the app requires the repository to re-fetch and re-map everything from the SDK.

## 4. Pagination Behavior

- **Initial Load**: Hardcoded to load the last **50 events** using `TimelineSettings(50)`.
- **Back-pagination**: **Missing**. There is no UI trigger or ViewModel logic to load older messages when the user scrolls to the top.
- **Directional Logic**: The app assumes a single-direction timeline and does not currently handle "gaps" in history.

## 5. Compose Recomposition Issues

- **List Keys**: `LazyColumn` uses `eventId` as a key, which is good.
- **Expensive Operations**: As mentioned, `reversed()` in the UI is a major issue.
- **Unstable Models**: If domain or UI models aren't marked as `@Immutable` or `@Stable`, Compose may perform unnecessary recompositions of the entire message list.
- **Avatar Loading**: `AsyncImage` is used, but without explicit sizing and crossfade optimization, it can cause "jank" during fast scrolling.

## 6. Recording Lifecycle

- **Implementation**: `AudioRecorder` provides a solid wrapper around `MediaRecorder`, handling API 31+ changes.
- **Integration**: **Incomplete**. While `MatrixClientManager` has `sendVoiceMessage`, it is not wired up to the `ChatScreen`. There is no "hold-to-record" gesture or waveform visualization in the current UI.
- **Resource Management**: The lifecycle of the `MediaRecorder` (start, stop, release) is manual and not yet tied to the ViewModel's lifecycle.

## 7. Risk Assessment

- **Refactoring Depth**: The repository needs a significant cleanup to remove redundant flows. This risks breaking the current message display.
- **Data Consistency**: Moving from manual in-memory flows to direct SDK observation might reveal bugs in how the app currently handles event IDs and local echoes.
- **UI Complexity**: Transitioning to a Telegram-style UX (Phase 5) will require high-precision gesture handling in Compose, which is difficult to get right without a solid architectural foundation.

---

> [!IMPORTANT]
> **Priority One**: Before adding new features, the message flow should be streamlined to use the Matrix SDK's `Timeline` more directly, and the `reversed()` logic must be moved out of the Composable.
