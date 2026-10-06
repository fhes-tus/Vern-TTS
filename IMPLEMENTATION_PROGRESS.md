# First stability batch — 1 October 2026

## Import-only follow-up — 2 October 2026

The user's new report concerns the dark import screen and unreliable batch imports. This follow-up changes only import behavior and its progress UI; the navigation/theme and original-document plans remain deferred.

- Removed both full-screen `ImportProgressOverlay` hosts and the unused overlay implementation. The browser retains a small status row, and the library retains its batch banner. Neither covers the reader or prevents Back navigation.
- The old batch loop launched every extractor concurrently and reset batch status after a fixed 1.2-second delay. It now registers the entire deduplicated selection as a persistent, sequential WorkManager chain before observing progress. This limits competing extraction work and keeps unstarted files owned by WorkManager after the Activity/ViewModel is destroyed.
- A rejected batch file records its import error while allowing the next file to run. Single-file failures retain their existing FAILED state. Cancellation still propagates. Successfully imported documents are queued before the next worker starts, preserving selection order; queue errors are reported separately without blocking later imports.
- Progress counts finished items rather than enqueued requests and distinguishes failures. Reopening the app reattaches to an unfinished batch. Ready PDF pages remain available before completion, active readers refresh as text arrives, and the library refreshes when each file finishes so completed PDFs no longer retain stale partial status.
- Original bad/incomplete copies are not automatically replaced or deleted. Available device logs did not contain the original reported batch, so the exact cause of those specific files' contents could not be confirmed; the identified concurrency, tracking, and overlay defects were verified directly in code and regression tests.

Verification: the initial targeted import suite passed all **11 Android tests**. After the final completion-refresh correction, all **four relevant Android regressions** passed again, including the newly added single-file batch/library/reader check. These are **12 distinct device tests**, with three overlapping reruns, zero failures/errors/skips, on SM-A175F/Android 16. Checks include actual Back dispatch, Activity/ViewModel destruction and reopening, failed-file continuation, duplicate selections, serial extraction, queue order, preserved original size, UTF-8 text, the final PDF page, and early reading. Process termination by the OS was not directly exercised; persistence follows the registered WorkManager chain. Reports are preserved at `tmp/verified-import-suite-11.xml` and `tmp/verified-import-final-4.xml`. Tests used `.checks` and did not modify the installed regular app's library. The final `:app:assembleDebug :app:testDebugUnitTest --offline --max-workers=2` passed with **350 JVM tests, zero failures/errors**. `git diff --check` passed. APK metadata was verified as `com.veritas.reader.preview`, and the ARM64 APK was installed successfully with `adb install --user 0 -r`. Preview had been absent from the phone before this installation; the regular app remains separate. No release or commit was made.

This batch follows `IMPLEMENTATION_REVIEW.md` and the request to check import, extracted-text display, and Android status pop-ups. The clarified pop-ups are messages about operations such as opening and importing, rather than the text-selection toolbar.

## Implemented

- Restore validates backup schemas, section shapes, document IDs, original filenames, duplicate records/ZIP assets, and bounded input sizes before metadata changes.
- Text and ZIP assets are staged. Existing text is retained until the metadata transaction succeeds. Handled failures roll back SQLite, preferences, and published files; database write failures are no longer silently accepted.
- PDF jobs use the WorkManager request's ID as their document identity. A restarted partial job replaces that same reading after full extraction; failure after partial publication does not fall through to a duplicate import. Cancellation propagates and is checked between chunks.
- PDF ranges are normalized and source-page markers remain in stored text. Blank initial segments do not publish an unreadable reading. Appends recompute sentence/character counts from stored content, and repairs preserve partial-import state.
- Reading progress updates only its SQLite row and retains extraction's current sentence count. Playback reloads changed content metadata at sentence boundaries so it can see additional pages.
- Finished import observers stop collecting. Concurrent jobs have separate notification IDs and tracked pending state. An old auto-opening import loses ownership when another document is opened, an opening operation is dismissed, or the reader returns to the library.
- Import status toasts are handled at the app root, consumed once, replace previous status toasts, and are cancelled on backgrounding. Returning to the Library screen no longer replays its last message. Importing does not show an incorrectly labelled opening dialog.
- Preparing/opening a document has cancellable job ownership and error handling. The opening dialog uses the requested document title, and dismissing it cancels loading.
- Reader-owned presentation spans update in place without removing Android selection/editor spans. Rendering responds to paper-tone changes; automatic scrolling pauses during selection. Pages without extracted text now show an explanation.

