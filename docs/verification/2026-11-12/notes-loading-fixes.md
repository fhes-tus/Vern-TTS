# Notes loading and audio-duration fixes

12 November 2026. Branch: `test/reader-ui-update`. Existing local Android work is preserved; pre-edit copies of the six edited application files are under ignored `.artifacts/notes-loading-20261112/before/`.

## Implementation

- `loadGeneralNotesSnapshot()` reads and normalizes stored notes once while holding the existing library write lock, then produces active notes sorted by update time and Trash sorted by deletion time. Each call reads current storage. There is no process-wide notes cache or new storage format. Existing resilient primary/backup parsing and managed-attachment handling remain the source of both lists.
- Startup uses the combined snapshot. Save/no-op-save validation, deletion, restoration and notebook deletion use combined active/Trash results where both lists are needed. Existing save revision/target checks, editor initialization guard, reminders, revision archiving and persistence operations remain in place. A saved/changed collection is read again as a new snapshot; this is not a promise that every unrelated notes load or every mutation serialization is eliminated.
- `NoteAudioDurationReader` moves local file inspection and metadata extraction onto `Dispatchers.IO`. A coroutine mutex permits one native read at a time and lets repeated requests use the first result through a cache. The cache retains at most 64 duration values and is keyed by canonical path, file modification time, byte length and the caller's note revision.
- Results are checked after native extraction: cancelled requests, deleted/replaced files and invalid durations are not published or cached. Metadata failures are not permanently cached. Native retrievers are released in `finally`, including failed reads. Cancellation discards results; it does not claim to interrupt an Android native metadata call already in progress.
- `rememberNoteAudioDurationMillis` owns state and work for the current path/known duration/revision. A changed key gets a new state cell immediately, preventing the preceding card's label from flashing. Recorded annotation durations display immediately without metadata extraction. The same helper replaces blocking duration calculations in general Notes cards, annotation cards and the note editor's audio block.
- Existing duration formatting/unavailable labels and playback, seeking, recording and attachment actions are retained. A duration requiring extraction can appear after the card. No application data is deleted or migrated. No Gradle configuration, dependency, speech model, UI layout or language pack was changed for this task.

The lifecycle/cancellation implementation follows the official [Compose effects guidance](https://developer.android.com/develop/ui/compose/side-effects), [coroutine context contract](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html), and [MediaMetadataRetriever API](https://developer.android.com/reference/android/media/MediaMetadataRetriever).

## Verification

- Focused JVM results: **21 tests passed**, zero failures/errors/skips: seven new metadata-reader tests, five existing notes-continuity tests and nine reader-text-model tests. Tests cover caller-thread isolation, concurrent duplicate requests, changed/deleted files, explicit revisions, bounded eviction, transient errors, cancellation and replacement during extraction.
- A comparison with the saved pre-edit files confirms playback/seek/attachment action code following the replaced annotation/editor duration blocks is byte-equivalent after line-ending normalization. Scoped `git diff --check` passed for the tracked application changes.
- The combined offline build passed in **9m40s**, producing the isolated app and test APKs. App SHA-256: `f841e6740d509b625e40d5c66da041cd1ccd8dee16be78bc8f7992ae38cde3a8`; first test APK: `d70a7d3e9dd6a74f47e237684a1fe6a972d05f00febb022586bc10e0e4baf986`.
- On Samsung SM-A175F, installed only new `com.veritas.reader.notesload.checks` and `.test` packages, after checking both metadata and embedded identities. The first run attempted nine cases: the two existing save/no-op/rapid-save tests passed; seven Compose-rule cases failed during Espresso setup with `NoSuchMethodException: android.hardware.input.InputManager.getInstance`. Those seven failures are not application acceptance. The full receipt is `.artifacts/notes-loading-20261112/native-tests.txt`.
- Reworked the five new device tests to use framework ActivityScenario/Compose state observation instead of the incompatible Espresso rule. **At the user's request, stopped the rebuild and further testing before this harness ran.** The corrected harness therefore has no passing native result. No speed percentage or APK-size improvement is established. The installed production and preview packages were not updated.

## Commands and observed failures

On Windows with Android Studio JBR and cached Gradle 9.4.1, ran:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
& 'C:/Users/wwwfe/.gradle/wrapper/dists/gradle-9.4.1-bin/arn2x92ynaizyzdaamcbpbhtj/gradle-9.4.1/bin/gradle.bat' :app:testDebugUnitTest --tests com.veritas.reader.NoteAudioDurationReaderTest --tests com.veritas.reader.NotesContinuityTest --tests com.veritas.reader.ReaderTextModelTest :app:assembleDebug :app:assembleDebugAndroidTest '-PdebugApplicationIdSuffix=.notesload.checks' --offline --max-workers=2 --no-parallel --console=plain
```

The first invocation left the `-P` argument unquoted; PowerShell split its suffix into a task name and Gradle failed before compilation with `Task '.notesload.checks' not found`. The quoted retry used the existing external cache. Logs are `.artifacts/notes-loading-20261112/build-checks.log` and `build-checks-retry.log`. `adb devices -l` identified Samsung SM-A175F, and package-path checks found neither of the intended temporary test packages installed. An initial optional read of a nonexistent test filename returned a missing-file error; the actual test files were then inspected. Read-only source searches and official documentation checks also informed this change.
