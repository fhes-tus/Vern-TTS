# Veritas Reader Android: implementation review and repair plan

Reviewed 1 October 2026 against the current working tree, including existing uncommitted changes.

## Assessment

The app has substantial working functionality and useful defensive engineering. The main weakness is that features share mutable state and index-based identities without sufficiently explicit contracts. Individual components can work while their combination fails: importing while listening, editing while notes exist, switching voices while synthesis runs, or restoring a library containing media.

Prioritize data integrity and playback correctness before expanding the feature set. A wholesale rewrite is unnecessary. Introduce clearer ownership and transactional boundaries around the existing implementations in small steps.

This is a source review with the existing JVM tests executed. It is not a device certification or an exhaustive review of every line. Findings marked **confirmed** follow directly from code; **risk** identifies a plausible failure requiring a targeted reproduction. Android is the primary scope; the desktop module was inventoried but not comprehensively audited.

Validation: `:app:testDebugUnitTest --offline` completed successfully: **40 suites, 278 tests, zero failures/errors/skips**. No emulator, physical-device playback, connected tests, release packaging, or lint execution was performed. The wrapper initially needed its distribution and required network approval; the subsequent test run succeeded.

## What is worth preserving

- Shared EPUB/DOCX parsers and the central reader text model reduce disagreement between views.
- AudioTrack writer bookkeeping and deferred native-engine release address serious teardown hazards.
- Native synthesis is serialized in the active audio buffer; background lookahead is bounded by sentence count.
- PDF import uses chunks and mixed PDFBox memory/storage management.
- Voice extraction includes canonical-path containment and package completeness checks.
- Sentence selection normalization maintains original offsets through Unicode folding.
- Parser, model, indexing, and reliability logic have a meaningful existing unit-test baseline.

These safeguards should survive refactoring. Their presence does not establish correctness of the surrounding asynchronous workflows.

## Priority findings

### R01 — Backup replacement can destroy the existing library before successful restore

**P1, confirmed.** `DocumentRepositoryBackup.kt:208` (`restoreBackupJsonWithMap`) checks only JSON parsing and a documents array before deleting current text, originals, and covers in replacement mode. It then writes text and saves multiple unrelated stores sequentially. Disk failure, malformed later sections, or interruption can leave a partially restored library. ZIP originals/covers are installed after metadata, and copy errors are swallowed.

**Plan:** Validate a versioned manifest and every record first. Stage all files, validate references and available space, then commit database changes and publish files with a recovery journal. Preserve the old library until the operation can be recovered or rolled back. Return asset-copy failures explicitly.

**Acceptance:** Inject failures before and after each commit boundary; reopening the app yields either the complete previous library or the complete restored one. Invalid backups never change live data.

### R02 — Backup identifiers become filesystem paths without containment validation

**P1, confirmed.** `DocumentRepositoryBackup.kt:234` accepts an incoming document ID and derives `"$newId.txt"`, then writes it under `docsDir`. An ID such as `../unexpected` escapes that directory. Restored `originalFileName` metadata is also accepted, while `DocumentRepository.originalFile` joins it to the originals directory. Stripping ZIP entry basenames does not validate these JSON-derived paths.

**Plan:** Allocate internal UUIDs independently of external IDs; use the ID map for relationships. Validate every restored filename and canonical destination against its owning root. Reject unsafe metadata before mutation. Add entry-count, expanded-byte, and individual-file limits; JSON/ZIP reads currently have no restore-size ceiling.

**Acceptance:** Traversal IDs, unsafe original filenames, oversized archives, and malformed schemas are rejected without writes outside staging.

### R03 — The advertised full backup is incomplete

**P1, confirmed.** `buildBackupJson` includes flashcards and general notes but omits quizzes. `writeFullBackupZip` adds only originals and covers; voice-note and note-media files referenced by annotations/notes are not bundled or relocated. Monthly reading time is exported without the month identifying that data. Replacement mode still merges tracker/monthly-time data, and several other sections preserve existing entries, so replacement has inconsistent semantics.

**Plan:** Define backup coverage explicitly: documents, originals, covers, notes and their attachments, voice clips, quizzes, flashcards, histories, settings, and dated analytics. Represent attachment references with portable IDs. Define merge versus replacement for each store and version the format. Include per-document voice memory if it is part of the promised settings backup.

**Acceptance:** Restore to a clean installation and open every attachment; quiz history and dated statistics survive. Repeating a merge is idempotent. Replacement consistently removes absent data according to the documented contract.