## Verification

- Added 15 JVM tests covering restore validation, read limits, staging/write/publication rollback, successful publication, reader-span refresh, dismissed selection offsets, and partial-state propagation into the reader model.
- Added five isolated Android tests covering rejected replacement, SQLite failure during restore, stale progress counts, partial-state preservation, and successful replacement. Their files/preferences/database are separate from the user's library.
- Final `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --offline` passed: 42 suites, 293 JVM tests, zero failures/errors. `git diff --check` also passed.
- Connected-device validation is now available on a Samsung SM-A175F running Android 16 (API 36), using the separate `com.veritas.reader.preview` app. The installed release app/library is not used by these tests.
- Added five real-device flow checks: UTF-8 import and reader loading; a 26-page PDF crossing the 25-page progressive boundary; resumed partial-import identity; unreadable-input failure/busy-state cleanup; and Android selection/editor span preservation. The UTF-8 flow also checks status consumption across returning to the library and background/resume.
- Configured `AndroidJUnitRunner`. Activity tests now launch with explicit action-free intents because the app consumes incoming intent actions, which otherwise breaks `ActivityScenario` tracking. The injected SQLite-trigger failure assertion uses Android's actual `SQLiteConstraintException`.
- Final `:app:connectedDebugAndroidTest --offline` passed on that device: 13 tests completed, zero failures/skips, `BUILD SUCCESSFUL` in 1m 18s. The five storage tests, five added device-flow tests, and three existing UI tests all passed. Report: `app/build/reports/androidTests/connected/debug/index.html`.
- These checks verify real extraction, repository state, reader models, native spans, and status lifecycle; they do not establish pixel-level visual quality or exact native-toast timing. Gradle removed the preview/test packages after the run; the installed release package remains.

## Remaining boundaries

This batch does not claim to resolve every finding in the review. Restore rollback covers handled failures within a running process. Recovery after termination between file, SQLite, and preference publication still needs a durable recovery journal; old original/cover assets are not aggressively cleaned up. Complete attachment/quiz backup coverage and consistent replacement semantics for every ancillary store remain work.

Playback content checks use file/count/partial metadata rather than a persisted content revision. Neural callback generations, engine capability parity, and pronunciation offset mapping remain separate repairs. Batch import progress can still be made more informative; scanned/encrypted documents and missing-permission cases still need device exercises. Direct toasts elsewhere in the app are not all converted to the import-status host.

Existing uncommitted work was preserved. No commit, release, or deployment was created.

## System voice punctuation — follow-up

The user reported flat question delivery with Android/system voices. Speech preparation now recognizes full-width and Arabic question marks, full-width exclamation marks, an interrobang, and common Unicode comma/colon/semicolon/full-stop variants. Normalization preserves character positions. Table-cell formatting retains terminal punctuation behind closing quotes/brackets rather than appending a statement period.

Narration Studio now exposes independent punctuation-expression enable/strength controls, with gentle default cues. At the default strength, questions use 4% slower pacing and 3% higher overall pitch; exclamations and trailing ellipses have smaller pacing cues. Existing statements retain their base delivery. Settings round-trip through the existing backup/settings format. Preview, selection reading, and system WAV export share punctuation preparation; preview now honors the calculated rate too.

These are utterance-wide controls, not a generated rising pitch contour. Natural stress and question intonation remain the Android engine/voice's responsibility. Extra expression is limited to system voices; neural playback retains its existing delivery. Stored sentence segmentation is unchanged to preserve existing annotation/progress indexes. Fixing question boundaries globally requires an anchor migration rather than silently reindexing saved books.

Added nine JVM regressions for Unicode/quoted punctuation, offset preservation, table endings, delivery controls, engine opt-out, settings compatibility, and mixed paragraphs. Added a device synthesis check for normal/expressive questions through the installed system engine.

