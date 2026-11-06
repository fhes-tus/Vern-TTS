# Android speech options — 2 November 2026

This is source and primary-documentation research. No models were downloaded or executed, no audio was auditioned, and no Android device, emulator, browser inference, or native desktop inference was tested in this investigation. The priority order below reflects integration fit and published licensing, not measured sound quality.

## Recommendation

The user's priority is general narration improvement: naturalness, clarity, long-session comfort and smooth playback on Android. Ukrainian or other language expansion is secondary.

1. Establish a fair baseline using the current Kokoro and Piper voices at neutral pitch and consistent speed. Compare Kokoro's raw synthesis with the app's processed output: `KokoroTtsEngine.kt` exposes raw generation and passes normal playback through `KokoroVoiceProcessor.render`. This is a comparison to perform, not evidence that processing is faulty.
2. Audition KittenTTS **v0.8 Mini** and **Micro** against that baseline. Their inspected model cards label weights Apache-2.0 and list eight voices, with 80M/40M parameters and approximately 79/40 MB respectively. Include **v0.8 Nano int8** (15M, approximately 25 MB, also Apache-2.0) as the smaller-phone comparison. Current Sherpa documentation explicitly lists these v0.8 packages. Compatibility and sustained speed against the app's actual bundled native libraries remain to establish; model size alone does not rank sound quality.
3. Prototype **Chatterbox Nano** as a further English quality candidate. It has MIT weights and a published CPU inference path, but no Android performance has been established here. Its reference-audio voice selection and runtime need a larger integration experiment.
4. Keep **Chatterbox Multilingual** and **Qwen3-TTS** for a separate, more resource-intensive quality comparison. Add vetted Piper language packs when language coverage becomes a product priority. Russian Dmitri/Denis and the Ukrainian three-speaker model remain documented options, with no claim that any matches Bot Maxim.

## What the current Android source establishes

- `app/src/main/java/com/veritas/reader/tts/VoiceModelManager.kt` exposes only `KOKORO` and `PIPER` in `OfflineEngineType`. Its actual catalog entries contain 24 English Kokoro speakers and 12 UK/US English Piper voices. The package description advertises 28 Kokoro speakers, so that description and the catalog differ. This describes downloadable in-app neural voices, not every voice a user's installed Android TTS engines may expose.
- The selected Kokoro package remains the pinned int8 multilingual v1.0 archive. The source documents speaker-order compatibility constraints; changing a model URL by version number alone is unsafe.
- The downloader calls `pruneNonEnglishAssets` for both families. It retains only `en_dict`, removes other eSpeak dictionaries and Chinese-specific files, and then hoists Piper pronunciation data into a shared directory. If shared data already exists, a later package's copy is discarded. Additional languages need explicit asset retention and shared-data migration/merge behavior, including users with existing English downloads.
- `tts/PiperEngine.kt` always generates with `sid = 0`. Offering every speaker in the Ukrainian three-speaker model needs speaker metadata and selection propagated into synthesis.
- `app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt` already declares Kitten, Pocket and Supertonic configuration types and identifies its bindings as matching Sherpa-ONNX v1.13.8. These declarations do not prove compatible bundled native behavior, and the app has no corresponding catalog engine entries or adapters. Its exposed `generate` wrapper takes text, speaker and speed; Pocket's reference-audio generation would need additional API wiring.

## Candidate evidence

| Candidate | Published license evidence | Language/use fit | Practical implication |
| --- | --- | --- | --- |
| Piper Dmitri / Denis | Piper voice repository metadata says MIT; each voice's dataset card says CC0. | Russian, one speaker per model. | First language-pack candidates to audition with the existing Piper path. Preserve the exact package's notices and provenance. |
| Piper ukrainian_tts medium | Repository metadata says MIT; voice dataset card says CC0. | Ukrainian, three speakers, 22,050 Hz. | First Ukrainian candidate; all three speakers require a selection change. Sherpa publishes a converted package and Android engine examples. |
| Kokoro-82M | Publisher labels weights Apache-2.0. | Existing Studio baseline; supported v1.0 voice list does not include Russian or Ukrainian. | Keep as an English comparison baseline. A good model family does not establish the quality or pacing of this app's current output. |
| KittenTTS v0.8 Nano int8 | Specific model card says Apache-2.0. | English; eight preset voices; approximately 25 MB. | Strong next small-model experiment. Current Sherpa docs list v0.8 support; verify the actual model/native-library combination before implementation claims. |
| KittenTTS v0.8 Mini / Micro | Both inspected model cards say Apache-2.0. | English; eight voices; Mini 80M parameters/about 79 MB, Micro 40M/about 40 MB. | First new candidates for a general narration comparison; keep Nano as the size/performance alternative. Greater parameter count is not a listening result. |
| Chatterbox Nano | Specific model card says MIT. | English; 110M parameters. Publisher reports faster-than-real-time CPU generation on eight cores. | Worth a later prototype. That CPU result is not evidence of performance on a mid-range Android phone. Reference implementation uses PyTorch and supports reference-audio voice selection. |
| Chatterbox Multilingual V3 | Official model repository labels Chatterbox MIT. | Approximately 500M parameters; Russian is listed, Ukrainian is absent from the published general multilingual language list. | Candidate for a separate resource/performance experiment, not an established mobile upgrade. |
| Qwen3-TTS | Official code and the inspected 0.6B Base checkpoint are Apache-2.0. | Ten listed languages including Russian; Ukrainian is absent. Family includes 0.6B and 1.7B models. | Consider for a desktop/server quality comparison or a separately scoped mobile port. Its reference example uses CUDA; this investigation establishes no phone performance. |

