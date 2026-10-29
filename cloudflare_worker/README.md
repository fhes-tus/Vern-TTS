# Vern Telegram bot

Cloudflare Worker for `@VernTTS_bot`. It serves book searches, reading guides, Google Play installation, and the Vern 2.7.0 / build 45 release summary. The welcome, downloads, feature help, FAQs, command menu, and channel copy share [telegram_content.json](../scripts/telegram_content.json) with the Python polling bot. Google Play is the primary install option. Alternative APK links resolve actual assets from the latest published GitHub release and are labelled separately; no APK filenames are guessed.

## Configuration

Keep credentials out of source, `wrangler.toml`, shell history and screenshots. Configure `TELEGRAM_BOT_TOKEN` and `TELEGRAM_WEBHOOK_SECRET` as encrypted Worker secrets. Register the HTTPS Worker URL with Telegram's `setWebhook` API, including the same random `secret_token` and `allowed_updates` containing `message` and `callback_query`. Use a trusted local script or secret manager rather than a browser URL containing the bot token.

The Worker rejects unauthenticated requests, limits update bodies to 64 KiB and returns sanitized failures. Deploy code and configure the authenticated webhook together so the existing bot is not interrupted by a missing secret. `wrangler deploy --dry-run` validates the bundle without publishing it. Hosting is subject to Cloudflare's plan and limits.

GitHub Actions uses the repository's `TELEGRAM_BOT_TOKEN` secret and optional `TELEGRAM_CHANNEL_ID`. No workflow contains a token fallback. Do not dispatch release or spotlight announcements merely to test code.

For local polling, provide `TELEGRAM_BOT_TOKEN` through the environment and run `python scripts/bot_polling.py`. Polling removes the webhook, so use it only when intentionally switching away from the deployed Worker.

## Verification

Run `node --test cloudflare_worker/worker.test.mjs` for authenticated webhook, body limits, command routing and actual release-asset checks. These use synthetic tokens and mocked network responses; they send no Telegram messages.

The previously embedded token was removed from current source. It remains in earlier Git history; the owner chose to keep it active rather than rotate it.

## Current deployment

The Worker retains encrypted secrets and an authenticated Telegram webhook. Public HTTP administration remains disabled. `/milestone` is a compatibility alias for the current release notes; `/customize` opens appearance help and `/security` opens privacy help. Old FAQ button identifiers remain supported. The command menu and profile descriptions are managed through Telegram's authenticated APIs; code deployment alone does not register them. GitHub release announcements use a short summary, Google Play first, and only actual published APK assets.

Deployment and channel-publication results, including the actual Worker version and channel message receipt, are recorded in [progress.md](../progress.md). Tests use synthetic credentials and mocked networking; they do not establish live Telegram behavior.
