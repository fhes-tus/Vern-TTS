# Vern voice options: research for review — 4 November 2026

This is a research and product-copy proposal, not an implemented provider catalogue. Sources are publisher documentation, repositories and pricing pages checked on 4 November 2026. No model was installed, no API key was requested, no paid synthesis was called, no audio was auditioned, and no Android or desktop runtime was tested. Recommendations concern integration fit; they do not rank measured sound quality.

The user's @Voice screenshots are inspiration for provider connections, custom servers, usage information, Wi-Fi controls and saved audio. Their layout and settings are not an implementation specification. Website claims about voice counts, naturalness and latency are not independent measurements.

## Default voice popup

The user explicitly requested a popup instead of the proposed permanent introduction. Suggested first-use copy:

> **Your phone's voice is ready**
>
> Vern starts with the text-to-speech engine selected on your phone. Its installed voices determine the available languages and how reading sounds. You can keep this voice, choose another installed voice, or explore downloadable and online options in Voice Studio.
>
> **Continue** · **Choose a voice**

Proposal: show once before first narration, remember dismissal, and let users reopen the explanation from Voice Studio help. Keep a visible current-engine/current-voice label. Do not repeatedly interrupt reading. The app's current `ReaderSettingsModels.kt` uses an empty engine package and system-default labels; `PlaybackTtsSetup.kt` constructs Android `TextToSpeech` without an explicit engine for that setting. Thus the default is the phone's selected engine, which is not necessarily the originally preinstalled engine. Existing saved user choices need to be respected.

## What the requested names actually provide

| Name | Actual role | Access and delivery | Recommendation for Vern |
| --- | --- | --- | --- |
| Cartesia | Hosted narration API with voice selection and generation controls. | Documented HTTP bytes, SSE and WebSocket output. Free and paid account plans. | Strong cloud candidate; timestamp responses are relevant to reading highlights. |
| MiniMax | Hosted speech synthesis, voice design and cloning. | API-key HTTP output streaming and WebSocket synthesis; paid character usage. | Strong additional narration provider to compare. |
| Kitten | Both hosted APIs and two distinct local-model families. | Hosted streaming; lightweight v0.8 ONNX models; larger Kitten-TTS 2 weights/runtime. | Evaluate hosted service and v0.8 offline models separately. |
| Paradee | Small local English TTS model distilled from Kokoro. | ONNX; repository demonstrates file generation and word timing. No hosted service established. | Interesting offline experiment. |
| debpalash/VoiceStudio | Multi-model creative app and self-hosted speech service. | Documented compatible speech API; desktop/server and mobile preview options. | Useful custom-server candidate and integration reference. |
| YourVoic | Web voice generator with published TTS/API documentation. | Documentation describes streaming, but pricing still labels API access coming soon. | Conditional candidate; confirm actual account access and API billing. |
| AIDubbing | Web generation/dubbing tools using credits. | MP3 downloads; customized API access is discussed by contacting the company. | External website option until an integration contract and streaming documentation are established. |
| Dessa | Block's speech-to-speech conversation research preview. | Official demos/waitlist; no public narration API, weights or price verified. | Watch for future access. |
| ClearerVoice-Studio | Existing-audio enhancement, separation and speaker extraction. | Open-source processing toolkit. | Potential recording-cleanup feature, not a narrator. |
| Whisper | Speech recognition/transcription. | Local implementations and hosted transcription workflows exist. | Potential voice notes/transcripts, not book narration. |
| Zipformer | Speech-recognition model family. | Sherpa documents streaming microphone/file recognition models. | Potential offline dictation/commands, not a voice catalogue entry. |

## Hosted narration candidates

