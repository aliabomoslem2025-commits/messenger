# Ultimate Messenger Evolution Plan

## Target Vision
Transform this Matrix messenger into a **next-generation premium messenger** that competes with:
- **Telegram** (feature richness, animations, performance)
- **Element X** (modern Compose architecture, Matrix integration)
- **WhatsApp/Signal** (reliability, simplicity)

---

## PHASE 1: CRITICAL TODO/FIXME RESOLUTION (Priority: HIGHEST)

### 1.1 Fix All Mapper TODOs

**Files to Update:**
- `data/matrix/mapper/UserMapper.kt` - Extract presence info
- `data/matrix/mapper/RoomMapper.kt` - Implement pinned/muted state detection
- `data/matrix/mapper/MessageMapper.kt` - Implement forwarded, poll, redaction, self-destruct

**Implementation:**
```kotlin
// UserMapper: Extract presence from Matrix presence API
// RoomMapper: Check room tags for pinned, check push rules for muted
// MessageMapper: 
//   - Check event relations for forwarded
//   - Implement poll content type
//   - Extract unsigned.redacts_reason
//   - Implement self-destruct from MSC
```

---

## PHASE 2: CHAT SCREEN REDESIGN (Priority: HIGH)

### 2.1 Message Bubble System

**Create:** `ui/components/message/MessageBubble.kt`

**Requirements:**
- Incoming/outgoing styling using MatrixColors
- Message grouping (First/Middle/Last/Single)
- Proper corner radii using MatrixShapes
- Metadata (timestamp, read receipts, edited label)
- Reply preview
- Reaction display

**Sub-components:**
- `MessageAvatar.kt`
- `MessageMetadata.kt`
- `MessageTimestamp.kt`
- `ReadReceiptIndicator.kt`
- `EditedLabel.kt`
- `ReplyPreview.kt`
- `ReactionList.kt`

### 2.2 Message Content Types

**Create separate composables for each type:**

1. `TextMessage.kt` - Markdown support, mentions, spoilers
2. `ImageMessage.kt` - With zoom capability
3. `VideoMessage.kt` - Inline player with ExoPlayer
4. `VoiceMessage.kt` - Waveform visualization, playback controls
5. `FileMessage.kt` - Download progress, file icon
6. `StickerMessage.kt` - Animated sticker support
7. `GifMessage.kt` - Giphy integration
8. `LocationMessage.kt` - Map preview
9. `ContactMessage.kt` - Contact card
10. `PollMessage.kt` - Interactive poll

### 2.3 Chat Header Redesign

**Update:** `ui/screens/chat/ChatScreen.kt` header

**Add:**
- User status indicator (online/offline/typing)
- Call buttons (audio/video) with proper integration
- Search in chat
- More options menu

### 2.4 Message Input Redesign

**Create:** `ui/components/input/MessageInput.kt`

**Features:**
- Animated send/mic button transition
- Reply bar
- Edit bar
- Attachment picker
- Emoji panel trigger
- Mention autocomplete
- Voice recording UI with amplitude visualization

---

## PHASE 3: MEDIA SYSTEM (Priority: HIGH)

### 3.1 Media Viewer

**Create:** `feature/media/presentation/MediaViewerScreen.kt`

**Requirements:**
- Fullscreen image with pinch-to-zoom
- Swipe navigation between media
- Shared element transitions
- Video playback with ExoPlayer
- Download button
- Share button
- Forward option
- Delete option

**Libraries to Evaluate:**
- Coil for image loading
- Accompanist permissions
- Custom Canvas zoom or compose-zoomable library

### 3.2 Image Loading Strategy

**Implement:**
- Progressive JPEG loading
- Thumbnail → Blur → Full resolution
- Memory cache configuration
- Disk cache strategy
- Size constraints for LazyColumn items

### 3.3 Video Player

**Create:** `ui/components/media/VideoPlayer.kt`