### R04 — Persistence rewrites entire collections and permits lost updates

**P1, confirmed mechanism; concurrent loss needs reproduction.** `DocumentRepository.kt:1023` loads all documents to update one progress field. `saveDocuments:1212` replaces every database document, mirrors the full JSON list, and refreshes widgets. Its lock covers saving, not the preceding read-modify-write. Import, editing, and service progress can read different snapshots and later overwrite each other's changes. SQLite errors are swallowed before the JSON mirror is updated, allowing stores to diverge.

**Plan:** Use targeted database mutations for progress, title, import append, and document deletion. Put dependent reads/writes inside one transaction. Make SQLite authoritative after an explicit migration; retain legacy JSON as an import/recovery format rather than a second continuously written authority. Surface write failures. Coalesce widget updates.

**Acceptance:** Concurrent append/progress/title updates retain all three changes. Progress updates touch only the intended document. Failed database writes produce a visible error and no false success.

### R05 — Playback keeps a stale document snapshot during progressive import

**P1, confirmed.** `PlaybackService.loadDocument:292` treats a matching document ID with nonempty chunks as already loaded. The worker publishes the first 25-page segment and later appends text; the UI reloads on completion, but the service has no content-revision check. Service progress saves its old `chunks.size` back into metadata. A listener can reach the end of the initial segment while further pages are available, or metadata can regress.

**Plan:** Introduce document content revisions and explicit import states. Notify the playback coordinator when a revision is published. Reload/rebase by a stable anchor, and distinguish “end of available text” from “document complete.” Do not let playback mutate extraction-derived chunk counts.

**Acceptance:** Listen to a PDF larger than 25 pages while import continues; new pages become available without restarting from the beginning or reducing stored counts.

### R06 — Import failure and cancellation can publish duplicate or unfinished readings

**P1, confirmed.** In `DocumentImportWorker.kt:185`, an exception from chunked extraction falls through to generic extraction even after the worker has created a partial document. That partial item is neither reused nor removed. The broad inner catch also catches cancellation; the outer cancellation catch returns failure rather than propagating it. Cancellation checks are not explicit between page chunks.

**Plan:** Persist one import job/document identity with a checkpoint. Fallback must continue or replace that same job, not create another reading. Rethrow cancellation, check it at page boundaries, and expose failed/partial/retry states. Stage transient URI content when persistent permission cannot be obtained.

**Acceptance:** Fail extraction after the first committed segment, retry, and cancel during OCR: one library item remains with an accurate state and no success notification after cancellation.

### R07 — Neural playback completion lacks a session identity

**P1, risk with concrete missing guards.** `PlaybackService.speakCurrent:525` supplies a neural completion callback that calls `advanceAfterSection` against current shared state; it does not capture and validate the original utterance/document/revision. `VeritasAudioBuffer` cancels jobs on flush, but native synthesis is noninterruptible and there is no generation check around cache insertion. A seek/settings change can therefore race an old result. Cache keys contain only sentence index, with rate/pitch stored separately.

**Plan:** Give every playback generation an immutable identity containing document revision, voice/configuration, and seek epoch. Validate it before publishing PCM, acquiring output, or handling completion. Capture the utterance identity in callbacks. Serialize playback commands through a coordinator; keep native resource guards.

**Acceptance:** A fake engine blocked during synthesis cannot publish audio or advance state after pause, seek, document switch, or voice change. Repeated seek/play commands never produce two active writers.

### R08 — AudioTrack completion can report success after incomplete output

**P1, confirmed.** `VeritasAudioBuffer.kt:161` ignores the return value of `track.write`. The wait loop exits on timeout or `isPlaying == false`, then returns `true` without proving all PCM frames were written and heard. This can advance past truncated audio. Track construction is outside the playback try/catch, so output initialization exceptions can bypass completion reporting.

**Plan:** Handle partial/error writes and validate track initialization. Track submitted and rendered frames with a monotonic clock and explicit outcomes: completed, cancelled, interrupted, failed. Only completed output advances the reading. Bound recovery retries.

**Acceptance:** Fake output errors, partial writes, stalled heads, and initialization failures never mark a sentence complete; recovery does not skip text.

### R09 — Live narration, WAV export, and RSVP use different speech paths

**P1, confirmed.** Live playback chooses Kokoro/Piper directly. `AudioExportManager.kt:168` always creates Android `TextToSpeech`, even when settings contain app-local neural engine identifiers. `RsvpSpeedReader.kt:138` creates default system TTS. Exported voice and RSVP narration therefore do not reliably match Voice Studio. RSVP advances using a timer rather than spoken word callbacks. `SystemTtsEngine` and `SynthesisQueue` exist but have no construction callsites in the Android source search.

