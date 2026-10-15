# Vern 2.6.2 — build 42

All interface, icon, animation and reliability refinements from the 13–15 October work are included in **2.6.2**. This is the current release draft. Local APK/AAB release builds were authorized on 15 October and are being verified. Publication remains pending; existing published 2.6.1 release files are historical snapshots.

## Interface and appearance

- Redesigned About with a branded hero, version badge, update card and grouped support/community links. Reorganized Accessibility into consistent reading, motion, appearance and daily-rhythm controls.
- Fixed dark text on dark backgrounds throughout the new screens and settings. Custom Notes paper chooses contrasting text, and high-contrast modes retain readable controls.
- Added 20 icon colour palettes and **Follow my colour theme**, which resolves the selected theme family and its light/dark or system mode. Changing icon colours keeps Settings open; the launcher update applies after leaving Vern.
- Matched startup colours to the active icon palette. Android 12+ uses one system splash with distinct **Bloom, Fade, Rise and None** choices and a visible, replayable settings preview. Returning from Recents does not replay it. Android 9–11 retains a matching static starting window.
- Kept the launcher mark centred at **52 dp** with more padding, inside the 66 dp safe circle. Adaptive layers, Android themed-icon monochrome and the white notification silhouette remain included.
- Renamed **Scattered cards** to **Funky cards**, retaining saved choices. Stronger, stable tilts and offsets with a settling arrival apply across applicable Library, Notes, Home, Classics, Study, Insights and settings cards. Reduced motion suppresses entrances; high contrast keeps cards straight.
- Reduced the Home wordmark to **26 sp** with a 28 dp mark—4 sp above other tab titles—and its header minimum to 48 dp. Reduced floating player and navigation bar heights by **4 dp** across theme packs.
- Added **App settings** to the Notes overflow alongside Notes settings.

## Listening, reading and navigation

- Rounded Listen covers and fallback artwork. The surrounding cover sheet draws colours from the book cover for a more cohesive appearance.
- Added collapsible playback controls and a full-screen reading option to **Listen > Read**, keeping playback available while reading.
- Refined physical-style page curl with a denser curved mesh, shaded paper reverse and moving shadow. Automatic turns use a gentler 1,050 ms motion; swipe settling uses 900 ms. Slide, Light flip and None remain available, and Reduce Motion disables decorative turns.
- Fixed invisible neighbouring pages intercepting touches when curl is enabled. Stationary pages keep live text for scrolling, taps, double taps and native selection. Pinch handling survives font updates, retains ownership until both fingers lift, and does not become an accidental page swipe. Text selection prevents pager swipes; Back clears native selection. Native text releases touch interception at gesture end and selection dismissal so later swipes can resume.
- Fixed extracted-reader bars hiding again after a reveal tap when the page automatically follows a sentence. Auto-hide now responds to a vertical user drag on the visible page; automatic scrolling leaves the revealed controls alone.
- Fixed reading-page synchronization and previous/next controls using another book's audio position. The displayed book now follows its own reading position while a separate audio session retains its identity.
- Kept the saved sentence position and page label synchronized after a manual page turn, including when the pager publishes its destination before scrolling fully stops.
- Restored coordinated swiping between Home, Added, Classics, Study and Notes: headers, floating controls and navigation follow the current destination together during the drag, with the previous swipe/snap timing. Offscreen Insights animations pause, prefetched pages do not start Funky entrances together, and Home data refresh waits until scrolling finishes.
- Restored all five Classics archive links to the in-app browser with the selected site and search query. Archive navigation now requires an explicit callback, preventing silently inactive buttons.

## Reliability and verification

- Corrected a startup I/O regression: the reading-session tracker initializes its repository on its I/O worker rather than during main-thread ViewModel construction.
- Updated the technical-debt recheck to distinguish repaired issues, subsequently implemented features and remaining acceptance limits. Approximate Word pagination, broader Office fidelity, tablet/foldable coverage and real-model listening/export remain limitations.
- Validation results and device checks are recorded in [the earlier debug report](docs/verification/2026-10-14/DEBUG_262_RECHECK.md) and [the tab/tap follow-up with the current preview](docs/verification/2026-10-14/TAB_TAP_FOLLOWUP.md). Debug success does not establish release/R8 or all-device behaviour.

## Security and distribution

- Protected private playback commands with a persistent app-only capability while retaining trusted media controls.
- Added HTTPS redirect validation, origin-specific download cookies, download confirmation and byte limits for browser books, voice models and APKs. Archive extraction now enforces entry counts and expansion budgets, including skipped entries.
- Disabled third-party browser cookies, explicitly destroy dismissed WebViews and restrict FileProvider paths to managed media, originals, captures and staged exports.
- Check downloaded APK package, newer version code and compatible verified signing identity before opening the Android installer. Updated the vulnerable transitive Commons Lang dependency to 3.18.0.
- Removed embedded Telegram credentials and workflow fallbacks, added authenticated bounded webhooks, sanitized API errors and fixed manual spotlight input handling. Bot/site guidance distinguishes the upcoming milestone from published downloads.
- Verified the APK/Play conflict: local release APKs use the Play upload certificate, while distributed Play apps use a different signing certificate. Future Play-compatible APKs must come from the Play-signed distribution route; existing installations need a separately verified migration. See [update compatibility](docs/UPDATE_COMPATIBILITY.md).
- Consolidated remaining acceptance into [the backlog](docs/ACCEPTANCE_BACKLOG.md), removed superseded root plans and one-off mutation scripts, and retained recovery copies outside the project.

The 15 October security build passed all 480 JVM tests, four focused Samsung security checks and one actual speech/selection regression. Webhook checks use mocked network calls. These results do not establish all-device, full penetration-test or release/R8 acceptance. Deployment status is recorded separately in the milestone verification report.