**Cartesia.** Its speech API accepts text and a selected voice; WebSocket responses can include word timestamps. Those are useful for synchronizing reader highlights, subject to actual testing. Current pricing lists 20,000 free monthly credits (about 27 TTS minutes) and a $5/month Pro plan with 100,000 credits (about 133 minutes). Credits can also be spent on other services. Free access is limited; paid/commercial features differ by plan. Sources: [streaming API](https://docs.cartesia.ai/api-reference/tts/websocket), [pricing](https://www.cartesia.ai/pricing). Proposed copy: “Online voices with language and delivery controls. Requires a Cartesia account and API key. Included usage and further charges depend on your plan.”

**MiniMax.** Its HTTP synthesis endpoint offers optional output streaming; a WebSocket API also accepts text and returns audio. Posted rates for speech-2.8 are $60 per million characters for Turbo and $100 for HD, including the corresponding asynchronous long-form models. Voice design and cloning have separate charges. The supplied public voice-generator page did not expose readable content to the web reader; the API documentation supplied the integration evidence. Sources: [HTTP API](https://platform.minimax.io/docs/api-reference/speech-t2a-http), [WebSocket API](https://platform.minimax.io/docs/api-reference/speech-t2a-websocket), [pricing](https://platform.minimax.io/docs/pricing/overview). Proposed copy: “Online speech with voice, speed and pronunciation controls. Uses your MiniMax API balance; model and voice options determine the available controls.”

**Kitten hosted API.** Public documentation provides a compatible `/v1/audio/speech` endpoint, voice listing, output streaming and a separate text-input streaming workflow. The streaming guide explicitly warns that a fixed small playback buffer does not guarantee uninterrupted playback. The website currently offers 30 introductory minutes shared between speech and transcription, followed by $15 per million TTS characters on its entry plan. A promotional Pro offer advertises $0.65 per million characters, conditioned on subscription: the inspected annual selection shows $179.88 billed annually ($14.99/month equivalent), with included monthly usage. This is not a universally available pay-as-you-go price. Sources: [speech API](https://docs.kittenml.com/tts/generate-speech), [output streaming](https://docs.kittenml.com/tts/stream-output), [current plans](https://kittenml.com/). Proposed copy: “Connect your KittenML account to stream speech. Introductory usage is limited; further usage follows your account's current plan.”

**ElevenLabs.** Remains relevant to the earlier request for using voices available through the user's account. The API documents streaming with a selected voice ID. Current posted example rates are $0.04 per thousand characters for v4 Turbo and $0.08 for v4, with model/plan-dependent included usage. The pricing page contains an expired September promotion, so that offer should not be reused in Vern. Voice access must be checked against the user's permissions and chosen model. Sources: [streaming](https://elevenlabs.io/docs/api-reference/text-to-speech/stream), [API pricing](https://elevenlabs.io/pricing/api). Proposed copy: “Use eligible voices from your ElevenLabs account. Requires internet and an API key; generation consumes your provider allowance or paid balance.”

**YourVoic.** Its developer pages show an API-key generation endpoint, voice listing and `/api/v1/tts/stream`. The streaming guide distinguishes provider streaming for some models from progressive pseudo-streaming for another. Its public pricing page simultaneously says “API access (coming soon).” Web plans advertise limited free characters and paid subscriptions, but those allowances cannot be assumed to apply to API use. Resolve this contradiction before offering Connect in Vern. Sources: [developer overview](https://yourvoic.com/api/docs/overview), [streaming](https://yourvoic.com/api/docs/text-to-speech/streaming), [pricing](https://yourvoic.com/pricing). Proposed copy while unresolved: “Explore voices on YourVoic's website. Direct API access and pricing are awaiting confirmation.”

**AIDubbing.** The supplied tool describes free starter credits, paid plans and downloadable generated MP3 audio. The pricing FAQ asks customers seeking API access to contact the company. No public self-service streaming API was established in this review. Web output downloads do not establish permission or an interface for automatic narration inside Vern. Sources: [voice tool](https://aidubbing.io/text-to-speech), [pricing and API enquiry](https://aidubbing.io/pricing). Treat as an external resource rather than an advertised connected provider until documentation and terms are available.

## Downloadable models and self-hosting

**Kitten v0.8.** Lightweight ONNX models have eight preset voices, adjustable speed and no cloning/expression controls. Nano int8 is listed around 25 MB; larger models are about 41–80 MB. The inspected Mini model card labels its weights Apache-2.0. These are the family to investigate for a small offline English narrator. Native compatibility, phonemizer assets, package notices and phone performance still need verification. Vern's Kotlin bindings declare a Kitten config, but its active engine families remain Android, Piper and Kokoro; the config alone proves no working Kitten integration. Sources: [v0.8 documentation](https://github.com/KittenML/KittenTTS/blob/main/docs/onnx-models.md), [Mini model card](https://huggingface.co/KittenML/kitten-tts-mini-0.8), [Sherpa Kitten documentation](https://k2-fsa.github.io/sherpa/onnx/tts/kitten.html).

**Kitten-TTS 2 local.** The repository describes a much larger multilingual model with voice cloning and expression controls, with separate CPU/GPU inference paths. It is not the same mobile integration as v0.8. Published platform/repository language and voice totals differ, so query the actual model catalogue rather than promise one universal count. The weights use the Stellon Labs Community License: the publisher describes registration/attribution requirements and free commercial eligibility only below its revenue/funding thresholds; this also has implications for hosted commercial use. Sources: [repository](https://github.com/KittenML/KittenTTS), [publisher's licence explanation](https://kittenml.com/license/). No entitlement or legal compliance determination was made for Vern.

**Paradee.** Current README describes v1.2: an approximately 8.5 MB int8 ONNX graph and four English voices, Heart/Puck (US) and Isabella/Fable (UK). It demonstrates word timings from synthesis durations, which may help highlighting. The documented CPU benchmark uses an Apple M4 Pro; it establishes no Android speed or battery result. The graph size is not the full installation size: phonemizer and dictionary dependencies also matter. The repository labels its code Apache-2.0. The exact v1.2 weight card could not be retrieved, so distribution rights for a pinned package remain to verify. Sources: [repository and current README](https://github.com/sahilmahendrakar/paradee), [model card linked by publisher](https://huggingface.co/sahilmahendrakar/Paradee-8M-v1.2). A new adapter is needed; it is not a replacement URL for Vern's existing Kokoro package.

**VoiceStudio.** This is a complete app supporting multiple engines, not one voice model. Its documented speech endpoint supports model/voice selection and chunked audio or SSE transport. That makes it relevant to “Connect your own server.” Engine inference may still buffer before returning audio; measure first-audio delay per engine. The project also documents Android/iOS previews and phone speech endpoints, but installing another app does not by itself establish Android system-TTS interoperability with Vern. The app is AGPL-3.0; model weights and voices carry their own licences. Self-hosting avoids a hosted-provider per-character bill but still uses the owner's hardware/electricity or rented server. Sources: [project](https://github.com/debpalash/VoiceStudio), [speech endpoint documentation](https://github.com/debpalash/VoiceStudio/blob/main/docs/agentic-voice.md), [mobile documentation](https://github.com/debpalash/VoiceStudio/blob/main/docs/mobile.md). Test protocol compatibility rather than import its app code or assume every installed engine offers the same features.

## Other requested names

**ClearerVoice-Studio:** Apache-2.0 repository for enhancement, separation, super-resolution and speaker extraction from existing audio. It could be investigated for noisy voice-note cleanup. It does not supply a text-to-speech narrator or voice-generation API. [Publisher repository](https://github.com/modelscope/ClearerVoice-Studio).

**Whisper:** recognizes spoken audio and produces text, useful for future notes or audio transcription. It does not generate a reading voice. The hosted `whisper-1` API has a published retirement date; that is separate from availability of local open models. [Official OpenAI transcription documentation](https://developers.openai.com/api/docs/guides/speech-to-text).

**Zipformer:** Sherpa documents it as a recognition model family, including streaming audio input. Potential uses are offline dictation and voice commands, with licence/runtime checks for the selected checkpoint. Streaming recognition produces text; it is not streaming narration. [Primary Sherpa model documentation](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html).

**Dessa:** the name is correct. Block's official page describes full-duplex speech-to-speech conversation trained on audio, with demos, a waitlist and safety-research access. It describes local operation on an M4 Max MacBook Pro or better. No public text-in narration endpoint, model download or consumer price was verified. Follow release developments; do not advertise it as an available Vern voice. Direct page opens failed, but official-domain web search returned the page's first-party text. [Official Block page](https://block.xyz/dessa).

## Additional options shown in the screenshots

| Provider | Evidence and expectation | Free/paid status |
| --- | --- | --- |
| Google Cloud TTS | Separate from the phone's Google speech engine. Cloud credentials/billing are required; documented bidirectional streaming is limited to compatible Chirp 3 HD voices. [API guide](https://docs.cloud.google.com/text-to-speech/docs/create-audio-text-streaming). | Model-dependent free allowances and paid usage; billing must be enabled. [Pricing](https://cloud.google.com/text-to-speech/pricing). |
| Azure Speech | Documented voice listing and synthesis with resource credentials and a matching endpoint/region. Appropriate multilingual candidate; playback events depend on integration choice. [API reference](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/rest-text-to-speech). | F0 advertises 500,000 neural TTS characters/month, with tier limits; paid rates vary. [Pricing](https://azure.microsoft.com/en-us/pricing/details/speech/). |
| Amazon Polly | Documented synthesis returns an audio byte stream; voice/engine selection and Speech Marks are relevant to reading. [API](https://docs.aws.amazon.com/polly/latest/APIReference/API_SynthesizeSpeech.html). | Posted rates per million characters: Standard $4, Neural $16, Generative $30, Long-form $100. Introductory credit eligibility is account-dependent. [Pricing](https://aws.amazon.com/polly/pricing/). |
| xAI/Grok | Public text-to-speech API and streaming workflow; documented timestamp support and an article read-along example. [API](https://docs.x.ai/developers/model-capabilities/audio/text-to-speech). | Published TTS rate $15/million characters. Do not confuse this with conversational voice-session pricing or a consumer subscription. [Voice overview](https://docs.x.ai/developers/model-capabilities/audio/voice). |
| OpenAI | Official OpenAI documentation says `tts-1`, `tts-1-hd` and `gpt-4o-mini-tts` retire on 6 January 2027. The recommended Realtime replacement uses a different workflow and is not a model-name swap for `/v1/audio/speech`. Validate exact-text narration and cancellation before selecting it. [Guide](https://developers.openai.com/api/docs/guides/text-to-speech), [deprecations](https://developers.openai.com/api/docs/deprecations). | Paid API usage; current Realtime pricing is token-based. Avoid a new implementation centered on retiring models. |
| Custom compatible server | Connection needs the endpoint, optional credentials, model and voice identifiers. VoiceStudio is one documented example. “Compatible” describes request/response conventions; it does not require buying an OpenAI account or guarantee a standard voice-list endpoint. | Local/self-hosted compute can avoid a provider's per-character charge; hosting and model licences still apply. |

## Cost comparison for review

USD snapshots exclude taxes, hosting, cloning/design fees and subscription minimums. The 100,000-character column is arithmetic from posted usage rates, not a quoted audiobook price or a cross-language duration estimate. Plan allowances, models and billing rules differ.

| Option | Published price/allowance | 100,000 billable characters outside allowances |
| --- | --- | --- |
| Kitten entry API plan | 30 introductory minutes; then $15/million characters | $1.50 |
| Kitten Pro promotional usage rate | $0.65/million, conditioned on paid plan and promotion | $0.065 incremental usage; subscription commitment also applies |
| Cartesia | 20,000 free credits/month; Pro $5/month with 100,000 credits | Depends on plan/remaining allowance; not a universal $5 usage quote |
| MiniMax Turbo / HD | $60 / $100 per million | $6 / $10 |
| ElevenLabs v4 Turbo / v4 | $0.04 / $0.08 per thousand | $4 / $8 |
| Grok TTS | $15 per million | $1.50 |
| Polly Standard / Neural / Generative | $4 / $16 / $30 per million | $0.40 / $1.60 / $3 |
| Azure F0 | 500,000 neural characters/month, subject to tier availability/limits | Within allowance if eligible; not an unlimited free plan |
| Local Paradee / vetted Kitten v0.8 | No hosted usage tariff established | Local device resources; model/distribution obligations remain |

Provider previews, retries and regeneration may consume billable usage. Do not infer API access from a website's free demo or assume all account voices are accessible via API.

## Product behavior worth taking from the screenshots

Keep setup simple: select provider, add credentials, test connection, choose a compatible voice, preview, then read. Put server URLs, timeout and retry settings under Advanced. Surface language/accent, installed/download status, internet requirements and cost basis where a user chooses a voice.

Offer Wi-Fi-only cloud use as a user choice. Make cloud-to-phone fallback an explicit preference; preserve the reading position when a request fails. Avoid automatically resetting a deliberately selected cloud voice on every restart. Document text goes directly to the explicitly selected service for narration; protect credentials in native storage and exclude them from logs and portable backups.

Saved generated audio can avoid another generation request on repeat reading where provider terms permit. Use bounded storage, a clear-cache action and cache keys covering text, provider, model, voice and settings. A limit on preloading reduces unused paid generation. Stopping prevents new requests, but an already accepted request may still be billed. Actual usage displays should distinguish provider-reported totals from Vern estimates.

## Suggested review order and remaining evidence

1. Preserve phone TTS as the initial default, record the popup decision, and retain explicit Studio/Kokoro and Lite/Piper choices.
2. Compare short narration samples from Cartesia, ElevenLabs and Kitten's hosted service. Include MiniMax and Azure where their languages/voices match the target readers. Choose based on sustained listening, language needs, cost and playback behavior, not published naturalness rankings.
3. Prototype Paradee v1.2 and Kitten v0.8 as separate offline adapters. Validate package licences, pronunciation dependencies, actual installed footprint, first-audio delay, sustained speed and highlighting on a physical Android phone.
4. Add a tested custom-server profile for an explicit compatible endpoint. VoiceStudio is a candidate; every engine/transport combination needs verification.
5. Keep YourVoic conditional until API access/billing is clarified, AIDubbing as an external resource pending a documented API agreement, and Dessa on the research watchlist. Keep Whisper/Zipformer/ClearerVoice out of the narrator picker.

Future acceptance must exercise exact source text, punctuation/numbers/names, mixed-language books, seek/cancel, bounded prefetch, API-key/quota failures, network interruptions, cached repeat reading and screen-off/background playback. Word highlights require real timing data or an explicitly described coarser approach. No native or sound-quality acceptance is established by this document.

Retrieval limitations: some direct Block, Kitten platform and Paradee weight-card requests failed; one guessed VoiceStudio `docs/api.md` path returned 404 and was replaced with the actual `docs/agentic-voice.md`. MiniMax's marketing page had no readable text. These failures are not evidence of unavailable products. The cited accessible publisher docs and clearly stated unresolved points bound this research.
