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