**Plan:** Use a common voice resolver and narration specification for listening/export/preview. Preserve platform direct speech for word callbacks where useful; a common contract need not force every engine through WAV files. Add engine capabilities such as PCM synthesis, word timings, pitch, export, and offline availability. Route RSVP through the coordinator or explicitly describe timed visual pacing independent of audio.

**Acceptance:** Choose one Piper and one Kokoro voice and compare live, selection, preview, and exported output. Unsupported capabilities have clear UI behavior. Audit unused adapters before removing them.

### R10 — Neural failure does not provide the documented fallback

**P1, confirmed.** `KokoroTtsEngine` declines native allocation in critically low memory. The service's neural failure callback reports that the voice needs downloading rather than retrying system TTS. `PlaybackTtsSetup` falls back when no voice is installed, but installed-yet-unready/runtime-failed engines are a separate case. Kokoro speaker-count mismatch is logged but still allowed to synthesize a clamped speaker ID.

**Plan:** Resolve readiness and failure reasons before play. Distinguish missing files, incompatible package, memory pressure, and output failure. Use a bounded fallback chain without silently changing the user's saved preference. Reject incompatible speaker tables.

**Acceptance:** Installed but unready models fall back with an accurate reason and no retry loop; incompatible voice packages never speak under a misleading voice label.

### R11 — Position mapping does not account for pronunciation replacements

**P1, confirmed.** `speakCurrent` applies pronunciation rules to the spoken string while retaining raw text as `activeChunkSpeechText`. System `onRangeStart` adds only a base offset to speech offsets. Length-changing replacements shift highlighting and persisted resume coordinates. Length-preserving glyph sanitation does not solve pronunciation expansion.

**Plan:** Produce speech text with a source-offset map for every transformation, including resume slicing, pronunciation rules, tables, and sanitation. Use this map for highlighting and resume. Expose sentence-level progress when an engine provides no trustworthy word timing; neural playback currently has no word-range updates.

**Acceptance:** Expand an acronym, replace a long name, and resume mid-sentence; visual and persisted anchors still point to the correct source words.

### R12 — Audio focus behavior differs between engines

**P1, confirmed.** `handlePlay` logs denied audio focus and continues. `PlaybackAudioFocusAndSensors.kt:23` handles ducking by slowing system TTS, with no corresponding neural output-volume handling. This is a change of reading pace, and it ignores active AudioTrack output.

**Plan:** Define one focus policy for all output paths. Defer/pause when focus is denied; implement ducking as output gain or choose consistent pause-on-interruption behavior. Centralize focus acquisition and release with playback state transitions.

**Acceptance:** With another audio owner and simulated focus changes, both engines honor the same policy and resume exactly once when appropriate.

### R13 — Neural feature parity is incomplete

**P2, confirmed.** The neural branch returns before slide/table `leadingSilenceMsFor` handling. Full-cast voice selection in `speakCurrent` operates on platform `tts`; the neural buffer is selected from saved voice settings. Prebuffering applies the current sentence's effective rate/pitch to all upcoming sentences, although narration analysis may assign different values. Background listening accounting is invoked in the system onDone path, not the neural callback.

**Plan:** Build per-utterance narration specifications containing speaker, timing, gain, and transformed text. Render cadence consistently and attribute time through shared start/stop events. Validate each sentence's settings before prefetch. Present full-cast limitations when the selected engine cannot honor them.

**Acceptance:** Table/deck cadence, character transitions, and background-time reporting behave consistently in both speech paths.

### R14 — Voice installation is staged but not safely replaceable or concurrent

**P2, confirmed.** `VoiceModelManager.downloadVoice:366` uses archive/staging names based on engine type, so different Piper downloads share paths. No manager-level mutex was found. Installation deletes the old voice directory before `renameTo`; a rename failure loses the working package. Completeness checks validate presence/size but not an expected digest.

**Plan:** Use unique per-job staging paths, serialize publication by package, verify manifest/digest, retain the old directory until replacement succeeds, and coordinate delete/install with active engine ownership. Treat shared Kokoro assets as one package with multiple speaker selections.

**Acceptance:** Two concurrent downloads, a failed rename, cancellation, and delete-during-playback cannot corrupt another voice or remove the last working installation.

### R15 — Text edits can silently move annotations to unrelated content

