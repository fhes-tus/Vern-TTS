# Vern 2.6.2 debug verification — 14 October 2026

This records the earlier debug snapshot. The user rejected its delayed tab chrome; that approach has been replaced with coordinated drag updates. See [the tab and tap follow-up](TAB_TAP_FOLLOWUP.md) for the later preview and checks.

Version **2.6.2 / 42**, package **com.veritas.reader.iconcheck**, label **Vern Preview**. The ARM64 preview is installed on the Samsung SM-A175F. Production installation is separate. Release APK/AAB packaging and publication remain on hold.

Earlier preview SHA-256: `4852ACECFE8F49294E2A5FB6E2E7A97142E766BCB295AD11E5D2F41F9A86BC7C`. That preview file has been replaced; use [the follow-up report](TAB_TAP_FOLLOWUP.md) for the current artifact and hash.

## Changes verified

- The Classics route is explicitly wired from Library to the ViewModel/browser state. All five archive buttons retain the chosen destination and search query.
- Curl neighbours stay physically offscreen at rest. The mesh uses 48 columns / 16 rows and reuses its point buffer. Automatic turns use 1,050 ms and swipe settling 900 ms with a gentler initial acceleration.
- Two-finger font changes retain gesture ownership through recomposition and staggered finger release. Native text resets interception at gesture end and selection dismissal. Selection prevents accidental pager turns, and Back clears native selection.
- Reading controls use the displayed book's own position when audio belongs elsewhere. Manual page synchronization observes both the settled page and scrolling state, avoiding a dropped completion update.
- Main-tab structural chrome follows the settled page. Header/player size changes ease after arrival; prefetched cards receive no entrance trigger; each landed page receives its own Funky entrance. Home refresh waits for arrival and pager snapping uses a critically damped spring.
- The wordmark is 26 sp with a 28 dp mark; Home minimum row height is 48 dp. Player heights are 76/72/70/66 dp for One UI/Material You/Liquid Glass/other packs. Navigation is 66 dp with labels and 62 dp without—each 4 dp shorter than previously.
- ReadingSessionTracker now initializes its lazy repository from its I/O worker, correcting the startup regression found during the debt audit.

## Results

Debug application, Android-test package and JVM build succeeded. **474 JVM tests passed**, zero failures/errors/skips. XML results are in [jvm-262](jvm-262).

**15 focused phone checks are verified across the component run and final reader rerun:**

| Coverage | Passing checks |
| --- | ---: |
| Native extracted reader: curl, Slide/Light flip/None, reduced-motion curl | 3 tests / 5 configurations |
| Repeated main-tab swipes, fixed drag viewport, cancellation and rapid retargeting | 1 |
| Curl forward/backward render, mesh endpoints, taps/double taps/vertical scroll/native selection, pinch and cancelled turns | 4 |
| Theme primary/onPrimary matching, all 20 palette startup resources, visible Bloom/Fade/Rise previews and reduced motion | 2 |
| All five Classics archive routes | 1 |
| Compact wordmark at 1.5× font size in light/dark themes, sidebar dismissal | 2 |
| Navigation destinations and callbacks | 2 |

The [combined component run](debug-262-phone-final.txt) passed 12 of its 13 cases; its original actual-reader case exposed the final dropped-position update. The corrected case plus the two added configuration regressions then **all passed** in [the final reader run](all-reader-styles-phone-final.txt), including native scrolling, long-press selection, no unintended page advance during a selection gesture, Back dismissal, and an interior horizontal turn updating both the visible page and reading position while another audio identity remains unchanged. Counts are combined coverage, not a claim of one clean 15-test invocation.

Earlier attempts also required test corrections: real uptime for native long-press, actual Compose scroll/word coordinates, advancing the test frame clock, reselecting before testing Back when a drag already dismissed selection, and keeping swipes outside Android's edge-Back region. One touch injection was interrupted when another app took focus. Those runs are not counted as passes. The user made the phone available again for the completed checks.

## Remaining limits

These are focused Samsung checks, not all-device acceptance or a measured 60 fps guarantee on every book. Large illustrated documents, other launchers/API levels, tablet/foldable/resizing behaviour and subjective motion remain broader acceptance. Real-model export and human listening remain paused. Native PDF/original-document viewers retain their own navigation. Release/R8 behaviour requires a later authorized release build.

All today's changes are assigned to [the 2.6.2 notes](../../../RELEASE_NOTES_v2.6.2.md); historical 2.6.1 distribution files have not been relabelled or replaced.
