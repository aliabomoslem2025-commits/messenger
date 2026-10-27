# Telegram-style animated emoji assets

The app uses Lottie to render animation assets mapped by Unicode emoji. The checked-in
catalog is generated from the public Telemoji mirror:
https://github.com/saeedtahmtan/telemoji (a third-party mirror of Telegram-style
animation assets). At the time this catalog was added it contained 599 animated-emoji
entries, 121 reaction entries, 6 generic-animation entries and 78 dice variants.
Entries include alternate variants where available.

The Gradle build downloads the Lottie JSON files referenced by the catalog and
packages them inside the APK under `assets/telegram_emoji/lottie/...`. The first send
uses a local composition, starts without an asset-network round trip, and continues to
animate offline after installation. The runtime retains a fallback for APKs built
without the generated asset set:

https://raw.githubusercontent.com/saeedtahmtan/telemoji/main/

When the machine building the app is fully offline, the bundle step is skipped and the
runtime fallback remains available; first playback then requires network access. The
source animations are maintained by a third-party mirror.

## Scope and important distinction

This change renders large standalone emoji messages and plays real Lottie reactions
when a reaction pill is selected. It does not add Telegram's MTProto-specific
messages.getStickerSet flow, sounds, paid/custom emoji entitlement, server-driven
emoji set updates, or sendMessageEmojiInteraction exchange: this app sends Matrix
events and those Telegram protocol features cannot be reproduced by a visual asset
alone. Unsupported or offline assets fall back to normal Unicode emoji / the existing
Matrix reaction UI. Do not claim full Telegram parity until the remaining
protocol-dependent behavior has been implemented and tested against Telegram on
devices.

The animation assets and catalog are maintained by their upstream authors. Review
their project license and Telegram's applicable terms before redistributing assets
inside a production APK.
