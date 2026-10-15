# Customization and UI feedback — 13–14 October 2026

The final focused Samsung SM-A175F run passes all 13 tests in `customization-feedback-phone-final.txt`. A separate `com.veritas.reader.iconcheck` preview package is installed; the production app was not replaced.

Release packaging is on hold at the user's request. A prematurely started release assembly was cancelled before producing new APKs; no AAB build was started. Existing release files are from earlier revisions. Finish the remaining work and debug review before starting release builds again.

About now uses a branded gradient hero with a quiet book-spine motif, version capsule, separate update card, grouped support rows and labelled community links. Accessibility uses compact, consistent toggle groups and wrapping contrast presets. Default text inherits `onBackground` at the app theme and full-screen settings levels; custom Notes paper chooses ink from its actual luminance.

Launcher aliases update only after all activities leave the foreground. The regression launches the actual launcher alias, changes to Ocean, confirms Settings remains resumed and the existing launcher stays enabled, then backgrounds the app and checks that only Ocean is launchable. The test restores preview preferences. The first lifecycle fixture failed because MainActivity consumes its incoming action before ActivityScenario compares the intents; the corrected explicit-component fixture passes.

The second Compose splash overlay is removed. Android 12+ shows a single system splash using the active palette, with static icons for Fade/Rise/None and reduced motion. Fade/Rise animate that existing splash out without hiding and replaying the logo. Android 9–11 uses a matching static starting window.

Scattered cards have stable mixed-key poses up to 5.4° rotation, 7.2 dp horizontal offset and 12 dp vertical offset, with more restrained poses for wide panels. Cards drop from 24 dp above their position into a spring-settled pose, with varied arrival delays. Home, Library, Notes, Classics, Study, Insights and settings groups use the effect. Reading text and navigation stay aligned; high contrast keeps cards straight, and reduced motion lands immediately. The Study fixture verifies taps after settling.

Page curl now has a more asymmetric fold and a broader bend. Programmatic turns use a 950 ms easing curve, with 780 ms swipe settling; dragging still follows the finger. Forward/backward phone fixtures capture selectable Android TextViews on the curved surface and verify the return to live text at rest. Native PDF/original viewers keep their own navigation.

Notes overflow exposes separate Notes settings and App settings actions. Listen keeps rounded covers/fallback marks, cover-derived panel colours, collapsed controls and full-screen reading without seeking.

Screenshots under `ui-preview/VernUiChecks` include About at large text and in dark mode; Accessibility in light, dark and high contrast; dark Customization; scattered Study arriving/settled; Listen placeholder/collapsed/full-screen; and forward/backward page curls. Fixtures are verification captures, not Play Store screenshots. Tablet/foldable checks remain deferred as requested.