**P2, confirmed.** `DocumentRepository.updateDocumentText:481` reanchors unchanged sentences using normalized text and nearest index. If the original sentence disappears, it keeps/clamps the index without marking uncertainty. Progress is clamped rather than anchored. Duplicate text is resolved by proximity alone. Direct `writeText` precedes metadata/annotation persistence, and editing also marks partial documents complete.

**Plan:** Assign stable block/sentence identities and revision-aware anchors with quote/context fallback. Mark unresolved annotations for repair instead of attaching them confidently to another sentence. Atomically publish edits with metadata and anchor changes. Preserve import completion state independently of editing.

**Acceptance:** Insert/delete text before notes, edit an annotated sentence, and use repeated sentences; notes either retain the right target or visibly need reattachment. Interrupted edits recover consistently.

### R16 — Original-page synchronization is still partly synthetic

**P2, confirmed design limitation.** `ReaderTextModel.extractPages:207` divides markerless text by approximate character counts when page count is known. `PdfSelectionLocator` narrows by page then falls back to progressively shorter query prefixes. This can choose the wrong repeated passage, and artificial pages can distort parts and TOC navigation. Existing Unicode normalization is useful but is not geometry-based alignment.

**Plan:** Store page IDs and source spans as extraction metadata independent of optional visible markers. Preserve OCR/text geometry where available. Record mapping confidence; ambiguous matches should offer candidates or a preview. Keep estimated pagination clearly distinguished from source pages.

**Acceptance:** Fixtures with repeated headers, blank pages, cropped ranges, multi-column order, ligatures, and OCR errors resolve to the expected source page or report uncertainty.

### R17 — WAV export memory usage scales with total audio length

**P2, confirmed.** `AudioExportManager` reads each WAV into arrays and retains parsed parts before writing the final WAV. Long books can require memory comparable to total decoded PCM. Cancellation may return a partial file as `ExportResult`, without a field distinguishing partial completion; the normal result reports input chunk count rather than actual synthesized part count.

**Plan:** Stream compatible PCM chunks to a temporary output and finalize its header; use a recoverable export job with explicit partial/cancelled status and accurate counters. Use duration-aware synthesis deadlines and cleanup incomplete output on failures.

**Acceptance:** A long export uses bounded memory, survives defined resumable interruptions, and labels partial files accurately. Format mismatch fails without presenting an incomplete file as complete.

### R18 — Study scheduling promises a shorter “Again” interval than it implements

**P2, confirmed.** `StudyAssistant.kt:95` schedules Again at one full day; `previewNextInterval` displays `<1d`. The existing parser/scheduler test explicitly expects that misleading preview. The scheduler has no immediate relearning step.

**Plan:** First make the displayed interval truthful. Then choose a product policy for short relearning steps, review-day boundaries, and lapse handling. Store review events separately from current scheduling state if analytics and synchronization require them. Add source references and an approval/edit step for generated study material.

**Acceptance:** Every displayed next interval matches actual due time; failed recall reappears according to the chosen policy. Backup preserves quizzes and scheduling history defined by the product.

### R19 — Documentation contradicts current privacy and architecture behavior

**P1 for privacy accuracy; P2 for other claims, confirmed.** README says all AI study aids are on-device and data never leaves the device; `GeminiStudyService` sends document excerpts to configured remote providers. `PRIVACY_POLICY.md:31` says `allowBackup=false`; current manifest has `allowBackup=true`, and backup rules include preferences, database, and extracted files. API credentials are stored in ordinary preferences that those rules include. README also claims 100% declarative UI, but native Android views/PDF fragments are used; advertised speed reaches 2.5x while service/session clamp to 2.0x. Known limitations still describes v2.4.0.

**Plan:** Rewrite feature/privacy claims around actual capabilities. Explicitly disclose optional cloud AI and system backup behavior. Exclude credentials from backup and store them through a suitable credential boundary. Add a visible data-destination description at AI configuration/use. Make engine-dependent capabilities and speed limits discoverable and consistent.

**Acceptance:** Documentation, onboarding, settings, manifest, and backup rules agree. A backup inspection contains no API credentials. Do not infer legal/store compliance from this source review.

### R20 — Catalog download handling treats any nonempty response as a book

**P2, confirmed.** `BookCatalogBrowserDialog.kt:126` infers format mainly from URL and provided MIME type, defaults to PDF, copies the whole response without a byte ceiling, and imports any nonempty result. HTML challenge/error pages can be downloaded as books. Errors collapse to null without an actionable message. Browser file/content access is disabled, which should be preserved.