The word “FOSS” needs care with AI distributions: runtime code, model weights, voice recordings, and phonemizer dependencies can have different licenses. The table records the inspected publisher evidence rather than treating a repository-wide badge as a license for every voice or dependency.

## Candidates with additional restrictions or a different role

- **Piper Ruslan:** its own model card specifies a CC BY-NC-SA 4.0 dataset. Keep out of the simple permissive distribution shortlist pending an exact rights assessment. **Piper Irina:** its dataset license is marked unknown. Other Piper voices cannot inherit Dmitri/Denis's license conclusions.
- **KittenTTS 2:** distinct from v0.8. Its code is Apache-2.0 but its weights use the Stellon Labs Community License, with registration and commercial eligibility restrictions. It is not an unrestricted FOSS substitute for the v0.8 weights.
- **Pocket TTS:** MIT code, CC BY 4.0 model metadata, separately licensed voice prompts, and additional model-page access/use conditions. Current official code lists English, French, German, Portuguese, Italian, Spanish and Dutch; Russian/Ukrainian are not listed. Small English models were announced in October 2026. Interesting research candidate, but resolve the exact checkpoint/prompt/terms before classifying a shipped pack as permissive.
- **Supertonic 3:** supports both Russian and Ukrainian and publishes ONNX assets, but weights are OpenRAIL-M rather than MIT/Apache. The official repository was archived in September 2026 and says development/support ended. Keep outside the main FOSS recommendation.
- **RHVoice:** a free/open-source external Android engine with Russian and Ukrainian voices. Its statistical parametric synthesis is a different option from the neural models above. It can be evaluated through the app's system-TTS integration; its engine and individual voices have their own licenses. No interoperability test was performed.

## First audition protocol

Use the same passages across models within each language: ordinary narration, dialogue, names, abbreviations, dates/numbers, and a longer paragraph. Have a native speaker assess Russian/Ukrainian pronunciation and comfort. Compare at the same apparent playback speed.

On a physical mid-range Android phone record cold/warm time to first audio, synthesis time versus audio duration, sentence gaps, peak memory, sustained battery/thermal behavior, and operation without networking after download. Include seek/cancel, screen-off playback, reinstall/restart, and language switching with already-installed English assets. Assess clarity, naturalness and long-session comfort separately. Do not select a default solely from a short demo or a parameter count.

## Primary sources checked

- [Piper Dmitri model card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/dmitri/medium/MODEL_CARD), [Denis model card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/denis/medium/MODEL_CARD), [Ukrainian model card](https://huggingface.co/rhasspy/piper-voices/blob/main/uk/uk_UA/ukrainian_tts/medium/MODEL_CARD).
- [Piper Irina model card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/irina/medium/MODEL_CARD), [Ruslan model card](https://huggingface.co/rhasspy/piper-voices/blob/main/ru/ru_RU/ruslan/medium/MODEL_CARD).
- [Sherpa Russian model samples](https://k2-fsa.github.io/sherpa/onnx/tts/all/Russian/index.html), [Ukrainian model samples](https://k2-fsa.github.io/sherpa/onnx/tts/all/Ukrainian/index.html), [Ukrainian package and Android examples](https://k2-fsa.github.io/sherpa/onnx/tts/all/Ukrainian/vits-piper-uk_UA-ukrainian_tts-medium.html).
- [Kokoro model card](https://huggingface.co/hexgrad/Kokoro-82M), [Kokoro voice list](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md).
- [Kitten v0.8 Nano int8 model card](https://huggingface.co/KittenML/kitten-tts-nano-0.8-int8), [current Sherpa Kitten packages](https://k2-fsa.github.io/sherpa/onnx/tts/kitten.html), [Kitten source and family distinctions](https://github.com/KittenML/KittenTTS), [KittenTTS 2 weight license](https://huggingface.co/KittenML/kitten-tts-2/blob/main/LICENSE.md).
- [Kitten v0.8 Mini model card](https://huggingface.co/KittenML/kitten-tts-mini-0.8), [Kitten v0.8 Micro model card](https://huggingface.co/KittenML/kitten-tts-micro-0.8).
- [Chatterbox Nano model card](https://huggingface.co/ResembleAI/chatterbox-nano), [Chatterbox model repository](https://huggingface.co/ResembleAI/chatterbox), [Chatterbox source and language list](https://github.com/resemble-ai/chatterbox).
- [Qwen3-TTS source](https://github.com/QwenLM/Qwen3-TTS), [0.6B Base model card](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-0.6B-Base).
- [Pocket TTS source](https://github.com/kyutai-labs/pocket-tts), [model terms](https://huggingface.co/kyutai/pocket-tts), [voice prompts](https://huggingface.co/kyutai/tts-voices), [Sherpa Pocket documentation](https://k2-fsa.github.io/sherpa/onnx/tts/pocket.html).
- [Supertonic archive](https://github.com/supertone-oss-archive/supertonic), [Supertonic 3 model card](https://huggingface.co/supertone-oss-archive/supertonic-3).
- [RHVoice source](https://github.com/RHVoice/RHVoice), [RHVoice platform overview](https://rhvoice.org/), [Ukrainian voices](https://rhvoice.org/uk-voices/).
