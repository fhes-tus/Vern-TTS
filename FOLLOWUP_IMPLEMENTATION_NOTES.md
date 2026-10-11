# Remaining original-document follow-up — stage 9

Updated 10 October 2026. Completed reader, outline, pronunciation and Notes repairs have been removed from this pending-work file. Their evidence remains in [PROGRESS.md](PROGRESS.md) and [IMPLEMENTATION_PROGRESS.md](IMPLEMENTATION_PROGRESS.md).

## Supplied-document comparison checkpoint

Source deck: `C:/Users/wwwfe/Downloads/Telegram Desktop/5B Medical Device Risk Management Using ISO 14971.pptx`.

- Check the original deck and phone rendering for duplicated/fragmented labels in Scope of the Plan: Design & Development, Production, Warehouse, Activate and Dispose. Preserve intentional labels and diagram relationships; the report alone does not establish a duplication cause.
- Compare short Heel Stick slides with the preferred compact, centred source layout. Verify both positioned and fallback rendering, safe system insets and reachable navigation/zoom controls.
- Compare source text styles, spacing, tables and images in representative DOCX files. Existing layout remains approximate; broader Word typesetting work is excluded under stage 12.
- Confirm source run-level font size, emphasis and colour alongside the unchanged text/selection buffer. Keep readable fallback where a source structure cannot be rendered reliably.

Screenshot references:

- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_161232_Vern Preview.png` — tall Heel Stick reference.
- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_161539_Vern Preview.png` — duplicated/fragmented labels.
- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_163408_Vern TTS.png` — preferred compact Heel Stick.
- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_163307_Vern Preview.png` — positioned slide with clipped/detached controls.

Phone/source comparisons now cover slides 8, 12 and 14 of the supplied deck. Source auto-fit boxes were clipping text: the positioned renderer now scales only boxes that request auto-fit, preserves mixed run sizes and source geometry, and avoids inventing bullets for empty paragraphs. Six parser cases and three phone source-canvas cases pass; title, diagram, compact Heel Stick layout and selection were checked. Intentional diagram labels are preserved. These checks do not establish exact fidelity for all 67 slides or fallback variants.

The authorized breast-milk storage/warming DOCX imports its cover/logo and table and keeps page controls reachable. The wide table scrolls horizontally. DOCX pagination remains approximate; exact source rendering is blocked by the absent LibreOffice runtime. LAB 2 - ASSIGNMENT (REPORT).docx was identified and its source styles/list structure inspected locally, but automatic approval review rejected its phone transfer because file selection did not establish destination permission. No LAB 2 phone comparison is claimed.

The local Reading Insights session ledger and restored graphical Overview are implemented. Remaining acceptance and larger features are in [FUTURE_UI_PLAN.md](FUTURE_UI_PLAN.md). Broader Word fidelity remains excluded under stage 12.


### 2026-10-10 — Books/History motion, supplied icon, and portable-backup scope
- Books now shows cached real book covers (generated binding fallback when absent), gentle cover motion, an entering position bar, and animated reading/listening duration bars. Continue, explicit timer, completion dates and Mark finished remain available.
- History now uses animated activity bars, a daily-goal ring, date-grouped sessions with covers and mode emblems, and clear correction/manual/exclusion badges. Every tab keeps its own scroll position. Decorative loops stop with Reduce motion; zero activity does not draw a false coloured bar.
- Kept the supplied Figma PNG unchanged for in-app artwork. Added scalable colour and monochrome Android launcher vectors based on the user's mark, with adaptive-mask safe spacing. Preview: `.checks/icon-user-design/launcher-masks.png`.
- User chose portable backup/restore for cross-device Notes. Existing archive/media-manifest restore remains the route; cloud/WebDAV/local-network auto-sync is outside this agreed scope. Corrected the manual's unsupported automatic-cloud-sync claims.
- Final isolated debug/test build succeeded (3m03s); all five Insights device tests passed. Compact 320dp/1.5-font screenshots inspected. No release build performed. Wider playback/study work remains active; this entry does not claim that backlog is complete.


