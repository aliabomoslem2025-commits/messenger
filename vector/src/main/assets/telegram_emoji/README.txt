Telegram animated emoji assets
==============================

The catalog at ../telegram_animated_emoji_manifest.json contains all 599 animation records in the pinned Telemoji snapshot (580 normalized Unicode emoji keys, including alternate variants).

Source:
https://github.com/saeedtahmtan/telemoji
Pinned source revision:
4f45fca016c71de9d2a9e15c07ae1fe3d8327a28

Renderer and caching:
- Android 9+ prefers animated WebP files rendered from the Telegram TGS sources through rlottie-python, so the output is closer to Telegram's own TGS renderer.
- If WebP decoding or downloading fails, the app falls back to the original gzip-compressed TGS/Lottie JSON.
- The three commonly used emoji 😀, 😂 and 🤣 also have a small Base64/TGS-derived local fallback.
- Remote animation assets are fetched over HTTPS on first use and cached in app-private storage for later/offline replay. A never-before-used emoji still needs a network connection to fetch its asset.

The Telemoji repository credits the animation assets to Telegram's Animated Emoji pack. These are not original artwork by this repository. Use of the original animations must comply with Telegram's terms:
https://core.telegram.org/api/animated-emojis
https://core.telegram.org/stickers

Airbnb Lottie for Android is Apache-2.0. The Telemoji website/repository provides its own open-source license, but that license does not replace the terms applicable to Telegram's original TGS artwork.