**Features:**
- ExoPlayer integration
- Play/pause toggle
- Seek bar
- Playback speed control
- Picture-in-picture support
- Hardware acceleration

### 3.4 Voice Message Player

**Create:** `ui/components/media/VoiceMessagePlayer.kt`

**Features:**
- Waveform visualization
- Play/pause
- Seek
- Speed control (1x, 1.5x, 2x)
- Progress animation
- Background playback support

---

## PHASE 4: CALL SYSTEM COMPLETION (Priority: CRITICAL)

### 4.1 WebRTC Integration

**Create:** `core/webrtc/WebRtcManager.kt` (already exists, needs completion)

**Requirements:**
- PeerConnection management
- ICE candidate exchange
- Audio track handling
- Video track handling
- Camera enumeration
- Microphone selection
- TURN/STUN configuration

### 4.2 Matrix VoIP Signaling

**Update:** `feature/call/data/repository/CallRepositoryImpl.kt`

**Implement:**
- Send call invite (m.call.invite)
- Handle incoming call events
- Send call answer (m.call.answer)
- Send ICE candidates (m.call.candidates)
- Send hangup (m.call.hangup)
- Call state machine

### 4.3 Call Screens

**Enhance existing:** `feature/call/presentation/screen/CallScreen.kt`

**Add:**
- IncomingCallScreen with accept/reject actions
- OutgoingCallScreen with cancel option
- VideoCallScreen with local/remote video
- CallControls component (already partially done)
- AudioVisualizer component
- Connection quality indicator

### 4.4 Call Notifications

**Create:** `feature/call/data/service/CallNotificationService.kt`

**Requirements:**
- Full-screen intent notification
- Accept/Reject actions
- Ongoing call notification
- Background service for active calls
- Lock screen visibility

### 4.5 Call History

**Create Database:**
- `CallHistoryEntity`
- `CallHistoryDao`
- Repository + UseCase layer
- `CallHistoryScreen` UI

---

## PHASE 5: SEARCH SYSTEM (Priority: MEDIUM)

### 5.1 Global Search

**Enhance:** `feature/search/presentation/screen/SearchScreen.kt`

**Integrate with:**
- Matrix search API (/search endpoint)
- Local database FTS (Full Text Search)
- Recent searches storage
- Search suggestions

### 5.2 In-Chat Search

**Create:** `ui/components/search/InChatSearchBar.kt`

**Features:**
- Search within current room
- Highlight matches
- Navigate between results
- Close button

### 5.3 Search Results

**Create components:**
- `SearchResultItem.kt` - Unified result display
- `MessageResultHighlight.kt` - Highlighted text
- `UserResultItem.kt`
- `RoomResultItem.kt`

---

## PHASE 6: MESSAGE INTERACTIONS (Priority: MEDIUM)

### 6.1 Reactions System

**Create:**
- `ui/components/reaction/ReactionPicker.kt`
- `ui/components/reaction/ReactionList.kt`
- `domain/usecase/SendReactionUseCase.kt`

**Features:**
- Emoji picker popup
- Quick reaction bar
- Animated reaction addition
- Counter animation
- Custom emoji support

### 6.2 Message Selection

**Create:**
- Selection state management
- Multi-select mode
- Action toolbar (copy, forward, delete, reply)
- Animated selection indicators

### 6.3 Message Forwarding

**Create:**
- `domain/usecase/ForwardMessageUseCase.kt`
- `ui/components/forward/ForwardDialog.kt`
- Room selector
- Forward with/without caption

### 6.4 Context Menu

**Create:** `ui/components/menu/MessageContextMenu.kt`

**Options:**
- Reply
- Edit (if own message)
- Copy text
- Forward
- React
- Select
- Delete (if own or admin)
- Pin
- View details

---

## PHASE 7: STICKER & GIF SYSTEM (Priority: MEDIUM)

### 7.1 Sticker Packs