Final `:app:testDebugUnitTest :app:connectedDebugAndroidTest --offline` passed: 302 JVM tests with zero failures/errors, and all 14 Android tests on the connected Samsung SM-A175F with zero failures/skips. Synthesis checks verify successful WAV generation, not audible question intonation. The installed release app was not updated; Gradle removed the isolated preview/test packages after the run.

## Column, illustration, vocabulary, and unfinished reliability follow-up

This section supersedes the earlier statement that restore has only in-process rollback and neural generations are unimplemented. Other remaining findings above are still open unless explicitly covered here.

### Implemented

- Reconstructed complete visual PDF rows from glyph callbacks. PDFBox can call `writeString` once per word; the previous line assumption left actual Sherlock Holmes pages undetected as columns. Gutter detection now tolerates short/unequal columns and full-width headings. Extraction reads top headers, left/right body bands, intervening full-width chapter bands, and footer in sequence. Removed a text-similarity heuristic that could discard a legitimate repeated right column. Actual source pages 13, 101 and 111 are regression fixtures, including the split-heading screenshot and a chapter transition within a page.
- PDF illustration extraction follows drawn image positions, including images in Form XObjects, with a three-image/size budget and 720 px scaling. Reliable following-text matches place images before the corresponding whole sentence, preserving source sentence indexes. EPUB/DOCX image markers use stable decoded-image indexes even when a decode fails. Ambiguous or rotated PDF illustrations retain the bottom-of-page fallback.
- Inline images render outside the native prose TextView in a fixed-size frame. Bitmap/focus/selection refreshes no longer restart the sentence auto-scroll effect, and displayed-page TextView binding follows the actual pager page. The page resolves media positions before exposing scrollable prose, preventing a newly inferred PDF anchor from inserting height during reading. Bitmap data and anchor metadata share one byte-sized LRU entry and eviction policy; metadata/empty pages have a nonzero cost.
- Google/translation web lookup carries the actual selection's global sentence index through UI callbacks. Source document/context is captured before asynchronous definition fetching/browser handoff. An ambiguous legacy string-only lookup stays explicitly unknown instead of borrowing playback's highlighted sentence. Definition failures have an explicit unavailable message. Vocabulary additions/removals mutate under one repository lock and retain remaining entries' source context/pronunciation.
- Neural PCM cache entries validate text/rate/pitch/generation. Cancelled native synthesis cannot publish into a later generation. Output serialization, partial-write handling, initialized-track checks, rendered-frame completion deadlines, cancellation ownership, and service utterance/document/index/playing guards protect advancement. The unsigned playback-head delta handles wrap back to zero. Neural playback now honors leading silence and records background listening; accurate failure messages replace the misleading download claim.
- Audio-focus denial pauses playback. Both engine paths pause for transient duck/interruption rather than changing platform reading speed.
- WAV export streams PCM through a 64 KiB buffer, validates compatible RIFF parts and frame alignment, checks cancellation, uses unique names, and publishes only the completed staged result. Selected Vern neural voices are explicitly rejected until neural export exists, avoiding silent system-voice substitution.
- Restore writes a durable file manifest and typed previous-preference snapshot before publication. A marker committed in the same SQLite restore transaction decides keep-versus-rollback when the repository reopens. Backup rules exclude recovery transactions. Tests exercise abandoned publication and committed restoration awaiting cleanup. Empty transaction parents are cleaned up too.
- The flashcard “Again” preview now accurately shows its existing one-day scheduled interval; this does not add immediate relearning.

### Final verification

