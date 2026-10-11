# Reader glass and Back hierarchy — 9 October 2026

## User-requested corrections

- Extracted-text `PlayerPanel` and actual-document `DocPlayerPanel` now use the same Kyant backdrop material as the library player/file-browser pill: 10 dp blur and 36% surface tint. Their document content is recorded separately from chrome. Active glass removes the old gradient, border and extra Material elevation shadow. Solid/accessibility fallbacks remain available.
- Native PDF playback controls retain their existing Android views, expansion, sliders and fragment routing. A Compose background records only the PDF fragment beneath the controls and applies the same glass material. It tracks the fragment's paper-tone paint and on-resume settings. Rendering refreshes while the bar is visible and the activity is started, without screenshot/bitmap allocation or capturing the controls themselves.
- Notes navigation uses two physically separate rounded material layers, retaining the gap and Kyant lens/highlight on each. Switching away rejoins the navigation. This replaces the generic combined shape that lost the rounded glass treatment. Both Liquid Glass and Glass in other themes use the same path.
- Back from a Study subsection returns to Overview first. Back from Notes Trash, a notebook, a note category/tag or a retained search returns to All Notes first. Selection, search controls and modal dismissal retain their nearer-level handling. Back from each root then returns Home.
- Show trash/Show notes and Revision history receive Material outlined icons. Restore and Notebooks retain their outlined icons throughout the affected menus.
- Removed the Study Overview introduction and subtitle requested in the final screenshot. The queue appears directly below section navigation.

## Verification

Final app and instrumentation APK assembly passed. All 12 checks passed on the connected ARM64 phone: 2 glass chrome cases, native PDF in both modes, the full Study/Notes Back hierarchy, 3 Home/Added cases, 3 study persistence cases and 2 Overview cases. Earlier focused JVM verification passed all 35 cases.

The first Back test exposed a scroll-to-top effect triggering synchronous remeasurement while Pager was laying out. Study/Added section changes and Back resets now use layout-safe `requestScrollToItem(0)`; the complete hierarchy test passes. The Notes gap assertion permits the subtle backdrop edge shadow while still rejecting an opaque connecting bar.

Visually inspected screenshots of extracted-text, actual-document and native-PDF expanded controls, plus both separated Notes glass modes. Notes has a real background-visible gap and two rounded material layers; all four navigation actions and reader playback/expansion pass. These checks cover deterministic fixture backgrounds, not every possible document/theme combination.

Fixtures/installations use `com.veritas.reader.checks`; the normal and preview apps/data are preserved. The native PDF fixture and its temporary settings are cleaned up afterward. Final log: `.checks/study-hub-review/glass-back-final-device-tests.txt`; screenshots are in the same ignored directory. No public release was created.


## 10 October 2026 — glossy Listen and shared pills

The later screenshot request refines the shared material to restrained tint/blur/vibrancy with a subtle sheen and thin reflected rim. Add/Note floating pills remove duplicate glass borders and all Material elevation states. Notes keeps its two separate glass layers and real gap. Listen now uses a rounded-square cover, circular skip/bookmark controls, a wide Play/Pause capsule and speed/voice/timer pills; Read has smooth sentence emphasis and edge fading. Both glass modes, compact/reduced settings, navigation and native PDF pass phone checks. Details and current evidence: [Listen redesign](LISTEN_REDESIGN_2026-10-10.md).


### 2026-10-10 — Restore LastWave-style refractive glass
- User clarified that removing gloss must preserve real liquid glass. Read the authorized LastWave `ui/theme/LiquidGlass.kt`: its material uses `drawBackdrop`, vibrancy, colour controls, an edge lens, a narrow highlight and a translucent scrim. Vern's shared helper now uses that recipe rather than `drawPlainBackdrop` plus painted reflection gradients.
- Default blur is 8dp, brightness .05, saturation 1.5; theme tint is lighter (roughly 22–28% for current controls). A 1dp library highlight defines the real edge. Capsule lens dimensions match LastWave; expanded rectangular panels bound the lens by their actual rounded corners so their centre stays undistorted. No opaque top gloss overlay or duplicate glass border is added.
- The single helper covers library navigation and separated Notes capsule, Add/Note/import floating controls, library/text/original/native-PDF players and Listen controls. Reduced transparency, high contrast and unsupported-device fallbacks remain intact. The icon palette and legacy streak fix remain unchanged.
- Final app/test assembly passed in 1m20s (`.checks/refractive-glass-bounded-final-build.txt`). Playback/expansion, separate navigation gap and native PDF checks passed on this final source. Listen's two safe-area/interaction cases passed on the capsule-equivalent source. The new refraction probe initially sampled a flat centre and then a one-direction stripe pattern; it now samples inside the curved band with a two-dimensional pattern and passes (`.checks/refractive-glass-two-axis-probe-phone.txt`). Those test-fixture corrections did not weaken the required visual difference.
- Final expanded player, navigation, Listen and native-PDF screenshots reviewed; the bounded lens removes the earlier large corner folds. All tested controls remain sharp/clickable. Only isolated `.checks` installations; no release build or main/Preview data changes.
