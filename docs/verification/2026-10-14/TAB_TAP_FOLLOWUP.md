# Coordinated tab swipes and extracted-reader taps — 14 October 2026

Current debug preview: **2.6.2 / 42**, package **com.veritas.reader.iconcheck**, label **Vern Preview**, installed on Samsung SM-A175F (Android 16). Release packaging/publication remains on hold.

Artifact: [Vern-v2.6.2-debug-arm64.apk](../../../output_apks/Vern-v2.6.2-debug-arm64.apk). SHA-256: `31FB91FA34ED023816CAEFC2AB72BCA85E852AD2E04BB398CF396D5BB2A935B6`.

## Tab correction

The previous revision delayed structural chrome until the pager settled, then animated header/player size changes. The user reported separate updates, delay and snapping. That approach was removed: headers, floating controls, player visibility and navigation again use the same current destination during a drag, and the previous swipe/snap timing is restored.

Performance changes are limited to pausing offscreen Insights decoration, giving prefetched pages no Funky entrance trigger, remembering navigation callbacks, and deferring Home data refresh until scrolling finishes. Visible chrome does not wait for that refresh.

The replacement regression holds the finger down after crossing to each destination and checks the selected navigation item, destination title, Notes action pill and player visibility **before release**. It covers 16 forward/backward drags across Home, Added, Classics, Study and Notes with 60 books, 40 notes and an active player, followed by a cancelled drag and rapid Notes-to-Library retargeting. The earlier fixed-viewport test did not establish the desired interaction and was replaced.

## Tap correction

Ordinary native prose taps, taps immediately after scrolling and both page margins worked in baseline checks. A further case reproduced a conflict: controls revealed by a tap were hidden again when the page automatically scrolled to follow a new sentence. The old auto-hide observer treated every scroll, including programmatic following, as user activity. See [the failing baseline](single-tap-follow-baseline.txt) and [its screenshot](single-tap-sentence-follow-failure.png).

Auto-hide now observes a vertical drag interaction on the visible page beyond the existing scroll threshold. Automatic sentence following cannot undo the reveal tap. The regression checks repeated hide/show taps after native double-tap confirmation, both margins, a manual drag hiding both bars, a subsequent tap revealing both bars, and a programmatic sentence-follow scroll leaving them visible. It then checks native selection, selection dismissal and a page turn updating the saved position.

## Results and limits

Debug application and instrumentation package compiled successfully. [The final Samsung run](tab-tap-reader-phone-final.txt) passed **all 9 tests in 119.721 seconds**:

- Four extracted-reader tests: curl, Slide/Light flip/None, reduced-motion curl, and the tap/follow regression.
- One coordinated main-tab regression, including floating player visibility during a held drag.
- Four curl component checks: forward/backward rendering, mesh endpoints/reverse, pinch ownership/cancelled turns, and live-page taps, double taps, vertical scrolling and native selection.

The existing 474-test JVM result belongs to the earlier source snapshot described in the earlier report; this follow-up changed UI behavior and reran the relevant physical-device checks. Whitespace checks passed. These checks establish covered behavior on this Samsung; they do not guarantee subjective smoothness or a particular frame rate for every document/device. Tablet/foldable, release/R8 and real-model listening/export acceptance remain as previously recorded.