**Create:**
- Sticker pack repository
- Pack installation
- Favorites system
- Recently used tracking

### 7.2 Sticker Picker

**Create:** `ui/components/sticker/StickerPickerPanel.kt`

**Tabs:**
- Recently used
- Favorites
- Installed packs
- Trending (if available)

### 7.3 Animated Stickers

**Implement:**
- Lottie integration
- Animation lifecycle management
- Pause when not visible
- Emotion-based animations (from Telegram analysis)

### 7.4 GIF Integration

**Create:**
- Giphy API integration or alternative
- GIF picker
- Search functionality
- Trending GIFs

---

## PHASE 8: ADVANCED FEATURES (Priority: LOW-MEDIUM)

### 8.1 Threads

**Create:**
- Thread creation from message
- Thread view screen
- Thread notifications
- Thread counter in main chat

### 8.2 Polls

**Create:**
- Poll creation UI
- Interactive poll display
- Vote handling
- Results visualization

### 8.3 Spoilers

**Implement:**
- Spoiler rendering with blur
- Tap to reveal animation
- MSC compliance

### 8.4 Code Blocks

**Implement:**
- Syntax highlighting
- Copy button
- Monospace font

---

## PHASE 9: ANIMATION INTEGRATION (Priority: HIGH)

### 9.1 Integrate Animation Engine

**Apply to all screens:**

**Message Animations:**
- Enter animation (scale + alpha + translation)
- Send animation (button morph → message travel)
- Delete animation (particle effect)
- Edit animation (subtle pulse)

**UI Animations:**
- Typing indicator (sequential dots)
- Send button morph (mic ↔ send)
- Reaction counter (rolling numbers)
- Unread badge (scale pulse)

**Transition Animations:**
- Chat list → Chat screen (shared element)
- Media viewer open/close (expand/collapse)
- Screen navigation (fade + slide)

### 9.2 Sticker Emotions

**Implement from Telegram analysis:**
- HAPPY - gentle bounce
- LAUGH - rotation shake
- CRY - tear particles
- LOVE - floating hearts
- ANGRY - vibration + flash
- SURPRISE - zoom + explosion
- FEAR - shrinking + shake

---

## PHASE 10: DESIGN SYSTEM INTEGRATION (Priority: HIGH)

### 10.1 Refactor All Screens

**Replace Material3 defaults with design tokens:**

**HomeScreen:**
```kotlin
// Change from:
MaterialTheme.colorScheme.surface
// To:
MatrixColors.Surface.Primary
```

**All Cards:**
- Use MatrixShapes.Card
- Use MatrixColors.Surface
- Apply MatrixDimens spacing

**All Text:**
- Use MatrixTypography styles
- Use MatrixColors.Text.*

### 10.2 Create Reusable Components

**Build component library:**
- `MatrixCard.kt`
- `MatrixSurface.kt`
- `MatrixDivider.kt`
- `MatrixAvatar.kt`
- `MatrixBadge.kt`
- `MatrixButton.kt`
- `MatrixIconButton.kt`
- `MatrixTopBar.kt`
- `MatrixBottomBar.kt`
- `MatrixChip.kt`
- `MatrixSkeleton.kt`

---

## PHASE 11: BACKGROUND SERVICES (Priority: HIGH)

### 11.1 Sync Worker

**Create:** `core/sync/MessageSyncWorker.kt`

**Features:**
- WorkManager integration
- Network constraints
- Periodic sync
- Initial sync on app start
- Incremental sync optimization

### 11.2 Call Service

**Create:** `feature/call/data/service/CallService.kt`

**Features:**
- Foreground service for active calls
- Notification updates
- Media session integration
- Bluetooth headset support

### 11.3 Media Service

**Create:** `feature/media/data/service/MediaTransferService.kt`

**Features:**
- Upload queue management
- Download queue management
- Progress notifications
- Pause/resume capability

---

## PHASE 12: NOTIFICATIONS (Priority: HIGH)

