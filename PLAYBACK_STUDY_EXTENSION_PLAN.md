# Playback and study extensions — stage 11

Status: design completed against the current implementation on 4 October 2026. This stage defines the remaining extensions; it does not claim that neural RSVP, mixed-engine casts or portable sentence-note media have shipped. Stage 12 is excluded at the user's request. Export/listening acceptance in stage 13 remains paused.

## Current behavior and compatibility

| Area | Current implementation | Remaining boundary |
| --- | --- | --- |
| Narration | Saved role profiles; narrator/default dialogue and named speech tags; preview uses the selected reading engine and base delivery. Android and installed neural character voices work within their respective engine families. | A single sentence can include narration and dialogue; the chosen role applies to the sentence. Profiles do not store a separate Android engine package. |
| Neural audio | `PlaybackService` and `VeritasAudioBuffer` synthesize bounded sentence PCM. Cache entries include text, rate, pitch and generation. Changing the local voice rebuilds its engine. | Cast playback suppresses look-ahead generated with another role's settings. Repeated model switches can add loading latency. |
| RSVP | `RsvpSpeedReader` has a visual word timer and optional Android sentence speech. | The visual timer is not an audio-position clock. Neural speech and reliable word-level audio synchronization are not implemented. |
| Cast export | Existing export paths use Android or local neural voices. | Mixing Android and neural voices must fail explicitly rather than substitute a voice. Do not present this as supported. |
| Reading notes | `ReaderAnnotation.audioPath` stores a device-local recording reference. | A reference is not a portable recording in another installation. |
| Quizzes | `QuizSet` and `QuizQuestion` store text and results. | There is no typed owned media attachment model for question audio/images. |
| Standalone notebooks | A note can have multiple notebook IDs. Assignment, removal and notebook deletion preserve the note. Editor autosave and copies include assignments. | Collaborative sync is separate work; notebooks are label-like organization, not physical folders. |

## 1. One playback plan for a passage

Introduce a speech-plan builder before adding more playback surfaces. Each immutable segment must carry document/revision identity, original sentence and UTF-16 source range, prepared speech/source map, role ID, engine family/package, voice ID, rate, pitch, language and leading pause. Classify the original text before pronunciation replacements. Keep source punctuation and displayed text intact.

The builder first supports the existing sentence-based roles. A later segmenter can split quoted speech from surrounding narration without changing saved sentence indexes. Ambiguous speaker tags use the default Dialogue role; expose the detected role in the preview and allow overriding a sentence. Do not infer a gender or promise dramatic acting from a voice label.

Validate all assigned voices before playback/export. Missing local models offer Download or an explicit reading-voice fallback. Missing Android engines/voices offer Voice Studio. Persist a fallback only if the user chooses it; retain unavailable assignments so a restore does not lose the cast. Version profile serialization to add engine package/family while preserving existing profiles and multipliers.

## 2. Neural RSVP

Move audio out of the RSVP composable into a session using the same speech plan. Opening RSVP pauses the book session once; closing it releases RSVP audio and returns to the existing reader position without starting two players.

Use original-source tokens with UTF-16 ranges. Android range callbacks drive the highlighted word when supported. For neural audio, use actual word timings if the engine exposes them. If it does not, show an explicitly estimated paced display derived from PCM duration; do not call a fixed WPM timer “synchronized.” Silent visual mode continues to use the WPM timer.

Define idle, loading, playing, paused, seeking, ended and failed states. Seeking cancels obsolete synthesis and invalidates the generation before preparing the requested sentence. Pause stops the visual clock and audio together. A voice/rate change re-plans from the current source location. End-of-document stops both. No word timer advances while buffering.

Store the selected mode and speed separately from the book's saved listening rate. A user can choose silent RSVP for any engine. Offer audio only when the chosen voice is ready; a failed or unsupported engine must not silently speak with Android's default voice.

## 3. Mixed-engine cast playback and export

Give a playback coordinator exclusive audio focus and one active output. Prepare the next segment without playing it, then switch output at the segment boundary. Keep Android TTS callbacks and neural AudioTrack callbacks tagged with the same session generation. A canceled, deleted or superseded book cannot publish completion into the next session.

Cache at most a small bounded set of local models/segments. Dispose native engines only after their synthesis work has stopped; never release a model while a native call is in flight. Cache keys include role voice, text revision, pronunciation revision, rate, pitch and engine version. Tests with repeated narrator/dialogue model switches must establish a safe memory bound before expanding the cache.

Export uses the same immutable speech plan. Android voices require successful `synthesizeToFile`; neural engines supply PCM. Normalize channels/sample rates before concatenation, preserve pauses, stream to a temporary file and commit only after all segments succeed. Report missing/offline-only/network synthesis limitations before a long job starts. Cancellation removes partial output and never changes live playback. Stage 13 remains the gate for device/model/export acceptance.

## 4. Sentence-note and quiz media ownership

Add versioned attachment records with stable IDs, kind, MIME type, size, optional duration, owning record and a managed relative media path. Copy picked URI content into app-owned storage; never rely on another app's temporary URI or absolute device paths in a portable archive. Keep text note/quiz IDs unchanged.

Migrate legacy `audioPath` references lazily and safely. If a file is absent, retain the note with a visible missing-recording state; never discard its text. Removing an attachment removes the association first. Delete a managed file only when no active note, Trash record, revision or quiz references it.

Extend the existing backup manifest with media IDs, relative archive entries, sizes and hashes. Restore rejects paths outside managed roots and oversized/unsupported entries, verifies files before committing references, and can restore text when media is missing with a clear result. Credentials remain excluded. Duplicate restore/copy must define attachment sharing or copy ownership explicitly.

Quiz UI should expose Add image/recording only after this storage path exists. Provide accessible labels, play/stop and missing-file recovery. Avoid embedding recordings in question text or exporting a text-only file under a media-backup label.

## Delivery gates

1. Add source-mapped speech plan and versioned profile migration; preserve present playback and role previews.
2. Build a single-session RSVP adapter; prove pause/seek/end behavior and label estimated timing honestly.
3. Implement mixed-engine coordination with bounded model ownership; measure switching on the device.
4. Add typed managed media, legacy migration and round-trip backup restoration.
5. Resume stage 13 only on the user's instruction; verify real models, exports and human pitch listening then.

Focused checks should cover cancellation during native synthesis, a missing voice, repeated phrases/ligatures and source offsets, fast seeking, engine switches, rapid AI generation/cancel/retry, notebook removal, missing media and interrupted restore. Existing phone notes, reading progress and study scores are not disposable test data.
