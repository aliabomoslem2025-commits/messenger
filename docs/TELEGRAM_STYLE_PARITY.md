# Telegram-inspired UI parity for Element Android

## Base and guardrails

- Base application: [Element Android](https://github.com/element-hq/element-android), upstream `develop` branch.
- UI and interaction reference: [Telegram Android](https://github.com/DrKLO/Telegram).
- Preserve Matrix/Element session, sync, timeline, room membership, encryption, pagination, media, and notification behavior. UI work must not replace the Matrix data path with fabricated Telegram-style state.
- Do not copy Telegram source files or assets into this repository. Element Android is licensed under AGPL-3.0 and Telegram's repository is GPL-2.0; combining source code requires a careful license-compatibility review. Reimplement desired interactions independently and preserve applicable notices for imported Element components.

## Visual and interaction acceptance checklist

- [ ] Bring a consistent Telegram-inspired layout to room list, room header, message timeline, composer, context menus, and profile/details screens.
- [ ] Use cohesive light/dark themes and a configurable accent; project preference is green `#0DBD8B`, without the default purple look.
- [ ] Match familiar chat affordances: long-press actions, reply/forward/edit/delete, reaction picker and updates, attachment previews, voice-message recording/playback states, and message status when Element/Matrix provides equivalent state.
- [ ] Add polished state transitions and motion for opening/closing panels, selecting messages, sending/receiving messages, and changing reactions. Respect reduced-motion and accessibility settings.
- [ ] Keep Persian/RTL text readable within message content while preserving the intended overall chat-row and navigation geometry; verify mixed Persian/Latin text, timestamps, links, and media captions.
- [ ] Reuse Element's Matrix models, timeline, event sending, permissions, encryption, and media pipeline. Never simulate successful sends or hide SDK failures.
- [ ] Preserve accessibility labels, screen-reader semantics, dynamic text size, and adequate touch targets.

## Implementation order

1. Audit the current room list, timeline item, composer, reactions, theme tokens, and media/voice message implementations.
2. Record the exact Element files and current behavior before changing them.
3. Introduce shared theme tokens and icon mapping; verify light and dark modes.
4. Adapt the room list and chat header, then message bubbles and composer.
5. Implement actions and reaction motion against existing Matrix event APIs.
6. Validate RTL/mixed-script rendering and accessibility.
7. Run relevant Gradle unit tests, lint, and app compile tasks; do not mark any item complete without evidence.

## Status

This is the acceptance checklist and implementation plan; it does not claim the UI parity items are already implemented.

## Implementation progress

### Landed in the first UI pass (not yet build-verified)

- Updated the existing light/dark message-bubble palette to use a neutral inbound surface and soft-green outbound surface.
- Changed the message composer outline to a more pill-shaped radius while retaining the current theme-aware stroke.
- Made the room-list rows more compact with 54dp avatars and single-line previews; room ordering, unread state, typing state, and Matrix room actions remain in the existing Element implementation.
- Added short add/remove/move transitions for timeline and room-list items. Change cross-fades are disabled in the timeline animator to reduce flicker during receipt, reaction, and send-state updates.
- Added a brief scale bounce for reaction selection and reset the emoji scale when the view is rebound, retaining the existing Matrix reaction callbacks.
- Updated the repository README to document the customized Element base and the independent Telegram-inspired direction.


### Follow-up UI changes merged (still not build-verified)

- Enabled grouped message bubbles by default for fresh installs; the existing preference remains available to users. Increased the grouped-bubble radius to 16dp and message timestamps to 11sp.
- Refined reaction-chip padding and added selection/deselection bounce motion that respects Android's disabled animator setting.
- Connected the existing timeline row animator and applied short message-action-list transitions; system reduced-motion settings disable those animations.
- Tightened chat-header title typography and enlarged the shared send-action circle from 36dp to 40dp without changing text-message or voice-message send callbacks.
- Added a dedicated chat canvas token: white in light mode, deep blue-gray (#0E1621) in dark mode, and pure black in black mode. It is scoped to the chat screen and expanded plain-text composer rather than changing unrelated screens.
- Kept the Gradle Wrapper on 8.13, using the official distribution checksum, rather than accepting the imported upstream wrapper's 8.14.3 version.


### Current incremental PR: RTL and reaction polish (not build-verified)

- Resolve the actual message text view's paragraph direction from the first strong character before measuring it. This is intended to improve Persian/Arabic alignment in an LTR app while keeping the outer message bubble's sender-based placement.
- Keep reaction selected/activated view states in sync with the current checked state, add system-mediated light haptic feedback on tap, and retain existing Matrix reaction callbacks and selection animation.
- Use the messenger green accent (`#0DBD8B`) for selected-reaction surfaces across light, dark, and black themes.
- Replace square internal corners between adjacent same-sender bubbles with a 4dp soft radius while preserving the existing 16dp outer corner and RTL-aware grouping logic.
- Reduced the room-list row's vertical padding to target Telegram's compact ~70dp row rhythm, and inset dividers to begin at the title/content column rather than under the avatar.
- Unified the rich-text send-circle's rendered size with the plain composer at 40dp.
- Added a pinned-action Android CI workflow on the repository default branch to assemble both GPlay and F-Droid debug variants for pull requests. Its result is the compile-validation gate; it does not replace device-level UI and Matrix-flow tests.
- These changes were committed on `telegram-ui-rtl-reaction-polish`; no device-level test has been run from this editing session.


### Additional room-list, quick-reaction, and motion-accessibility pass (not yet build-verified)

- Multi-select now highlights the whole room row as well as the avatar, while preserving the existing room click and Matrix selection callbacks.
- Quick reactions in the message-action sheet now have a visible selected chip state, adequate minimum touch-area sizing, emoji content descriptions, tap haptic feedback, and a short selection scale response when system animations are enabled. The selected chip is no longer reduced to 20% opacity.
- The composer send-button fade now follows the system animation setting and resets alpha when hidden, preventing a recycled/invisible send button from retaining a transparent state.
- The branch's Android UI validation workflow should be checked on the latest commit before merging.


### Chat header and selection clarity pass (not yet build-verified)

- Direct-message headers now show a localized Matrix presence label under the room title when presence data is available, while non-direct rooms and unavailable presence keep the subtitle hidden.
- The small presence icon is excluded from accessibility traversal because the same presence state is presented as readable subtitle text; this avoids announcing the state twice.
- The selected room-row overlay uses constraints on all four edges so it follows the measured row height instead of relying on `match_parent` inside a wrapping ConstraintLayout.


### Still required before calling this Telegram parity

- [ ] Compare the running app against current Telegram Android screens for room list, header, timeline, composer, action menu, profile, and media.
- [ ] Audit and adapt the chat header, message bubble geometry and grouping, context actions, icon mapping, and sheet/menu transitions.
- [ ] Verify reaction sync/error states, message send failure states, encrypted media, and voice-message recording/playback end to end.
- [ ] Verify Persian/RTL and mixed Persian/Latin message content independently of the app's overall layout direction.
- [ ] Run Gradle compilation, unit tests, lint, and targeted manual tests on a device/emulator.

No Gradle build or device test has been run from the remote repository editing session. The items above are source changes only and should be treated as a first pass pending compilation and runtime validation.