### 2026-10-10 — Theme-adaptive brand and legacy streak compatibility
- Home uses tintable native-vector layers at 38dp with a 28sp `ern` wordmark. Both layers follow the active palette and light/dark surface; the header grows with large text. Four live palette changes at 320dp/1.5x font pass (`.checks/home-brand-rsvp-final-phone.txt`).
- The supplied mark now has a filled blue torso, original blue (#3F78FF) and white foreground, and a lighter slate-blue (#3B4D65) background: three flat paints, with matching adaptive/legacy/monochrome launcher resources and an in-app bitmap generated from the vector. The original supplied image is preserved in the ignored check directory. This supersedes the earlier original-PNG/ivory-and-two-blues notes.
- Fixed the missing old streaks: legacy `readDocumentIds` day markers now participate in active calendar days/current/best streaks and weekly book counts even when old data has no `readingMillis`. Unknown minutes remain unknown; measured reading/listening totals are unchanged, app launches alone do not qualify, and the stored history is not rewritten to invent sessions.
- Phone regression passes old JSON without a duration field, an old/new three-day streak, a five-day historical best, two repeated backup imports, app-only and excluded-session days, and the selected calendar day's unknown-duration label (`.checks/legacy-streak-calendar-phone.txt`). Only the isolated `.checks` app was installed; users see their preserved markers after installing a build containing the compatibility fix.
- Audio RSVP uses the shared playback service and original source spans; buffering does not advance words, Android callbacks provide word ranges, neural PCM timing is explicitly estimated, and temporary RSVP pace does not overwrite book pace. Three focused JVM tests and six real-service checks passed; the synchronized UI timing check passed with the Home brand check. Shared immutable speech plans, mixed-engine cast and broader acceptance remain pending. No release build performed.


### 2026-10-10 — Restore LastWave-style refractive glass
- User clarified that removing gloss must preserve real liquid glass. Read the authorized LastWave `ui/theme/LiquidGlass.kt`: its material uses `drawBackdrop`, vibrancy, colour controls, an edge lens, a narrow highlight and a translucent scrim. Vern's shared helper now uses that recipe rather than `drawPlainBackdrop` plus painted reflection gradients.
- Default blur is 8dp, brightness .05, saturation 1.5; theme tint is lighter (roughly 22–28% for current controls). A 1dp library highlight defines the real edge. Capsule lens dimensions match LastWave; expanded rectangular panels bound the lens by their actual rounded corners so their centre stays undistorted. No opaque top gloss overlay or duplicate glass border is added.
- The single helper covers library navigation and separated Notes capsule, Add/Note/import floating controls, library/text/original/native-PDF players and Listen controls. Reduced transparency, high contrast and unsupported-device fallbacks remain intact. The icon palette and legacy streak fix remain unchanged.
- Final app/test assembly passed in 1m20s (`.checks/refractive-glass-bounded-final-build.txt`). Playback/expansion, separate navigation gap and native PDF checks passed on this final source. Listen's two safe-area/interaction cases passed on the capsule-equivalent source. The new refraction probe initially sampled a flat centre and then a one-direction stripe pattern; it now samples inside the curved band with a two-dimensional pattern and passes (`.checks/refractive-glass-two-axis-probe-phone.txt`). Those test-fixture corrections did not weaken the required visual difference.
- Final expanded player, navigation, Listen and native-PDF screenshots reviewed; the bounded lens removes the earlier large corner folds. All tested controls remain sharp/clickable. Only isolated `.checks` installations; no release build or main/Preview data changes.


### 2026-10-10 — Final implementation, book/earbuds branding and Midnight default
- Replaced the earlier headphone/V mark with the selected violet book and ivory wired-earbuds design, warm ivory adaptive background, proportional launcher padding, legacy bitmap and Android monochrome companion. Built-in imagegen source/prompt is preserved in `docs/brand/ICON_GENERATION.md`; the generated foreground retains fixed artwork colors.
- Latest user correction supersedes the icon-plus-wordmark header: Home now shows text-only **Vern**, 32sp Black (900) weight, using the same neutral `onSurface` color as Library/Notes titles. Midnight (System) is the fresh/unset default; explicit saved theme choices stay intact.
- Extracted the Streaks card's layered discs/orbit lines into a shared animated graphic and applied it to the Home hero. Existing cover, title, progress, Continue, Dismiss, first-read and weekly-pick content/actions remain present. Reduced motion stops decorative looping.
- Implemented the immutable source-mapped speech plan, cast JSON v2 engine/locale identity, one-owner mixed playback/export, streaming Android WAV normalization, explicit unavailable voices and cancellation cleanup. Device checks combine synthetic native output with real offline Android TTS; this does not lift the paused real-model/human stage 13 acceptance gate.
- Added typed owned attachments, recorder/backup leases, quiz images/recordings with play/stop/remove controls, stable identity/hash validation and saved-quiz portable ZIP restoration. Portable backup/restore remains the user-selected Notes cross-device route.
- Books classifies time at first/revisit/unknown reading locations using source revisions and measured sessions, without inventing skipped ranges. History streams CSV with correction/status fields and formula-safe cells; a 10,000-session classifier check passes.
- Focused JVM verification: 37 cases passed across backup safety, neural-output reliability, Note sharing, reading-location classification, session clocks, RSVP timing and WAV normalization. Phone checks pass managed attachment migration/restore, mixed speech ownership, real Android interleaving, Note share formats and quiz media controls. Voice quality/Lite hiss still requires human listening.
- Five widget layouts rendered at two sizes in light/dark mode (20 renders). Dismissed incomplete quests and completed/celebrated progress survive reload. A two-phase lookup test enqueues, force-stops/restarts the app and resolves the original selection with meaning, sentence/location intact.
- A synthetic 360-line Note PDF passes full text extraction and ten-page pagination; first/last rendered pages were inspected. Actual WhatsApp recipient delivery was not performed.
- Restored the authorized ISO/breast-milk fixtures after discovering the first latest batch had skipped them. The explicit rerun passes both tests (`.checks/final-source-document-phone.txt`): 12 ISO slide canvases, reachable controls/non-clipping text and selectable Heel Stick, plus the breast-milk report cover/table/illustration. Ten slides compared against locally rendered source images; the source renderer produced blank slides 1/2, so those two have device/parser checks only. Word pagination remains approximate; broader Office work stays excluded.
- Debug DEX packing investigation is complete: download bytes fell from 141.38 to 63.82 MiB, but installed code grew by 21.89 MiB. Production packaging stays unchanged; detailed measurements are in `docs/RELEASE_SIZE_REVIEW_2026-10-09.md`.
- Only the isolated `.checks` app/test runner was installed. Production/Preview data was not reset or changed. No release build or publication was performed.

- Header/hero verification refinement: the original fixture expected 16% for sentence index 16, although the existing progress helper counts the seventeenth sentence (17%). Compose paragraph constraint width also differs from the Text node's intrinsic rendered width; final checks inspect the actual line width, ellipsis and line count. These corrections retain full text/control-fit requirements.
- Visual inspection found the compact 1.5x-font Continue label wrapping. On narrow cards or font scales at least 1.3, the Continue action now sits below the cover/details at full width; the regular roomy layout keeps its inline action.

- Latest correction: default theme is **Midnight (System)** (`midnight_system`), so the phone chooses its light/dark variant. Explicit saved themes remain preserved.
- Hamburger collapse uses a bounded 180ms slide/fade (120ms fade with Reduce motion), fades its scrim together and disables the Dialog's duplicate window dim/fade. The close callback no longer waits for an invisible spring tail while Home remains blocked.

- Final source build and six theme catalog tests pass (`.checks/midnight-system-sidebar-final-build.txt`). The phone's four-palette neutral 32sp/900-weight header check and compact/large-font/reduced-motion hero check pass on this source (`.checks/midnight-system-sidebar-final-phone.txt`). The revised Continue action was visually inspected: its label stays on one line at 320dp/1.5x text.

- Final menu-only phone regression passes (`.checks/sidebar-close-final-phone.txt`): normal and reduced-motion closure sets dismissal within 224ms of the controlled animation clock, removes the Dialog and allows an immediate Home button click. The test explicitly commits disposal before reopening, so stopped-clock false/true coalescing cannot retain a closed drawer. Final header, hero, theme and menu checks are complete; no release build performed.