**Plan:** Move downloads into an owned repository/job with response/status/content validation, size limits, unique filenames, cancellation, progress, and recoverable errors. Sniff supported file signatures before import. Keep optional catalog browsing separate from the core reading journey.

**Acceptance:** Redirects, login/challenge HTML, unsupported formats, oversized downloads, cancellation, and lost connectivity produce accurate UI outcomes and no corrupt library item.

### R21 — Startup and UI ownership make feature interactions difficult to reason about

**P2, confirmed architecture concern.** `ReaderViewModel` loads multiple stores synchronously during construction, then reloads many in its IO initialization. It wraps `MutableStateFlow` to synchronize navigation and exposes a broad `updateState` entry point. Many extension files divide source length while still sharing one large state owner. Startup recreates the bundled book whenever its title is absent, so deliberately deleting it does not persist across starts.

**Plan:** Load a minimal initial state, then expose independent library/reader/study/settings flows. Replace navigation interception with explicit route transitions. Keep playback service authoritative for playback state. Seed sample documents using a migration/installation marker, not title absence. Review dialog ownership and accessibility through actual task journeys.

**Acceptance:** Deleted samples stay deleted; startup time is measured on a populated library; navigation/back behavior is deterministic during import, playback, and modal transitions.

### R22 — Existing tests leave the highest-risk workflows uncovered

**P1 as a delivery gap, confirmed scope.** The 278 passing tests are valuable but mostly exercise pure logic. `PlaybackPipelineLogicTest` checks lookahead ranges and cadence, not actual buffer races. `VeritasDatabaseModelAndMigrationTest` primarily tests JSON/model compatibility rather than executing SQLite migration. Only two Android test files were inventoried.

**Plan:** Add fault-injected repository/restore tests, playback coordinator tests with controllable engines/output, real SQLite migration tests, and a small instrumented workflow suite. Test fixes against observable behavior, not implementation structure. Establish a device matrix for system/Piper/Kokoro, low memory, Bluetooth, backgrounding, process death, and notification settings.

**Acceptance:** Regression tests reproduce R01–R12 before their respective repairs, then pass. Device validation establishes which background/engine claims can be shipped.

## Implementation sequence

| Stage | Work | Exit condition |
|---|---|---|
| 1: Protect existing data | R01–R04, R19 credential/claim corrections | Transactional restore and targeted mutations; complete clean-install backup round trip; no secrets in backups |
| 2: Stabilize import and playback ownership | R05–R08, R10–R12 | Document revisions, explicit import states, playback generations, accurate output completion, consistent focus/fallback |
| 3: Make features consistent | R09, R13–R18, R20 | Shared voice/narration contract, durable anchors, source-page mapping, bounded export, reliable downloads |
| 4: Simplify product and UI | R21, remaining documentation | Clear reading/listening/study workflows; measured startup; explicit navigation and capability-aware controls |
| Throughout | R22 | Add targeted regressions with each fix; run existing tests and relevant device checks |

Recommended first implementation slice: safe backup staging/path validation plus a clean-install round-trip test. Next, replace progress's whole-library rewrite with a single-row update. Then establish content revisions and playback generation guards. These changes remove the most expensive failure modes while preserving visible features.

## Architecture direction

Keep the current UI and parsers while introducing four narrow boundaries:

1. **Document store:** authoritative metadata, revisioned content, source spans, and durable annotation anchors.
2. **Import coordinator:** one job identity, staging/checkpoints, bounded resources, and explicit partial/failed/complete states.
3. **Playback coordinator:** serialized commands and engine events; immutable voice/narration specification; generation-aware PCM/output ownership.
4. **Backup/export services:** portable manifests, streamed assets, validation, and recoverable publication.

Avoid introducing a new framework merely to organize files. File splitting alone cannot fix shared-state ownership. Stabilize the contracts first, then move code behind them gradually.

## Remaining verification

- Reproduce asynchronous risks on controlled fakes and real devices; source inspection alone does not establish frequency.
- Inspect each note-media storage path when implementing full attachment portability.
- Review widget transitions, native reader accessibility, reminder behavior, and release signing/packaging on actual target devices. They were inventoried/read selectively, not certified here.
- Debug build currently selects release signing despite comments promising builds without credentials; separate debug signing and document the pinned local JDK requirement before establishing clean-machine builds.
- Treat provider/model catalogs as configurable data. This review did not query external APIs or certify current model availability.
- Audit the desktop module separately before promising cross-platform backup compatibility.

No existing application code was changed by this review.
