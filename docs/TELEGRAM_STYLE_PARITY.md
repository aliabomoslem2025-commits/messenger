# Telegram-inspired UI parity for Element Android

## Base and guardrails

- Base application: [Element Android](https://github.com/element-hq/element-android), upstream `develop` branch.
- UI and interaction reference: [Telegram Android](https://github.com/DrKLO/Telegram).
- Preserve Matrix/Element session, sync, timeline, room membership, encryption, pagination, media, and notification behavior. UI work must not replace the Matrix data path with fabricated Telegram-style state.
- Do not copy Telegram source files or assets into this repository. Element Android is licensed under AGPL-3.0 and Telegram's repository is GPL-2.0; combining source code requires a careful license-compatibility review. Reimplement desired interactions independently and preserve applicable notices for imported Element components.

## Visual and interaction acceptance checklist

- [x] Apply consistent Telegram-inspired source-level styling to room list, room header, message timeline, composer, contextual actions, and profile/details surfaces. Exact pixel parity remains a screenshot-based device acceptance gate.
- [x] Keep light, dark, and black themes coherent; use the project green accent #0DBD8B and separate chat and list/profile surface tokens without globally recoloring unrelated Element screens.
- [x] Preserve Element's reply/forward/edit/delete actions, reaction picker and callbacks, attachment previews, voice recording/playback states, and message-send/error models wherever Matrix provides equivalent behavior.
- [x] Add selected-message feedback, reaction feedback, compact list/bubble geometry, and list/action transitions that respect reduced-motion settings.
- [x] Apply per-message text direction for Persian/Arabic content in an LTR app while preserving sender-based placement of the outer bubble.
- [x] Preserve Matrix timelines, event sending, sync, room membership, permissions, encryption, pagination, and media handling. The UI does not fabricate successful sends or suppress SDK errors.
- [x] Retain Element accessibility semantics and improve reaction/menu feedback. TalkBack and target-size review remain runtime acceptance tests.
- [ ] Verify visual parity with same-device, same-density, same-font-scale screenshots of Telegram Android and this app across list, chat, profile, actions, and media screens.

## Implementation order

1. Audit the current room list, timeline item, composer, reactions, theme tokens, and media/voice message implementations.
2. Record the exact Element files and current behavior before changing them.
3. Introduce shared theme tokens and icon mapping; verify light and dark modes.
4. Adapt the room list and chat header, then message bubbles and composer.
5. Implement actions and reaction motion against existing Matrix event APIs.
6. Validate RTL/mixed-script rendering and accessibility.
7. Run relevant Gradle unit tests, lint, and app compile tasks; do not mark any item complete without evidence.

## Status

Checked items record source-level implementation, not proof of 100% pixel parity. Device screenshots, manual UI checks, and Matrix end-to-end acceptance are tracked separately below.

## Implementation progress

### Landed in the first UI pass (CI-verified on main; device acceptance pending)

- Updated the existing light/dark message-bubble palette to use a neutral inbound surface and soft-green outbound surface.
- Changed the message composer outline to a more pill-shaped radius while retaining the current theme-aware stroke.
- Made the room-list rows more compact with 54dp avatars and single-line previews; room ordering, unread state, typing state, and Matrix room actions remain in the existing Element implementation.
- Added short add/remove/move transitions for timeline and room-list items. Change cross-fades are disabled in the timeline animator to reduce flicker during receipt, reaction, and send-state updates.
- Added a brief scale bounce for reaction selection and reset the emoji scale when the view is rebound, retaining the existing Matrix reaction callbacks.
- Updated the repository README to document the customized Element base and the independent Telegram-inspired direction.


### Follow-up UI changes merged (CI-verified on main; device acceptance pending)

- Enabled grouped message bubbles by default for fresh installs; the existing preference remains available to users. Increased the grouped-bubble radius to 16dp and message timestamps to 11sp.
- Refined reaction-chip padding and added selection/deselection bounce motion that respects Android's disabled animator setting.
- Connected the existing timeline row animator and applied short message-action-list transitions; system reduced-motion settings disable those animations.
- Tightened chat-header title typography and enlarged the shared send-action circle from 36dp to 40dp without changing text-message or voice-message send callbacks.
- Added a dedicated chat canvas token: white in light mode, deep blue-gray (#0E1621) in dark mode, and pure black in black mode. It is scoped to the chat screen and expanded plain-text composer rather than changing unrelated screens.
- Kept the Gradle Wrapper on 8.13, using the official distribution checksum, rather than accepting the imported upstream wrapper's 8.14.3 version.


### RTL and reaction polish (merged and CI-verified on main; device acceptance pending)

- Resolve the actual message text view's paragraph direction from the first strong character before measuring it. This is intended to improve Persian/Arabic alignment in an LTR app while keeping the outer message bubble's sender-based placement.
- Keep reaction selected/activated view states in sync with the current checked state, add system-mediated light haptic feedback on tap, and retain existing Matrix reaction callbacks and selection animation.
- Use the messenger green accent (`#0DBD8B`) for selected-reaction surfaces across light, dark, and black themes.
- Replace square internal corners between adjacent same-sender bubbles with a 4dp soft radius while preserving the existing 16dp outer corner and RTL-aware grouping logic.
- Reduced the room-list row's vertical padding to target Telegram's compact ~70dp row rhythm, and inset dividers to begin at the title/content column rather than under the avatar.
- Unified the rich-text send-circle's rendered size with the plain composer at 40dp.
- Added a pinned-action Android CI workflow on the repository default branch to assemble both GPlay and F-Droid debug variants for pull requests. Its result is the compile-validation gate; it does not replace device-level UI and Matrix-flow tests.
- These changes were committed on `telegram-ui-rtl-reaction-polish`; no device-level test has been run from this editing session.


### Additional room-list, quick-reaction, and motion-accessibility pass (CI-verified on main; device acceptance pending)

- Multi-select now highlights the whole room row as well as the avatar, while preserving the existing room click and Matrix selection callbacks.
- Quick reactions in the message-action sheet now have a visible selected chip state, adequate minimum touch-area sizing, emoji content descriptions, tap haptic feedback, and a short selection scale response when system animations are enabled. The selected chip is no longer reduced to 20% opacity.
- The composer send-button fade now follows the system animation setting and resets alpha when hidden, preventing a recycled/invisible send button from retaining a transparent state.
- The current main head has passed the Android UI validation workflow; the run is linked in the automated verification section below.


### Chat header and selection clarity pass (CI-verified on main; device acceptance pending)

- Direct-message headers now show a localized Matrix presence label under the room title when presence data is available, while non-direct rooms and unavailable presence keep the subtitle hidden.
- The small presence icon is excluded from accessibility traversal because the same presence state is presented as readable subtitle text; this avoids announcing the state twice.
- The selected room-row overlay uses constraints on all four edges so it follows the measured row height instead of relying on `match_parent` inside a wrapping ConstraintLayout.


### RecyclerView reuse and motion-preference audit (CI-verified on main; device acceptance pending)

- The dedicated room-list screen now disables row animations when Android's system animator scale is zero, matching the home room list and timeline.
- Room-list rows explicitly hide typing and timestamp views in filtered mode and restore timestamp visibility when returning to the normal list, preventing stale recycled-row content.
- Reaction-pill scale feedback now uses Element's system animation-scale helper on all supported Android versions (rather than only checking the API-26 animator flag); haptic confirmation remains available when motion is disabled.


### Menu and profile action feedback pass (CI-verified on main; device acceptance pending)

- Generic message/context bottom-sheet actions and profile action rows now provide light keyboard-tap haptic feedback on activation. Their existing debounced click listeners, selection state, and Matrix action callbacks are retained.


### Final action-sheet visual audit (CI-verified on main; device acceptance pending)

- Quick-reaction sheets now hide unused reaction slots when the configured reaction list has fewer than eight entries, preventing blank clickable chips after Epoxy view reuse.
- Standard context-menu labels use the primary content color for readable Telegram-like menu hierarchy, while icons remain secondary and destructive actions retain the theme's error color.


### Selected-reaction palette consistency follow-up

- Corrected the selected-reaction chip colors for light, dark, and black themes. The previous values were still translucent Telegram blue despite the resource comment and project checklist specifying the messenger green accent `#0DBD8B`.
- The revised Android ARGB values use a 12.5% green overlay in light mode (`#200DBD8B`) and a 25% overlay in dark/black mode (`#400DBD8B`), preserving the existing neutral unselected chips and theme-specific contrast.
- This is a presentation-only resource correction: reaction selection, event sending, and Matrix callbacks are unchanged. A real-device screenshot/contrast check remains required.

### Black-theme bubble color follow-up (merged and CI-verified; device screenshot pending)

- Explicit black-theme inbound and outbound bubble tokens now override inherited dark-theme bubble colors. The chat canvas remains pure black while incoming/outgoing bubbles keep subtly distinct surfaces.
- This is a source-level theme correction; verify it visually with a real black-theme screenshot before treating theme parity as accepted.

### Latest automated verification on main (2026-10-19 UTC)

Validated source revision: [f8fd8a2](https://github.com/aliabomoslem2025-commits/messenger/commit/f8fd8a29bf06ac15add3496938b58c30b1d3d357).

- [Android UI validation run](https://github.com/aliabomoslem2025-commits/messenger/actions/runs/39201176410): **passed**. Both assembleGplayDebug and assembleFdroidDebug completed successfully.
- [Android UI quality run](https://github.com/aliabomoslem2025-commits/messenger/actions/runs/39201176317): **passed**. Both GPlay and F-Droid debug unit-test tasks and Android lint tasks completed successfully.
- The checked-in Gradle Wrapper remains **8.13** and these workflows use **JDK 21**.
- These results prove automated build/unit-test/lint success for that commit. They do **not** prove visual pixel parity, emulator/device behavior, live Matrix sync, message send/retry, cross-client reaction sync, encrypted-media playback, or TalkBack behavior.

### Screenshot-driven chat and creation pass (implementation committed; CI/device acceptance pending)

- Replaced the flat light chat canvas with a very low-contrast, original doodle wallpaper vector behind the timeline. The wallpaper is scoped to the timeline surface and has theme-aware light/dark/black line colors.
- Matched outgoing text bubbles to Telegram's blue surface. Message-body text and timestamps inside outgoing bubbles now use a white text token; incoming content still uses the normal theme text color. The change is applied by the message-bubble renderer rather than globally.
- Rounded the chat toolbar and composer, placed the attachment action inside the composer capsule, and added a send-button pop/collapse transition that resets correctly when animations are disabled or interrupted.
- Reaction count labels now bounce briefly when the underlying Matrix reaction count changes; the existing reaction selection animation, haptics, event callbacks, and SDK add/remove operations remain intact.
- Added a real per-room local wallpaper choice (doodles/plain) stored in app preferences. This setting is local to the current account/device and does not pretend to be a Matrix-synced room state.
- Expanded the chat overflow with Matrix-backed mute notifications, search, user/room details, invite, share-my-Matrix-ID via the Android share sheet, per-room wallpaper selection, and a confirmed leave-room action labelled “Delete Chat”. “Clear history” is deliberately not exposed as if it were universally available: a Matrix client cannot reliably erase every participant’s room history with one generic action.
- Added a four-tab floating home navigation wired to the real Matrix chat list/filter, device contacts book, settings, and signed-in user profile flows.
- “New Group” now selects multiple users first, then creates a private Matrix room and invites the selected Matrix users/third-party identifiers in the actual create-room request. “New Channel” uses a public Matrix room flow. Matrix does not define a separate Telegram channel room type, so the implementation is a public room and still requires a valid room alias.
- These are source-level changes. Automated Gradle build/test/lint and device checks must pass before treating the changes as accepted; screenshot resemblance and action behavior are not claimed as 100% until verified on device.

### Main chat-list parity pass (new branch; Android CI/device acceptance pending)

- The home screen toolbar no longer displays the Element-style hamburger beside the title. The title stays as “Telegram”; drawer access remains available from the overflow menu.
- The developer overlay is hidden on the home surface so it cannot intrude into the screenshot-matched header.
- The home search field is now a compact 40dp pill with a smaller radius and matching side margins. Folder filters use a separate rounded capsule with a distinct selected pill; the default folder label is “All Chats”.
- The default saved home display mode and the Chats tab now show all joined Matrix rooms rather than People-only rooms. Filtering and search still act on the existing Matrix room query/state.
- The home list uses a single chronological stream instead of a duplicate horizontal recent-chat carousel. Home list rows hide message previews and separators for the supplied visual reference, while other room-list contexts retain normal previews. Avatar/title/date/unread/typing states remain sourced from the real room summary.
- The lower navigation was tightened and made text-led with the account avatar kept in Profile, while the camera shortcut and new-message action are separate floating buttons. The camera shortcut opens the native camera; it does not claim to publish a Telegram Story or create a fake Matrix upload.
- These are source changes on `telegram-home-screen-parity-2026-10`. The new PR must pass Android assembly, unit tests and lint, followed by on-device comparison. The previous main-branch CI result listed above does not validate this newer commit.

### Remaining acceptance gates before claiming 100% Telegram parity

- [ ] Capture side-by-side screenshots of running Telegram and Messenger at the same device, density, font scale, theme, and content for room list, chat header, grouped messages, replies/edits/forwards, reactions, context menus, composer/recording, media, and profile screens; fix the differences revealed by those screenshots.
- [x] Keep both GPlay and F-Droid debug assembly, unit-test, and lint workflows green on the final head: all jobs passed on f8fd8a29bf06ac15add3496938b58c30b1d3d357 (see the run links below).
- [ ] Test Matrix send success/failure/retry, reaction add/remove and cross-client sync, encrypted media download/decryption, voice recording/cancel/send/playback, room membership, and pagination on a device/emulator.
- [ ] Verify Persian-only, Latin-only, mixed-script, URL, timestamp, multiline, edited-message, and media-caption rendering at multiple font scales with TalkBack enabled.

Remote source editing cannot substitute for screenshot comparison or hands-on Matrix acceptance. Do not describe this as 100% parity until these gates pass.
