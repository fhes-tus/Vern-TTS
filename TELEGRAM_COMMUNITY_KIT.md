# Vern Telegram community kit

The channel is `@myreader_veritas`; the bot is `@VernTTS_bot`.

## 2.7.0 / 45 content

- [Channel announcement](release/v2.7.0/text/telegram-channel-post.txt)
- [Bot preview copy](release/v2.7.0/text/telegram-bot-preview.html)
- [Full release notes](RELEASE_NOTES_v2.7.0.md)
- [Release preparation and verification](release/v2.7.0/README.md)

The user confirmed version 2.7.0 / build 45 as the current Play release. The bot uses a concise welcome, Google Play as the primary install option, current release notes, and practical reading help. GitHub APK downloads are labelled separately and resolve actual published assets. [Shared content](scripts/telegram_content.json) supplies both bot implementations, profile text, the channel bio, and the channel announcement. Actual publication receipts are recorded in [progress.md](progress.md).

## Channel bio

```text
Vern for Android — read, listen and study. Updates, reading ideas and help.
Get Vern: https://play.google.com/store/apps/details?id=com.veritas.reader
Bot: @VernTTS_bot
Website: https://fhes-tus.github.io/Vern-TTS/
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

For installation and current changes, ask @VernTTS_bot with /download or /changelog. Version 2.7.0 / build 45 adds photo scanning, quote images, PDF swipes, and reading improvements.

Your reading data stays on the device by default. Optional AI, translation, sharing and online catalog features use their relevant services; the privacy policy explains these and ML Kit diagnostics.

Share reading recommendations and feedback. For a bug report, include your Android version, document format, the steps that caused it and a screenshot if useful. Keep conversations respectful.
```

## Publication

The existing GitHub release workflow can announce a published release. Do not dispatch it merely to preview this draft. Bot deployment and authenticated webhook configuration are described in [cloudflare_worker/README.md](cloudflare_worker/README.md); running the polling bot changes webhook ownership, so it is not a copy-preview command.
