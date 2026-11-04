# Android personal cloud voice hardening — 4 November 2026

Scope: the Android voice connections introduced in `bf58823`. The desktop implementation and unrelated work are outside this change. This is a source audit and regression verification, not an independent penetration test or a promise that provider contracts will never change.

## Current recommended connections

| Provider | New-profile model | Speech route and response | Verification |
| --- | --- | --- | --- |
| ElevenLabs | `eleven_v4` | `/v1/text-to-dialogue/stream`, one `inputs` entry with `text` and `voice_id`, MP3 | [Models](https://elevenlabs.io/docs/overview/models), [dialogue stream](https://elevenlabs.io/docs/api-reference/text-to-dialogue/stream) |
| Cartesia | `sonic-3.6` | `/tts/bytes`, `Cartesia-Version: 2026-08-14`, string voice ID, WAV PCM16/44.1 kHz | [Current stable alias](https://docs.cartesia.ai/build-with-cartesia/tts-models/latest), [bytes API](https://docs.cartesia.ai/api-reference/tts/bytes) |
| MiniMax international | `speech-2.8-hd` | `/v1/t2a_v2`, non-streaming JSON with hex MP3 | [Synchronous TTS](https://platform.minimax.io/docs/api-reference/speech-t2a-http) |
| Hosted KittenML | `kitten-tts-2-latest` | `/v1/audio/speech`, MP3; stock voice name or cloned voice object | [Official current example](https://kittenml.com/) |
| Custom HTTPS speech server | Owner-supplied model | Exact entered endpoint, OpenAI-compatible speech request, MP3 or WAV | Contract belongs to the server operator; compatibility is not inferred from the name alone |

Cartesia's stable alias follows stable snapshots, not `sonic-preview`. Kitten's documented latest alias is used as published. Eleven v4 Turbo and v3 Conversational require a different realtime protocol and are rejected by this HTTP implementation. Existing supported Eleven TTS models keep their TTS route. Existing selections are not silently changed, since that could change narration or charges; the settings action selects the release's current recommended model. Kitten's new-profile stock voice is the current official Saoirse example. Users may select their account's other voice IDs.

Newest model does not establish access to every voice, language, region or billing plan. The preview action is the explicit account/voice audition and can incur provider charges. The application has no embedded provider key and does not relay requests through a Vern server.

## Findings fixed

- A draft preview could replace the key used by active reading. Preview now receives a transient credential override and cannot change the active configuration or stored key. Activation validates first, then saves, with credential rollback on a configuration-save failure.
- Malformed header tokens, invalid ports, missing speech paths, credential-bearing URLs and altered built-in hosts are rejected before network IO. Custom servers may use trusted private HTTPS hosts. Android's default certificate/hostname verification remains in force; no insecure trust manager was added.
- Redirects remain disabled, so auth headers are not forwarded. Credentials are absent from JSON bodies and URL construction. Automatic HTTP caching is disabled, with a no-store request header.
- Error bodies are never surfaced verbatim. Authentication, quota, transport and audio failures become local translated messages, without retaining provider exception causes. MiniMax semantic errors inside HTTP 200 are checked separately. Unknown saved provider names fail closed to phone voices.
- Advertised and actual response sizes are bounded. A total deadline disconnects the request even if a server keeps returning small reads. Only explicit HTTP 429 retries are permitted; ambiguous POST failures are not replayed.
- MIME and container checks reject proxy HTML/JSON and truncated headers before audio publication. Existing invalid cache entries and native decoder failures are evicted. Abandoned process fragments are cleaned without deleting files owned by current requests. Optional retained audio stays in private cache with the existing 64 MB bound.
- Configuration/request diagnostic representations hide endpoint, voice and text. Recognized auth fields, bearer tokens, common key prefixes and URLs are redacted from crash reports before writing and again before sharing old reports. Crash files are excluded from backup/transfer. Unlabelled arbitrary text is not guaranteed to be detectable as a secret; the voice network path therefore suppresses raw provider errors at source.
- Keystore AES-GCM credentials remain authenticated to their namespace and excluded from both backup systems. FileProvider paths do not expose credential preferences or cloud audio. Playback service app commands retain their capability checks; only trusted Media3 controllers receive a session.
- Advanced numeric settings retain editable drafts and validate when applied, so users can clear and replace a value instead of the previous number reappearing mid-edit.

## Verification boundary

JVM tests exercise exact current request contracts, old supported Eleven routing, invalid configurations/keys, redaction, cache cleanup, redirected/error responses, bounded bodies, total-deadline cancellation, no ambiguous retries and submission cancellation. All 696 JVM tests in 106 suites passed. Eight native tests passed on Samsung SM-A175F / Android 16 API 36 in 23.749 seconds: Keystore isolation including copied-ciphertext rejection, preview/activation credential separation, native audio playback/Stop, rejected-file eviction, phone fallback, two device-language checks and locale resource fallback. Actual commands and packaging results are recorded in `progress.md`.

No live paid API synthesis or real account key was used in this work. The tests use fake credentials, fixture HTTP responses, generated local audio and an unavailable loopback endpoint. Account-specific permissions, provider-side retention, voice availability, listening quality and long-book acceptance remain live-account checks. No automatic pricing or remaining-credit balance is claimed. Full lint and additional Android/OEM versions remain outside the verified results.
