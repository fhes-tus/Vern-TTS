# Vern 2.6.2 security milestone verification

Source: 2.6.2 / versionCode 42. Verified 15 October 2026. Release APK/AAB assembly and publication remain on hold.

## Repairs and focused checks

The 14 October audit remains a historical record. Its S1–S6 findings have source repairs in this milestone:

| Boundary | Change | Evidence |
| --- | --- | --- |
| Private playback intents | Persistent app-only capability; reject missing/altered tokens; restrict session connections to trusted controllers | Samsung capability tests and actual cold/repeated speech plus native-selection regression |
| Browser downloads | Explicit confirmation, HTTPS-only redirects, rebuilt destination cookies, bounded streamed writes, supported-file checks, partial cleanup | JVM redirect/cookie/error/size checks; debug compilation |
| Document and voice extraction | Document input budget; ZIP count/per-entry/aggregate limits include skipped entries; voice TAR aggregate expansion budget | JVM exact-limit, skipped-entry and aggregate/count checks |
| Browser lifecycle | Third-party cookies disabled; dismissed WebViews explicitly destroyed | Source and debug build verification; broader browser acceptance remains |
| File sharing | Narrow provider roots; deny private app files; stage explicit exports; preserve direct camera writeback | Samsung private-file refusal, export readback and camera-output checks |
| APK updates | Package, newer version code and verified signing-history checks before installer | Samsung installed-APK rejection; signing tools and direct Play Console comparison |

`commons-lang3` is constrained to **3.18.0** to address CVE-2025-48924. No reachable exploit path was established in the earlier audit; the vulnerable transitive version was removed as a precaution.

## Results

- Debug application, JVM suite and Android test APK build: success.
- **480 JVM tests: 0 failures, 0 errors.**
- Samsung SM-A175F / Android 16: **4 security boundary tests + 1 actual playback/selection regression passed**.
- Worker: **3 mocked network tests passed** (configuration/authentication, malformed/oversized bodies, milestone command, actual published APK asset lookup).
- Python bot: **3 tests passed** (missing-token refusal, bounded/capped rate-limit retries, actual APK assets); all four operational scripts compile.
- No embedded Telegram token matches in the candidate source inventory. Earlier Git history still contains the token; the owner declined rotation.

## Distribution finding

Direct Play Console inspection confirms app signing SHA-256 `C54A80058D6D9142C1179818DE7CB206BF0119E246403BEAC5FFA23686297E45`. The upload key and local 2.6.1 APK use `3B143A77A70253305A496595432461B018FFCB3792E82A96905BD10243424DB8`. They differ. The legacy APK passes the LegacyApk signing check and must fail the Play-channel check. See `docs/UPDATE_COMPATIBILITY.md`; no installed production app was uninstalled or migrated.

## Scope and handoff

Superseded root plans, one-off source mutation scripts, an old listing ZIP and six outdated debug previews were moved to a recoverable local folder outside the project. Their remaining acceptance items are consolidated in `docs/ACCEPTANCE_BACKLOG.md`.

Site and bot source describe 2.6.2 as in development and retain published stable downloads. The live Cloudflare Worker has no encrypted runtime token/webhook-secret bindings and its deployed source contains additional functions absent from the repository baseline. Live deployment requires preserving those functions and configuring the authenticated webhook together. Do not deploy the fail-closed source without that configuration. No Telegram announcement was sent.

Full lint, full penetration testing, native binary provenance beyond the earlier scoped review, all-device acceptance and release/R8 checks are incomplete. These results apply to the tested debug snapshot, not a universal security guarantee.
