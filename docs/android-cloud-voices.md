# Personal cloud narration in Android

The localization rollback checkpoint is `8c36ede`. Voice changes follow that commit and are isolated to Android. The existing phone/downloaded voice preference is preserved. New cloud configuration lives in `vern_cloud_voice`; Android TTS remains the initial default.

## Implemented scope

Voice & language starts with a source choice and a dismissible explanation of the phone default. Users can reopen that explanation. Phone voice selection precedes delivery presets. A small external directory links to Google Speech Recognition & Synthesis, RHVoice and eSpeak, with descriptions of language coverage, offline behavior, price differences and synthetic sound. Availability is not promised for every country or device.

Personal API connections support ElevenLabs, Cartesia, international MiniMax, hosted KittenML and an exact HTTPS endpoint accepting the OpenAI speech JSON format and returning MP3. Each provider has a saved profile and independent credential. Custom endpoint credentials are scoped to the endpoint hash. The app sends text only after explicit cloud activation or the labelled preview action. Testing a draft saves its profile without replacing the active reading source.

API keys use the existing Android Keystore AES-GCM credential store with separate authenticated namespaces. Existing AI credentials retain their original format. Keys are absent from cloud profiles, requests' JSON bodies, document exports, logs, Android cloud backups and device transfer. Credentials are never forwarded through redirects. Endpoint URLs cannot contain user information, query strings or fragments. A self-hosted HTTP server needs HTTPS in front of it for this implementation.

The reader submits at most one current passage of up to 800 UTF-16 code units, preserving grapheme boundaries through the existing speech plan. Each audio result is fully buffered before native MediaPlayer playback. This is passage-by-passage online narration, not live playback of arriving audio bytes. No look-ahead requests run. Highlight timing is at passage level. Speed and pitch are device playback adjustments; this does not expose provider emotion controls or cloud full-cast voice assignments. Audio export continues to use its existing engine path and does not use cloud credentials.

Pause, seek, stop, replacement and service teardown cancel the current cloud player and disconnect its request. Stale completions cannot advance the reader. Requests already accepted can still be billed. Ambiguous network failures are never automatically replayed; optional retries (0–2, default zero) apply only to HTTP 429. Wi-Fi-only is enabled initially. Phone fallback is opt-in, continues from the current passage, lasts for the playback session and does not overwrite the chosen cloud connection. Cached passages can be replayed without a network request.

Cancellation is checked again after opening the output stream and before writing passage text. Temporary file publication and cancellation share an owner so a completed download cannot leave uncached audio behind during the IO-to-main handoff. Regression tests cover concurrent publication/Stop, deliberate cache retention, and Stop while opening a connection.

Optional caching is disabled initially and explicitly asks users to enable it only if their provider permits storage. Audio is in the private application cache, bounded to 64 MB, with a 16 MB response limit per passage. Cache identity includes provider, endpoint, model, voice, language, credential hash and text. Playback speed/pitch do not regenerate source audio. The page offers cache removal and credential removal.

Usage counts submitted characters and attempted requests per provider on this device, including previews. Optional estimates use a rate entered by the user in that rate's currency. They are not provider billing, credit balances or a guarantee that failed requests are free. No provider prices are hardcoded.

## Protocol references checked 4 November 2026

- [ElevenLabs stream speech](https://elevenlabs.io/docs/api-reference/text-to-speech/stream): voice in endpoint, `xi-api-key`, `model_id`, MP3 response.
- [Cartesia bytes](https://docs.cartesia.ai/api-reference/tts/bytes): current `2026-08-14` version, string voice ID, Sonic 3.6 and WAV response. Older request shapes in third-party integrations were not used.
- [MiniMax synchronous TTS](https://platform.minimax.io/docs/api-reference/speech-t2a-http): international endpoint, voice/audio settings and hex-encoded audio inside JSON.
- [KittenML quickstart](https://docs.kittenml.com/getting-started): hosted Kitten-TTS 2 speech endpoint. This connection does not install Kitten offline models.
- [Google listing](https://play.google.com/store/apps/details?id=com.google.android.tts), [RHVoice listing](https://play.google.com/store/apps/details?id=com.github.olga_yakovleva.rhvoice.android), [eSpeak listing](https://f-droid.org/packages/com.reecedunn.espeak/). RHVoice describes most voices as free and some as paid.

## Remaining work and acceptance limits

Provider transport and request contracts have local tests. On Samsung SM-A175F / Android 16, native tests passed for encrypted credentials, generated local silent audio playback, cancellation, and end-to-end reader fallback from an unavailable loopback HTTPS endpoint to phone TTS, followed by Stop. The test restored the user's voice/cloud configuration and removed its fixture. No paid API request or account-specific voice was auditioned. Live provider language quality, account permissions, cache rights and long-book reliability need acceptance with the owner's accounts. Manual model/voice IDs are currently used; authenticated voice browsing is not implemented.

Google Cloud, Azure, Polly, xAI, new OpenAI Realtime narration, additional offline Kitten/Paradee models and other research candidates are not implemented by this change. Whisper, Zipformer and audio enhancement tools are not presented as narrators. Existing offline Studio/Lite implementations retain their own download/catalog behavior. Full lint, Android 12 acceptance, large-font/RTL sweeps, interruptions across OEMs and cloud export remain open.

All new interface messages have French, Spanish, Hindi, Simplified Chinese and Arabic preview translations. These still require speaker review. Actual commands, results and tested platforms are recorded in `progress.md`.