- `:app:testDebugUnitTest :app:connectedDebugAndroidTest --offline` completed successfully on the final source: **46 suites, 322 JVM tests, zero failures/errors/skips; 20 Android tests, zero failures/skips**, Samsung SM-A175F, Android 16/API 36. Final combined run: `BUILD SUCCESSFUL` in 3m 52s.
- New regressions cover column geometry/sparse layouts/headings, inline source ranges, exact repeated-word selection, vocabulary persistence/removal, late generations/partial writes/stalled output/cancellation/counter wrap, streaming WAV compatibility/cancellation/truncation, and restore recovery reopening. Device fixtures exercise real PDFBox geometry, generated PDF image anchoring and rotated fallback, stable page/scroll on delayed bitmap, real-reader vocabulary selection while playback is elsewhere, and merging WAVs from the installed system engine.
- Two intermediate JVM cleanup assertions initially failed against the pre-cleanup compilation snapshot, and earlier column tests exposed the word-callback assumption. Those were corrected and the final full run above passed. The device disconnected before one intermediate run; reconnecting allowed final validation.
- `git diff --check` passed; Git emitted existing line-ending warnings. Tests used `com.veritas.reader.preview`, and Gradle removed preview/test packages afterward. The installed release and library were not updated. No commit or release was created.

### Boundaries and next priorities

Saved extracted text is not automatically replaced by the new column extractor. Existing malformed imports need fresh extraction after an updated build is installed; automatic replacement would require migrating saved sentence-based notes/progress rather than silently changing their indexes. Inline placement is conservative: rotated/ambiguous images remain below the page, and vector diagrams/oversized raster images are not all supported by the bounded extractor.

Device coverage does not certify every reader-selection/layout interaction, neural-engine stress race, audible punctuation quality, remote definition availability, or abrupt power loss. Restart journal tests model repository reopening at commit boundaries, not physical battery removal. First-load PDF performance and original EPUB/DOCX memory need profiling; there is no persisted media index yet.

Remaining priority work includes progress reload/widget overhead and targeted clear/rename/delete/favorite/collection mutations, full quiz/media/dated-statistic backup coverage, safe voice-package replacement/concurrency, pronunciation source-offset mapping, neural export/RSVP/full-cast parity, startup hydration/sample seeding, navigation transitions, and credential storage/backup exclusion with privacy-policy corrections. The supplied second review and proposed fixes are evaluated in `IMPLEMENTATION_FOLLOWUP_REVIEW.md`; its no-cache/UI-thread/dropped-rotated-image and no-journal claims do not describe the final code.

### Debug preview installed for user testing — 1 October 2026

At the user's request, `:app:assembleDebug --offline` succeeded and the current ARM64 debug APK was installed on device `RZGL403LCNW` using user 0. The launcher name is **Vern Preview**, package `com.veritas.reader.preview`, version 2.5.1 (39), with `DEBUGGABLE` confirmed. MainActivity launched successfully and remained the foreground activity with a live process. The release package `com.veritas.reader` remains installed alongside it. The preview uses its own library; no release-library data was copied or replaced. Fresh imports into the preview exercise the revised PDF extractor.

### Progressive import barrier reported during user testing — 1 October 2026

The user imported the 987-page Sherlock Holmes book in Preview and saw a blocking “Importing” popup until completion, despite the background-import toast. Phone logs showed a successful worker completion rather than an extraction failure. The UI displayed its full-screen import overlay for the entire `importInProgress` interval, covering even a partial reader already open underneath it. Previous device tests waited primarily for final import completion and missed this interaction.

The first batch is now five source pages rather than 25; later batches remain 25 pages. The worker publishes ready-page/character progress after each append. The foreground waiting state ends once ready pages are handed to the reader, while extraction retains its background running state. Ready batches refresh the same active reader without reopening it, resetting playback, or repairing/persisting an obsolete text snapshot. Leaving for the library or opening another document clears the foreground wait and automatic-opening ownership, so background completion cannot block navigation or reopen the book.

Added real-device regressions using the first 50 pages of the actual Sherlock PDF: the first reader appears while the worker remains RUNNING, the Compose “Importing” overlay is absent, later pages arrive before completion, final text preserves the initial prefix, and returning to the library stays usable through completion. The initial targeted run passed: first five pages published after **2,754 ms** and the reader opened after **3,507 ms** on the connected Samsung. This is a 50-page fixture measurement, not a timing guarantee for every complete PDF or OCR import.

Device checks now use `-PdebugApplicationIdSuffix=.checks`, a separate `com.veritas.reader.checks` package. This preserves the user's newly imported Preview library when Gradle removes its test installation. Default debug builds still use `.preview`.

