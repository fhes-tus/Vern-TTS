# Verified follow-up review — 1 October 2026

The supplied review found real issues. This records the current changes; earlier review files remain historical snapshots.

| Claim | Verified behavior and repair |
| --- | --- |
| Neural WAV export missing | True. Kokoro/Piper now stream bounded sentence PCM to WAV through `NeuralAudioExporter`. Selected voice/rate/pitch are retained; cancellation removes incomplete files. Mixed Android/neural casts fail explicitly rather than substitute voices. |
| Secondary mutations rewrite the library | True. Rename, delete, favorite, collection and clear progress now use targeted SQLite operations under the shared lock. Playback saves progress without loading the library. Progress widget refreshes coalesce over five seconds. Explicit restore/replacement remains a bulk operation. |
| Main-thread ViewModel startup I/O | True. Initial state is storage-free, the repository is lazy, and startup loads settings/study/library data on the I/O dispatcher. MainActivity reads the appearance value directly to preserve the first-frame theme, without constructing a repository for database/credential migration. Composition defers repository access and shares the ViewModel instance. |
| Deleted bundled book returns | True. Samples initialize once and existing used libraries are not reseeded. Completed migration also prevents an empty SQLite library from resurrecting stale preference JSON. |
| Every emission rebuilds navigation from 24 flags | True. StateFlow interception is removed. `withVisibility` changes a screen and its history together. Ordinary progress/settings emissions leave history unchanged; visibility fields remain for UI compatibility. |
| Original EPUB/Word holds whole archives/images | True. File-backed indexes load resources on demand with resource bounds and a compressed-image cache. Word creates one block DOM at a time. Images decode off the UI thread with downsampling. Document text models remain resident. |
| Plaintext credentials and inaccurate backup policy | True. Credentials migrate to device-bound Android Keystore AES-GCM storage, excluded from both backup formats. Legacy plaintext is removed after successful storage. The policy describes enabled Android backup accurately. |

## Pitch

Voice configuration does not itself reset Android pitch. Problems included persistence/restart races during gestures, explicit Play parameters being overwritten by document memory, and whole-utterance punctuation pitch changes. Gestures now update the UI immediately and apply the latest settled value. Ordinary Play restores the book's saved rate/pitch; explicit controls and a pending fresh gesture take precedence. This includes the brief delay before a gesture is persisted. Punctuation normalization remains, but question/exclamation cues no longer raise pitch across the entire sentence. Natural question cadence still depends on the Android voice.

Kokoro/Piper previously ignored pitch at synthesis. Generated PCM now uses resampling and waveform-aligned overlap/add to change pitch while preserving duration. Playback and WAV export share this path; AudioTrack does not apply pitch again. Frequency/duration tests pass. Human listening remains necessary, particularly for extreme settings and individual voices.

## Outlines, details and original view

- The Table of contents title uses a Material vector icon. Inference rejects bullet/checklist items and adjacent short numbered lists, retaining numbered headings separated by body text. PDF native bookmarks retain authored labels and destinations; its fallback shares the tightened text outline logic.
- Details prefer curated descriptions or an abstract. Other files receive up to three representative complete sentences sampled across stored text, with a source label. Optional AI summarization is an explicit action with the configured API; text is not sent automatically. Extractive output cannot guarantee a novel's plot synopsis.
- PPTX follows presentation relationships for slide order and speaker notes. Supported positioned objects preserve source coordinates, aspect ratio, basic typography, alignment, colors and rotation. Groups, charts, tables, cropped images, complex geometry and inherited-only placement retain a readable fallback. Theme-dependent palettes, unsupported image backgrounds and uncertain/low text contrast also use the fallback, keeping slide text readable. Decoded slide images have a total pixel budget.
- Word/EPUB use the available viewport, stable lazy items, source image positions where available, selectable text and horizontally scrollable Word tables. Word pagination remains a reading approximation, not Microsoft's typesetting engine. Legacy PPT remains a text preview.
- Optimizations address background loading, bounded media, widget coalescing, explicit navigation, stable gesture handlers and visible pressed states. No blanket animation-duration change was made.

## Notes and remaining boundaries

Notes fixes cover stable IDs, ordered autosaves, disk-commit confirmation, saved-state drafts, bounded undo/redo, checklist/attachment snapshots, current-draft sharing, media copying and ZIP note-media backup. Removing a sentence-note recording now persists its removal rather than preserving the previous audio path.

Still open: mixed-engine cast export; neural RSVP narration; portable sentence-note/quiz media in backups; full Word/PowerPoint native layout fidelity; and larger dedicated-notes features such as trash, notebooks, revision history and collaborative sync. These are not claimed as completed fixes.

## Validation

The final JVM suite passed 350 tests. The full phone suite passed all 38 tests on the Samsung SM-A175F using the isolated `.checks` application, with zero failures, errors or skips. Coverage includes rapid pitch gestures, normal Play restoring book memory, pending/explicit pitch controls, imports, reported Sherlock columns, exact selection, notes/attachments/restore, outlines/page jump, Word/PowerPoint canvas navigation, concurrent library edits and credential migration. In the warmed 50-page Sherlock fixture, the first reader opened after 610 ms while extraction continued; this is not a timing guarantee for every full PDF or OCR import. An earlier run was interrupted when Google's dynamically loaded language-identification module updated and killed the instrumented process; the rerun completed successfully. Neural WAV lifecycle checks use a deterministic substitute engine. Real-model export and human pitch listening remain acceptance checks. `git diff --check` passed. Installation is recorded in `IMPLEMENTATION_PROGRESS.md`.
