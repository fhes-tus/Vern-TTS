# Known limitations and verification status

Updated 14 October 2026 against the current **Vern / Veritas Reader Android 2.6.2 (build 42)** source. This replaces the v2.4.0 snapshot. Source changes do not automatically update previously built or installed APKs. Earlier 2.6.1 test/build results apply to their recorded snapshots; release packaging remains on hold.

## Android versions, screens and distribution

- **Android versions:** Minimum SDK 28 (Android 9), target SDK 36, compile SDK 37. Older Android versions are unsupported by this build.
- **Responsive screens:** Compose layouts use available-space constraints and density-independent dimensions. Library grids use two columns below 600 dp, three from 600 dp, and four from 840 dp. Several controls and artwork adapt to available width/height. This does not guarantee that every screen fits every window size.
- **Screen validation:** Selected 320 dp layouts, 1.5x font scaling, reduced motion and light/dark themes have automated checks. Recorded physical-device testing mainly uses the Samsung SM-A175F. All phone brands, aspect ratios, font sizes and Android versions have not been validated.
- **Large screens and resizing:** The manifest does not lock the app to portrait or declare Samsung-only support. Reader views offer orientation controls. Dedicated tablet navigation, two-pane layouts and fold/hinge handling are not implemented. Tablet, foldable, landscape, split-screen and live-resizing acceptance remains incomplete; wider windows can receive an expanded phone layout.
- **Architecture splits:** Release packaging produces arm64-v8a, armeabi-v7a and universal APKs. Low-memory and 32-bit behavior still needs broader validation.
- **Signing/build setup:** Releases use a local keystore and local-property credentials. Gradle pins Android Studio's embedded JDK to a Windows path; other machines must override or adjust it. Generated caches are excluded from version control and recreated as needed.

## Document import and original formatting

- **PDF extraction — improved, still bounded:** Column detection, reading-order handling and positioned illustrations are implemented. Complex magazines, tables, formulas, unusual gutters and rotated/cropped pages can still cause imperfect ordering, spacing or image placement. Illustration extraction limits image count and size; it is not a complete PDF renderer.
- **OCR — continuing limitation:** ML Kit recognition depends on scan quality, resolution, orientation, language and runtime availability. Errors can affect speech and selection matching.
- **Covers — corrected description:** PDF cover extraction examines up to the first five pages. EPUB, DOCX and PPTX use format-specific paths. Unusual title/artwork layouts can still need manual selection or a placeholder.
- **DOCX/EPUB — improved preservation:** Original views support more source structure, selectable text, image placement and Word tables. Reflowed reading is not an exact rendering. Word pagination remains approximate; complex styling, sidebars, footnotes and charts can differ.
- **Presentations — partial layout fidelity:** Supported PPTX objects preserve positions and basic formatting. Groups, charts, SmartArt, tables and unsupported structures can use readable fallbacks. Legacy PPT remains a text preview. Exact Office fidelity is not established.
- **Web imports — continuing limitation:** Public pages are fetched directly. Logins, paywalls, CAPTCHAs, JavaScript-only content and anti-bot protections can prevent clean imports.

## Native PDF selection and speech synchronization

- **Selection-based playback:** Native selection depends on Android's PdfViewerFragment and the actions it exposes. Availability and behavior can differ with the OS/framework and document.
- **Speech highlighting — approximate:** Sentence/page metadata and document search improve synchronization, but search and estimated-page fallbacks remain. Repeated text, OCR errors and unusual layouts can cause missing highlights or drift.
- **Image fallback:** Rasterized PDF pages preserve appearance without native selectable text. This does not describe the selectable Word/EPUB original views.

## Playback and audio export

- **Neural engines:** Kokoro/Piper through Sherpa-ONNX need model storage, RAM and CPU time. Real-time performance on constrained devices is not guaranteed. Native crashes can terminate the process below the JVM and bypass the in-app crash reporter.
- **System TTS:** Voice quality, installed languages and synthesis behavior depend on the device's selected TTS engine and voice data.
- **Background playback:** Playback uses a foreground media service and media session. Foreground-service restrictions, battery policies and device-specific process management remain acceptance checks. The old assertion that notification denial always stops background playback was unsupported: current code does not explicitly stop on denial. Denied-permission/background behavior needs device validation.
- **WAV export — improved:** Android synthesis is segmented; neural export processes bounded text and streams PCM to WAV. This addresses oversized synthesis jobs and memory pressure. Long exports still take time and can fail because of engine errors, storage limits or interruption.
- **Real-model acceptance — pending:** Automated/substitute-engine checks do not establish actual voice quality, absence of Lite hiss, long-book export reliability or human pitch/cadence quality.

## Storage, backup and synchronization

- **Local storage and portable exports:** Library data, reading sessions and notes are stored on the device. Manual backup/restore and portable Notes archives are implemented. Save to phone places book copies in user-selected external storage.
- **No account-based cloud sync:** There is no central account service continuously synchronizing the library or Notes. Portable backup/restore is the supported cross-device path.
- **Android backup — enabled with exclusions:** The manifest sets allowBackup=true. Both backup formats include preferences, databases and app files while excluding the encrypted secrets preference file, original documents and restore transaction folders. Cloud backup also excludes covers; device transfer permits covers. Restoration depends on device/platform behavior; these rules do not guarantee a complete library restore.
- **Credentials:** API credentials use device-bound encrypted storage excluded from backup. Restored devices may need credentials entered again.
- **Uninstall/data clearing:** Local app data is removed. Keep important manual backups and exported books/notes outside app-owned storage; automatic backup does not preserve excluded originals. Successful user-selected external exports survive removal of app data.