Final validation: `:app:testDebugUnitTest :app:connectedDebugAndroidTest --offline -PdebugApplicationIdSuffix=.checks` passed in 4m 38s: **322 JVM tests and 22 Android tests, zero failures/errors/skips**. In the warmed final suite the Sherlock reader opened after **811 ms**; the separate earlier targeted measurement remains the more conservative 3,507 ms. `git diff --check` passed with existing line-ending warnings. The default `:app:assembleDebug --offline` then succeeded in 55s, producing `com.veritas.reader.preview` APKs for installation.

Installed the corrected ARM64 Preview APK with `adb install --user 0 -r` (Success) and launched MainActivity (Status: ok). Preview's stored readings remain, including the reported Sherlock worker's `8eaa9fb6-68a3-43e9-a179-cf47aa9c507f.txt`. The release app remains alongside Preview; the isolated `.checks` package was removed by Gradle after testing.

### Reader, notes, technical-debt and original-view follow-up — 1 October 2026

The subsequent user reports and seven-point review were checked against the code. Current changes and limitations are recorded in `TECHNICAL_DEBT_RECHECK.md` and `FOLLOWUP_IMPLEMENTATION_NOTES.md`; the older unfinished-work paragraphs above are historical snapshots.

This batch covers the playback self-duck/Read from Here failure, exact selection and highlight stability, page-label/blank-page cleanup, responsive media/file loading, stricter inferred outlines with page jump and Material icon, pronunciation source mapping, note drafts/autosave/undo/attachments, sentence-recording removal, targeted library mutations, coalesced reading-widget updates, startup hydration/sample seeding/navigation, credential migration, pitch and neural WAV export. Details now use descriptions/abstracts or bounded representative sentences with optional explicit AI summarization. EPUB/Word original media loads on demand, and PPTX respects presentation order, actual notes relationships and supported source geometry with readable fallbacks.

Validation passed **349 JVM tests** and a full **37-test Android suite** on SM-A175F/Android 16, with no failures/errors/skips. The final startup/theme bootstrap change then passed all nine applicable launch/navigation/playback/settings tests again; those nine overlap the full suite and are not additional distinct tests. The full phone report is preserved at `tmp/verified-phone-full-suite-37.xml`. `git diff --check` passed. All connected tests used `.checks`, preserving the user's Preview library.

Real installed-model neural export and human pitch listening remain acceptance checks; deterministic engine/PCM tests validate streaming, cancellation, pitch frequency and duration. Complex Office layouts retain a readable fallback and Word page layout remains approximate. Neural RSVP, mixed-engine cast export, sentence-note/quiz media portability and larger dedicated-notes features remain open. Saved malformed PDF/PPTX extraction is not silently replaced, so extraction corrections require fresh import when relevant.

### Final follow-up validation and Preview update — 1 October 2026

Final checks passed **350 JVM tests and all 38 Android tests**, with zero failures, errors or skips. The added phone regression verifies normal Play restoring a book's remembered rate/pitch, pending gestures overriding memory before persistence, and explicit controls taking precedence. PPTX palette tests cover theme-dependent colors, uncertain dark-slide text, image backgrounds and unreadable contrast falling back to readable text. The full phone report is preserved at `tmp/verified-phone-full-suite-38.xml`.

An initial final-suite attempt was interrupted by Google's dynamic language-identification module update: phone logs show `DynamiteLoaderV2Impl` requesting a process restart for `mlkit.langid`, followed by SIGKILL. The unchanged rerun passed. In that completed run, the first reader opened after 610 ms on the warmed 50-page Sherlock fixture while extraction continued. This is a fixture measurement, not a guaranteed full-book/OCR timing. The concluding voice-settings source adjustment was indentation only; executable behavior was unchanged.

The default `:app:assembleDebug --offline --max-workers=2` succeeded. APK metadata was verified as `com.veritas.reader.preview`, then the ARM64 APK was installed on `RZGL403LCNW` with `adb install --user 0 -r` (Success). MainActivity launched with Status: ok; its process remained alive. All **13 stored reading text files and two note-media files** present immediately before installation remained afterward. Connected tests used the separate `.checks` application. `git diff --check` passed. No release publication or commit was made.