### 12.1 Notification Channels

**Configure:**
- Messages channel
- Calls channel
- Mentions channel
- Groups channel
- Silent channel

### 12.2 Message Notifications

**Features:**
- Rich notification with avatar
- Direct reply action
- Mark as read action
- Deep link to chat
- Grouped notifications

### 12.3 Call Notifications

**Features:**
- Full-screen intent
- Accept/Reject actions
- Ongoing call notification
- Video call indicator

---

## PHASE 13: SETTINGS COMPLETION (Priority: MEDIUM)

### 13.1 Settings Categories

**Create separate screens:**

1. `AppearanceSettingsScreen.kt`
   - Theme toggle (Light/Dark/System)
   - Chat background
   - Message text size
   - Animation toggle

2. `NotificationSettingsScreen.kt`
   - Sound selection
   - Vibration patterns
   - LED color
   - Popup notifications
   - Mute options

3. `PrivacySettingsScreen.kt`
   - Last seen visibility
   - Profile photo visibility
   - About visibility
   - Blocked users
   - Read receipts toggle

4. `SecuritySettingsScreen.kt`
   - 2FA setup
   - Active sessions list
   - Encryption settings
   - Secure backup

5. `StorageSettingsScreen.kt`
   - Cache size
   - Data usage
   - Auto-download rules
   - Clear cache button

6. `AccountSettingsScreen.kt`
   - Display name edit
   - Avatar change
   - Username edit
   - Password change
   - Email/phone binding

7. `HelpSettingsScreen.kt`
   - FAQ
   - Contact support
   - Report bug
   - App version

---

## PHASE 14: PROFILE ENHANCEMENT (Priority: MEDIUM)

### 14.1 Profile Editing

**Add to ProfileScreen:**
- Edit mode toggle
- Display name form
- Avatar picker with camera/gallery
- Bio/status editor
- Save/Cancel actions

### 14.2 Shared Media

**Create:** `ui/components/profile/SharedMediaGrid.kt`

**Features:**
- Photo grid
- Video section
- File section
- Links section
- Load more pagination

### 14.3 Profile Actions

**Add:**
- Message user button
- Call user button
- Video call button
- Block/unblock
- Clear history
- Notification settings
- Add to favorites

---

## PHASE 15: ACCESSIBILITY (Priority: MEDIUM)

### 15.1 Content Descriptions

**Add to all:**
- Icons
- Images
- Buttons
- Interactive elements

### 15.2 Semantic Structure

**Ensure:**
- Proper heading hierarchy
- Logical reading order
- Group related elements

### 15.3 Touch Targets

**Verify:**
- Minimum 48dp touch targets
- Adequate spacing between interactive elements

### 15.4 Testing

**Test with:**
- TalkBack
- Large fonts
- High contrast
- Switch access

---

## PHASE 16: RTL SUPPORT (Priority: MEDIUM)

### 16.1 Layout Mirroring

**Configure:**
- Enable RTL in manifest
- Test all screens in RTL
- Fix any broken layouts

### 16.2 Directional Adjustments

**Handle:**
- Message alignment (incoming/outgoing swap)
- Icon directionality
- Navigation arrows
- Timestamp position

---

## PHASE 17: RESPONSIVE DESIGN (Priority: LOW)

### 17.1 Tablet Layout

**Implement:**
- Two-pane layout (chat list + chat)
- Three-pane for very large screens
- Adaptive navigation

### 17.2 Landscape Mode

**Optimize:**
- Chat screen layout
- Media viewer
- Call screen

### 17.3 Foldables

**Support:**
- State restoration on hinge movement
- Spanning across displays

---

## PHASE 18: PERFORMANCE OPTIMIZATION (Priority: HIGH)

### 18.1 Compose Optimization

**Apply:**
- Stable data classes
- remember where appropriate
- derivedStateOf for expensive calculations
- LaunchedEffect for side effects
- DisposableEffect for cleanup

