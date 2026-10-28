# Vern Telegram community kit

The channel is `@myreader_veritas`; the bot is `@VernTTS_bot`.

## 2.7.0 / 45 drafts

- [Channel announcement](release/v2.7.0/text/telegram-channel-post.txt)
- [Bot preview copy](release/v2.7.0/text/telegram-bot-preview.html)
- [Full release notes](RELEASE_NOTES_v2.7.0.md)
- [Release preparation and verification](release/v2.7.0/README.md)

These are local drafts. Review the channel post after the update is available. The Worker and polling bot's `/milestone` response describes the upcoming version; `/download` and `/changelog` continue to resolve actual published releases. Source updates have not deployed a bot or sent a message.

## Channel bio

```text
Vern — read, listen and study with your books and documents.
Offline-first reading and OCR, optional online tools, and no account required for local reading.

Google Play: https://play.google.com/store/apps/details?id=com.veritas.reader
Website: https://fhes-tus.github.io/Vern-TTS/
Bot: @VernTTS_bot
```

## Pinned welcome message

```text
Welcome to the Vern community.

Read supported books and documents, listen with on-device or supported Android voices, and keep your notes and study materials together.

Get Vern:
https://play.google.com/store/apps/details?id=com.veritas.reader

Published APKs and release notes:
https://github.com/fhes-tus/Vern-TTS/releases

Website:
https://fhes-tus.github.io/Vern-TTS/

For download links and published changes, ask @VernTTS_bot with /download or /changelog. Use /milestone to preview the next update.

Your reading data stays on the device by default. Optional AI, translation, sharing and online catalog features use their relevant services; the privacy policy explains these and ML Kit diagnostics.

Share reading recommendations and feedback. For a bug report, include your Android version, document format, the steps that caused it and a screenshot if useful. Keep conversations respectful.
```

## Publication

The existing GitHub release workflow can announce a published release. Do not dispatch it merely to preview this draft. Bot deployment and authenticated webhook configuration are described in [cloudflare_worker/README.md](cloudflare_worker/README.md); running the polling bot changes webhook ownership, so it is not a copy-preview command.
