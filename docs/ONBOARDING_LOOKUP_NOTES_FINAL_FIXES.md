# Onboarding, Web lookup and Notes sharing — 10 October 2026

## Implemented behavior

Onboarding page 3 uses the same `VeritasRoundSlider`/`VeritasSleekSlider` as the app, with accessible speed adjustment. All five illustrations have gentle scene motion; page changes, progress and Continue feedback use the shared motion settings. Reduced motion disables scene drift and uses the existing reduced-motion transitions. Speed and pitch survive Back/Continue navigation. The four onboarding quests remain enabled; completion and dismissal still control visibility.

Web lookup saves the selected word, original context and source before opening the browser. WorkManager resolves the meaning independently and updates only the matching saved entry. Pending work can survive leaving the app and waits for connectivity. Dictionary lookup does not require an AI key. The primary dictionary endpoint timed out during verification; Wiktionary now provides a second source, with a visible source link. Unavailable meanings show an explicit status and Find meaning; existing definitions can be refreshed without losing them on a failed attempt. The regular reader and native PDF share the durable save helper.

Notes with attachments offer note text, a PDF of the text, individual recordings/files, or a complete-note ZIP. Text sharing has no attachment MIME or stream. New recordings use AAC in M4A. Existing AMR recordings in 3GP are extracted into a real AMR file with the appropriate header, preserving encoded audio and the original recording. Complete-note ZIP includes the note metadata and owned attachments; restore it through the existing Backup & restore import. Missing media fails visibly rather than producing a silently incomplete archive.

## Verification

- Final app and Android-test assembly succeeded: `.checks/onboarding-final-fixes/dictionary-fallback-build.txt`.
- 14 JVM checks passed: 7 vocabulary/source/persistence transformations, 4 Notes sharing logic, 3 onboarding tour sequencing.
- All 9 Android checks passed on the connected ARM64 phone: 2 onboarding controls/navigation/tuning checks covering normal and reduced motion, 6 Notes tests covering incoming ownership, sharing grants, complete-note restoration and actual encoded AMR extraction, and 1 live background vocabulary lookup/persistence check.
- The live lookup saved a real meaning for “serendipity” with the original sentence and sentence index, then reloaded it through a new repository instance.
- The AMR test encoded synthetic silence, verified exported frame bytes and duration, and verified the original file was unchanged. It did not record microphone audio.
- Screen 3's phone screenshot was inspected for slider style, text/control clearance and layout. Evidence is under `.checks/onboarding-final-fixes/`.

Tests installed only `com.veritas.reader.checks` and its test package. The normal/preview app data and prior release were not replaced; no new release was published.

## Remaining acceptance

Exercise browser handoff from actual regular-reader and PDF selections, offline/reconnect, process restart and older entries' Find meaning action. Inspect long PDF exports and actual WhatsApp recipient playback. No message was sent to a recipient during verification. Phone tests confirm the sharing payload, content access and media validity, not every receiving app's behavior.

The wider remaining work, including the Reading Insights implementation, document comparisons, advanced playback/study features, media maintenance/sync, app icon and measured release shrinking, remains tracked in `FUTURE_UI_PLAN.md`.
