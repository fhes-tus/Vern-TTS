# Playback startup feedback and final release — verified 10 October 2026

The user authorized the two animation opportunities, requested startup feedback on playback controls, and requested a local release build. This supersedes the earlier search-only scope for those two opportunities and the earlier restriction to release size investigation. It does not authorize public release publication.

## Changes

- Playback now has a transient preparation state distinct from its accepted Play intent. It begins immediately when Play is requested. The playback controls show a stationary play glyph inside a subtle loading ring; tapping again cancels startup. The preparation state is scoped to the requested book, cleared by pause/stop/error/reset/service teardown, and never persisted across process restarts.
- System TTS clears preparation on its valid utterance-start callback. Neural playback observes the AudioTrack head moving, independent of the streaming write: a blocking write can last most of a sentence. Generation and utterance checks reject cancelled/stale callbacks. Failed synthesis never claims to have started output.
- Neural model construction is deferred to the background synthesis path, allowing the main thread and loading feedback to continue responding during initialization. The existing synthesis/shutdown lifetime guard still owns the engine.
- Shared feedback covers Home's continue-reading button, the floating library player, extracted-text and actual-document panels, the listening screen, native PDF controls, and the home-screen playback widget. Native PDF observes playback state while resumed and stops ring animation when paused/hidden. The widget uses Android's indeterminate progress control without per-frame widget refreshes.
- Reduced motion uses a stationary loading arc/glyph with an accessible preparation description instead of rotating feedback. Media session preparation is reported as buffering.
- The voice installation status fades in using shared `VeritasMotion.effectsFast()`, with a reserved action slot and immediately usable controls. Existing installed voices do not replay a success entrance on every visit. The daily-goal ring center crossfades from percentage to checkmark using the same token; its ring, reading time and surrounding layout stay fixed. Reduced motion retains these opacity changes.

## Validation

Production debug/release compilation and signed release assembly passed. All 30 focused JVM cases passed: 6 playback-state, 10 playback-pipeline, 6 neural-output and 8 product-refinement cases.

All 17 phone cases are verified across the initial suite and the corrected selector retest. The initial run passed 16 cases, including actual system speech startup, neural output-start acknowledgement, cancellation, failed synthesis and the existing glass/navigation/Study/Home checks. The remaining UI test selected an internal tag hidden by accessibility semantics; it now selects the accessible Cancel/Play labels and passes in both normal and reduced-motion modes. Its retest is in `playback-feedback-selector-retest.txt`.

The isolated checks app had been removed before the final retest. Recreated its missing APK from cached outputs and restored only `com.veritas.reader.checks`; the normal/preview apps and their data remain preserved. No production source changes were needed for the retest.

The release was built on 9 October and its signature/alignment checks completed on 10 October. Package `com.veritas.reader`, version 2.6.0 (40): ARM64 51.32 MiB, ARMv7 43.46 MiB, universal 69.27 MiB. All three APKs pass signature verification and 16 KiB ZIP alignment. Files are in `app/build/outputs/apk/release/`. No public publication or production-app installation was performed.

Evidence is kept in `.checks/study-hub-review/playback-feedback-compile-tests.txt`, `playback-feedback-final-build.txt` and the final device/signature logs in that directory. The first combined build caught two test-only imports for methods that are members in the current Compose test API; those imports were removed. No production compilation error was involved.

The broader voice quality, real concurrent network downloads, sharing destinations and external book-copy acceptance checks remain in `NEXT_UPDATE_DEVICE_CHECKS.md`. The startup output test uses synthetic PCM and system TTS; it does not claim every downloaded voice was auditioned again.
