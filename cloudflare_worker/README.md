# Vern Telegram bot

Cloudflare Worker for `@VernTTS_bot`. It serves book searches, guides, published downloads and the upcoming 2.6.2 milestone. Downloads resolve actual APK assets in the latest published GitHub release; development notes do not publish new packages.

## Configuration

Keep credentials out of source, `wrangler.toml`, shell history and screenshots. Configure `TELEGRAM_BOT_TOKEN` and `TELEGRAM_WEBHOOK_SECRET` as encrypted Worker secrets. Register the HTTPS Worker URL with Telegram's `setWebhook` API, including the same random `secret_token` and `allowed_updates` containing `message` and `callback_query`. Use a trusted local script or secret manager rather than a browser URL containing the bot token.

The Worker rejects unauthenticated requests, limits update bodies to 64 KiB and returns sanitized failures. Deploy code and configure the authenticated webhook together so the existing bot is not interrupted by a missing secret. `wrangler deploy --dry-run` validates the bundle without publishing it. Hosting is subject to Cloudflare's plan and limits.

GitHub Actions uses the repository's `TELEGRAM_BOT_TOKEN` secret and optional `TELEGRAM_CHANNEL_ID`. No workflow contains a token fallback. Do not dispatch release or spotlight announcements merely to test code.

For local polling, provide `TELEGRAM_BOT_TOKEN` through the environment and run `python scripts/bot_polling.py`. Polling removes the webhook, so use it only when intentionally switching away from the deployed Worker.

## Verification

Run `node --test cloudflare_worker/worker.test.mjs` for authenticated webhook, body limits, command routing and actual release-asset checks. These use synthetic tokens and mocked network responses; they send no Telegram messages.

The previously embedded token was removed from current source. It remains in earlier Git history; the owner chose to keep it active rather than rotate it.

## Current deployment

Updated 15 October 2026 with encrypted Worker secrets and an authenticated Telegram webhook. Public HTTP admin actions were moved to the authenticated local manager (`scripts/manage_bot.py`). The live message/media/changelog behavior is retained; `/milestone`, `/customize` and `/security` describe the upcoming update. GitHub Actions uses its encrypted token secret. No release announcement was sent.