## Testing and release acceptance

- **Customization checks:** Thirteen focused phone tests passed on the SM-A175F on 14 October: changing the launcher palette keeps Settings open and applies after backgrounding; one launcher remains available; static splash choices use a matching colour; Notes can open App settings; scattered Study cards remain clickable; About works at 1.5x text; light/dark/high-contrast settings text is readable; Listen collapse/full-screen and forward/backward curls retain their controls/live text. These are focused checks, not all-device acceptance. Evidence: docs/verification/2026-10-13/customization-feedback-phone-final.txt and ui-preview/VernUiChecks.
- **Customization limits:** Launcher refresh timing depends on the launcher and happens after leaving Vern. Android 12+ provides the configurable system splash motions; Android 9–11 uses a matching static starting window. Page curl applies to reflowed text pages, with native selectable text restored after a turn; original-document/PDF viewers retain their own navigation. Large-book memory/performance and other launchers/API levels remain device checks. High contrast keeps cards straight and reduced motion disables decorative arrival/page turns.
- **Later theme/startup correction:** The subsequent 14 October debug update resolves theme-following by family/light-dark mode, expands to 20 icon palettes and moves the startup preview beside its controls. Two JVM regressions and debug/resource compilation passed. Physical motion and launcher checks for this later revision are deferred at the user's request; the preceding 13 phone tests should not be treated as verification of these later changes. See docs/verification/2026-10-14/THEME_STARTUP_FIXES.md.

- **Coverage — expanded:** JVM tests cover parsers, speech planning, audio processing, reading sessions and storage/restore safety. Instrumentation covers Compose UI, ViewModel flows, playback, imports, Notes, widgets and persistence. The old single-smoke-test description is obsolete.
- **Debug results:** The earlier 14 October recheck passed all 474 JVM tests and verified fifteen focused phone checks across split runs. Its delayed tab-chrome approach was subsequently rejected and replaced. The latest follow-up passed nine Samsung checks covering coordinated headers/pills/player during tab drags, extracted-reader reveal taps surviving automatic sentence following, all five page-turn configurations, scrolling, native selection, double taps and pinch. See [the earlier report](docs/verification/2026-10-14/DEBUG_262_RECHECK.md) and [the current tab/tap report](docs/verification/2026-10-14/TAB_TAP_FOLLOWUP.md). This is not all-device, measured frame-rate or release/R8 acceptance.
- **Historical results:** The 1 October follow-up review records 350 passing JVM tests and 38 passing phone tests on the SM-A175F. Later checks are in [the work log](docs/ACCEPTANCE_BACKLOG.md) and docs/verification. Results apply to their recorded revisions/subsets.
- **Release versus debug:** Debug success does not establish behavior under release R8/resource shrinking. The [10 October release report](docs/RELEASE_BUILD_2026-10-10.md) records signed assembly/packaging checks for that snapshot. Later source edits require a new release build.
- **Remaining acceptance:** Broader screen/device/API coverage, real-model listening/export, actual recipient sharing compatibility, long-history use and lifecycle/idle-policy behavior remain pending. See [the acceptance backlog](docs/ACCEPTANCE_BACKLOG.md) and [device checklist](docs/NEXT_UPDATE_DEVICE_CHECKS.md). Historical icon descriptions there may be superseded.
- **Current launcher:** The 13 October update includes adaptive background/foreground/monochrome layers, a verified 66 dp safe circle, standard launcher names, a white notification silhouette and documented animated-splash dimensions. Resource compilation and mask previews passed. A separate debug install passed normal Samsung SM-A175F launcher and cold-start splash visual checks on 13 October; themed icons and other devices remain unverified. See [icon notes](docs/brand/ICON_GENERATION.md).

## 15 October security follow-up

Private playback actions now require an app capability; downloads and ZIP/TAR expansion have budgets; browser downloads require confirmation; provider roots are narrowed; APK installation verifies package, newer code and compatible signer. The Commons Lang transitive dependency is constrained to 3.18.0. All 480 JVM tests and five focused Samsung security/playback checks passed for this snapshot. Lint, broad native-binary assessment, real-recipient sharing and all-device acceptance are incomplete.

APK/Play compatibility is currently blocked by different distribution signing keys, confirmed in Play Console. Future Play-compatible APKs must use Play signing; changing the upload keystore or version alone does not migrate existing APK users. See [compatibility](docs/UPDATE_COMPATIBILITY.md).

The bot token is removed from current source but remains in earlier Git history; the owner declined rotation. The hardened Worker is deployed with encrypted token and webhook-secret bindings, and GitHub Actions has the encrypted bot token. Live checks pass health, forged-update refusal, authenticated empty-update acceptance and removal of public admin routes. No announcement was sent. Local release builds were subsequently authorized on 15 October; their final verification is separate from the earlier debug results.