### 18.2 Lazy List Optimization

**Ensure:**
- Stable keys (not indices)
- Correct item count
- No expensive operations in item composition
- Placeholder while loading

### 18.3 Image Optimization

**Configure:**
- Coil memory cache size
- Disk cache strategy
- Size constraints
- Subsampling for large images

### 18.4 Memory Management

**Profile:**
- Leak detection with LeakCanary
- Bitmap recycling
- Coroutine cancellation
- Flow collection lifecycle

---

## PHASE 19: TESTING (Priority: HIGH)

### 19.1 Unit Tests

**Create tests for:**
- All UseCases
- All Repositories
- All ViewModels
- Utility functions

### 19.2 UI Tests

**Create tests for:**
- Login flow
- Chat list display
- Message sending
- Call initiation
- Search functionality
- Settings changes

### 19.3 Integration Tests

**Test:**
- End-to-end messaging
- Call flows
- Media upload/download
- Offline scenarios

---

## PHASE 20: FINAL POLISH (Priority: MEDIUM)

### 20.1 Visual Audit

**Compare against:**
- Telegram Android reference
- Element X reference
- Behance design reference

**Fix:**
- Spacing inconsistencies
- Color mismatches
- Animation timing issues
- Typography problems

### 20.2 Documentation

**Create:**
- README with features
- Architecture documentation
- Contribution guidelines
- API documentation

### 20.3 Release Preparation

**Configure:**
- ProGuard rules
- Release signing
- Version naming
- Changelog
- Store listing assets

---

## IMPLEMENTATION ORDER

Execute in this exact sequence:

1. **Week 1-2:** Phase 1 (TODO fixes) + Phase 2 (Chat redesign foundation)
2. **Week 3-4:** Phase 2 (Message types) + Phase 10 (Design system integration)
3. **Week 5-6:** Phase 3 (Media system) + Phase 9 (Animation integration)
4. **Week 7-8:** Phase 4 (Call system completion) 🔥 CRITICAL
5. **Week 9:** Phase 5 (Search) + Phase 6 (Message interactions)
6. **Week 10:** Phase 7 (Stickers/GIFs) + Phase 8 (Advanced features)
7. **Week 11:** Phase 11 (Background services) + Phase 12 (Notifications)
8. **Week 12:** Phase 13 (Settings) + Phase 14 (Profile)
9. **Week 13:** Phase 15 (Accessibility) + Phase 16 (RTL) + Phase 17 (Responsive)
10. **Week 14:** Phase 18 (Performance) + Phase 19 (Testing)
11. **Week 15:** Phase 20 (Final polish) + Bug fixes

---

## SUCCESS CRITERIA

The project is complete when:

✅ **Zero TODOs** remain in codebase
✅ **All features** from Telegram/Element X comparison matrix are implemented
✅ **Design system** is used consistently across all screens
✅ **Animations** are smooth and professional (60 FPS)
✅ **Calls** work end-to-end (audio + video)
✅ **Media** viewing is smooth with proper caching
✅ **Search** returns relevant results quickly
✅ **Tests** cover critical paths (>70% coverage)
✅ **Performance** is optimal (no jank, low memory)
✅ **Accessibility** passes basic requirements
✅ **RTL** works correctly
✅ **Build** succeeds with no errors
✅ **App** feels premium and polished

---

## KEY PRINCIPLES

1. **Never copy code** from Telegram/Element X - learn from them, implement better
2. **Always use design tokens** - no hardcoded values
3. **Maintain clean architecture** - UI never touches Matrix SDK directly
4. **Test incrementally** - compile after each logical unit
5. **Prioritize user experience** - animations should feel natural, not flashy
6. **Respect Matrix protocol** - use standard events, don't invent custom signaling
7. **Performance first** - profile regularly, optimize hot paths
8. **Accessibility matters** - build for everyone

---

*This plan will be updated as implementation progresses.*
