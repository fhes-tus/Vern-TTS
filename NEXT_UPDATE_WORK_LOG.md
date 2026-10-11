# Next update work log

Updated 9 October 2026. Preserve all pre-existing worktree changes. Home and weekly book selection are already complete and outside this work.

## Working rules

- Finish one coherent change before starting the next; keep the app compilable.
- Do not assemble/install a debug APK after every pass. Use focused compilation and regression checks at milestones.
- Ask the user when device observations or a product decision are necessary.

## 1. Notes sharing — implemented, compilation and targeted tests passed

- Incoming shares retain CharSequence text, all EXTRA_STREAM attachments, and ClipData streams (deduplicated).
- Share to Notes accepts single/multiple shares of all attachment MIME types.
- Import copies attachments into app-owned storage on IO, preserves file names/types, and persists the complete note before opening the editor. An untouched shared note is already saved.
- Failed imports clean up copied files and report failure instead of storing an inaccessible URI.
- Opening another incoming note resets the editor session. Startup cannot overwrite the newly imported notes list with an earlier snapshot.
- Editor and Notes-list sharing use the same helper: real attachments with FileProvider read grants, clean text without attachment paths or internal link metadata.
- Added focused JVM checks and isolated `.checks` device tests covering unchanged text, mixed attachments, sender-file removal, MIME selection, and read grants.

Validation: app Kotlin and device-test Kotlin compile successfully. 30 targeted Notes JVM tests pass (NoteSharingTest, NoteEditingLogicTest, NotesContinuityTest). Fixed eight pre-existing device-test callbacks that lacked the editor's label argument. Device tests are compiled, not run. No debug APK was assembled or installed. The project exposes only debug unit tests; no release unit-test task exists.

Device acceptance still required: share unedited text and links; share one/multiple photos, PDF, audio, and video from other apps; reopen the notes; share them outward into a receiving app.

## Remaining order

2. Voice downloads — implemented, compiled; device download acceptance pending:
   - Separate UUID archive/staging paths and guarded orphan cleanup prevent different downloads destroying one another.
   - Per-package locking deduplicates Studio and same-voice requests; three downloads run together and additional requests queue.
   - Atomic progress updates; distinguish queued/downloading/installing; network completion stays below 100% until install verification passes.
   - Shared pronunciation data installation is synchronized. Downloads use application scope and continue after leaving/scrolling the voice screen. They do not yet resume across process death.
   - Reject empty/truncated downloads; close extraction streams; propagate cancellation.
   - Studio's outdated 53-speaker file-size guard rejected upstream's updated 54-speaker v1.0 export. Accept both supported complete exports and keep English voice indices unchanged. Upstream source: https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/kokoro/v1.0/generate_voices_bin.py
   - Two package-shape regression tests pass, for 32 targeted JVM tests total. App/device-test compilation passed after these changes, with no APK assembled.
   - User should test Studio download and 3+ concurrent Lite downloads, leave the screen, then check every requested voice remains installed after restarting.
3. Lite humming at normal speed/pitch; inspect shared PCM, sample-rate, pitch and playback processing.
4. Kokoro/Piper reading quality: text preparation, phrase splitting, pauses, supported model controls; compare passages before/after.
5. Vocabulary web lookup: retain word and meaning.
6. Downloaded-book export to shared storage, surviving app uninstall.
7. Selective liquid glass based on Last Wave.
8. Onboarding: distinct artwork and clearer wording.
9. Icon/emoji polish, release-size measurement, final manual accuracy check.

Last Wave source located at `C:\Users\wwwfe\OneDrive\Desktop\Wave app`. Relevant files: `app/src/main/java/com/lastwave/app/ui/theme/LiquidGlass.kt`, `ui/common/LiquidGlassCard.kt`, `playback/NativePcmAudioProcessor.kt`, `NativeProcessingAudioSink.kt`, `AudioEffectsEngine.kt`, and `app/src/main/cpp/AudioEngine.cpp`. Read only; do not modify that project.

## Audio audit checkpoint / user preference

- Piper currently uses int8 models and converts raw floats directly to PCM16. Its normal-pitch path bypasses pitch processing. AudioTrack uses the engine's sample rate, so a hardcoded-rate explanation has not been established.
- Kokoro already contains notch filters, fixed headroom and limiting; do not apply speculative tone filters to Piper without comparing generated audio.
- Last Wave is a music player: useful ideas are preserving the source sample rate and avoiding doubled DSP/gain; it does not provide Kokoro/Piper text phonemization.
- Public upstream release metadata confirms Alan, Jenny and Lessac full-precision archives are about 67.2 MB each versus 21.0–21.1 MB int8. Half-precision packages also exist around 35.8–35.9 MB (including LJSpeech medium and several low voices). These optional model downloads do not increase APK size.
- User questioned whether 67 MB is necessary and requested a lighter option. Keep the current lightweight models; investigate processing/configuration first. A roughly 36 MB half-precision package can be compared if needed. Do not switch the whole catalog to 67 MB models. No further model-size approval question is pending.
- Next audio evidence needed: a short exported Lite WAV that contains the humming at normal speed/pitch, and whether the noise also appears in exported audio versus only live playback. This separates synthesis/model noise from device/output processing. A phone microphone recording alone can introduce its own noise.
- Vocabulary inspection found a separate native PDF viewer helper writing directly to vocabulary notes. Audit that path alongside the ViewModel lookup before changing dictionary logic.

## Vocabulary and book storage checkpoint

- Implemented shared `VocabularyLookup`: preserves apostrophes and phrase boundaries, uses locale-independent normalization, scans usable definitions across entries/senses, and falls back to phonetics entries. Reader and native PDF actions resolve real definitions (AI in context when configured, otherwise dictionary), preserve source/context, and append through atomic note mutation.
- Native PDF selection now includes Web lookup. Unavailable/offline definitions are explicitly labeled rather than saving a fake lookup message.
- Added Save to phone to all Library book-card menus and the details sheet. Uses Android's create-document picker, copies available originals on IO, and exports catalog/extracted text as genuine TXT. Shared-storage copies survive app uninstall. Export preserves source format and rejects a changed/missing original during picker selection.
- Corrected the related book-share fallback which previously put extracted text inside files named PDF/EPUB.
- Added VocabularyLookupTest (4 checks) and BookExportTest (2 checks). Production and device-test Kotlin compilation passed; these tests plus ReaderFollowupRegressionTest pass (14 total). Initial compile found a missing OutlinedButton import, corrected before the successful build. No APK assembled.
- Device checks needed: select a dictionary word in reader and PDF, reopen Study vocabulary with meaning; Save to phone for catalog TXT and original EPUB/PDF, reopen the exported files outside Vern.

## Visual preferences / remaining checkpoint

- User prefers Material-style icons for streaks, especially Study Hub; use outlined Material icons rather than emoji glyphs for that indicator.
- Baldrick's icon redesign is still pending. Keep current app icon.
- Last Wave uses Kyant Backdrop lens + highlight + thinner scrim; current Vern implementation only blurs and overlays a heavy tint. The upstream lens supports rounded/corner-based shapes, not generic paths. Preserve the Notes navigation split shape using blur-only fallback during its custom path animation; apply lens to rounded controls.
- Existing release configuration already minifies/shrinks resources and provides separate ARM64/ARMv7 APKs plus universal. No current release APK exists in this worktree; old debug artifacts are not release-size measurements. Debug ARM64 inspection shows ONNX native runtime is the largest native component; models are downloaded separately. Do not remove voices/features/ABIs or alter reflection keep rules without validation.

## Visual and reading preparation implementation — compilation and targeted tests passed

- Existing rounded glass controls now use Last Wave's lens/edge-highlight approach with lighter tint and blur, using the already installed Backdrop dependency. No new graphics dependency. Generic Notes split paths retain blur-only drawing to avoid the upstream unsupported-shape exception. Standard navigation uses an actual RoundedCornerShape at rest so refraction can run. Older/non-capable devices and reduced-transparency/high-contrast settings retain their fallback.
- Onboarding has five distinct themed vector scenes (reading nook, open spread, headphones/waveform, notes/flashcards, new-chapter path). Updated copy; preferences and setup flow unchanged. No bitmap assets added.
- Study Hub streaks now use outlined Material fire/plant/trophy icons and theme colors. Restore/history and notebook actions have corresponding Material icons. App icon remains pending Baldrick.
- Manual gets narrow accuracy updates for external book copies and incoming/outgoing Notes shares; existing manual work retained.
- Reader already has mapped symbol expansion and pronunciation rules. Added shared final neural-text cleanup for Kokoro/Piper (Unicode NFC, soft hyphens/invisible copy artifacts, space normalization, existing speech sanitation). No speculative audio EQ/noise control/model-size change. Added four regression tests for actual copied-text and punctuation cases.
- Reference for supported refraction shapes: https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/backdrop/src/commonMain/kotlin/com/kyant/backdrop/effects/Lens.kt

Consolidated production and device-test Kotlin compilation passed. Thirty focused tests pass (NeuralSpeechText 4, SpeechPunctuation 9, ReadingSymbols 6, VeritasThemeCatalog 5, VocabularyLookup 4, BookExport 2). No debug APK assembled. Release build/signing is available; assembly and size measurement in progress. Device visual checks still required for glass over varied backgrounds, Notes navigation animation, reduced transparency/high contrast, and onboarding on smaller screens.

## Audio evidence — 9 October 2026

- User supplied `October-2026- SPORTSTRONIC_136adb67-e12a-4795-96dc-076ac4d4b55a.wav` (Temp directory), exported with Lite Marcus US, speed/pitch 1.0. They clarify speech itself is fine; background is radio-like white noise/hiss.
- WAV: mono PCM16, 22,050 Hz, 251.82 s, peak 0.4843, RMS 0.03948, DC mean 0.000409, zero clipped samples. No clipping evidence.
- Candidate narrow spectral peaks near 4.48/8.96 kHz were tested in equal-level 18-second clips. User hears little/no difference. **Do not implement this diagnostic notch filter in the app.**
- Reproducible NumPy diagnostics are `tools/analyze_voice_wav.py` and `tools/compare_voice_notches.py`; reports/clips in ignored `.checks/audio-analysis`.
- Exact Marcus/HFC-male upstream archives: int8 20,984,819 bytes; fp16 35,924,983; fp32 67,214,049. The same voice has a roughly 36 MB alternative (not merely a different voice). Int8/fp16 downloaded only to ignored diagnostics, not app/catalog/default downloads.
- `tools/compare_marcus_models.py` generates identical text using int8 default, int8 lower latent noise and fp16 default. Isolated Sherpa runtime is in `.checks/tts-python`. Windows sandbox cannot read its installed package files; run this diagnostic with the approved escalation. Desktop runtime is 1.13.8; app JNI/bindings are 1.13.4, so desktop comparisons need eventual device confirmation.
- App Piper uses standard VITS noiseScale 0.667/noiseScaleW 0.8; neutral neural speech bypasses punctuation pitch expression and normal pitch bypasses pitch shifting. Narration character overrides can alter pitch if enabled. No proven synthesis parameter bug yet.
- Next decision depends on listening to the generated model/settings comparison. Preserve 21 MB default until evidence supports a quality change.

## Release checkpoint and follow-up audio evidence

- Signed release 2.6.0 (code 40) assembles successfully; ARM64 APK signature verified with apksigner. No debug APK assembled, device install or publication performed.
- Excluded unused Sherpa C/C++ entry points from APK packaging. Upstream bundled README confirms Android needs only JNI and ONNX Runtime; ELF dependencies agree for both ABIs. Retained source binaries and both supported ABIs.
- Before/after APK bytes: ARM64 57,960,884 → 53,111,052 (55.28 → 50.65 MiB); ARMv7 48,582,226 → 45,125,030 (46.33 → 43.03 MiB); universal 79,859,696 → 71,552,668 (76.16 → 68.24 MiB). ZIP checks confirm required JNI/ORT present, unused C/C++ entry points absent.
- ADB reports no connected device; runtime loading, media shares and visual behavior still need phone testing.
- User hears less hiss in both new int8 samples, with both about equally clean. Do not lower noiseScale by default based on that comparison: unchanged settings also improved.
- fp16 export fails type validation; diagnostic inspection found seven incorrect Cast destinations, duplicate metadata and further mixed-type Equal inputs. Correcting Casts alone does not yield a valid graph. Do not deploy the 36 MB package or spend more effort repairing that upstream export in this update.
- Important upstream difference: Sherpa 1.13.5 fixes PiperPhonemesToIdsVits missing pad after BOS (https://github.com/k2-fsa/sherpa-onnx/pull/3770). Desktop comparison uses 1.13.8, app 1.13.4. Generate the same passage/model with 1.13.4 next to isolate runtime parsing. No runtime upgrade or production noise filter has been applied at this checkpoint.

## Runtime parsing correction — final verification in progress

- Generated a same-passage/model/settings sample using desktop Sherpa 1.13.4. User's response to old/new runtime comparison: "i can manage". This is tolerability feedback, not proof that the hiss was solely a runtime bug. Both earlier 1.13.8 variants were equally clean to them.
- Upgraded the app's packaged ARM64/ARMv7 JNI and ONNX Runtime pair to official 1.13.8, including the verified Piper framing correction. Keep all voice URLs, model sizes and standard noiseScale settings unchanged; no unhelpful notch EQ or fp16 model deployed.
- Verified official Android archive SHA-256; backed up the previous four packaged binaries in ignored `.checks/sherpa-1.13.4-app-backup`. Native configuration field names/types/order match all ten tagged Kotlin config classes, all six used JNI functions are exported in both ABIs, dependencies still require only ORT/system libraries, and ELF LOAD alignment is 16 KiB.
- Added upstream's native-pointer initialization guard so a failed native constructor cannot masquerade as a ready voice. Bindings keep only the app's used APIs. Binary source and hashes are recorded in `app/src/main/jniLibs/SHERPA_RUNTIME.md`.
- Production/device-test Kotlin compilation, fourteen focused JVM regressions and signed release assembly are running after these changes. Final APK sizes must be remeasured: the new runtime adds a small amount versus the packaging-only checkpoint above.

## User scope clarification

- Release assembly is for local shrinkage measurement only. Do not publish or prepare a new public release; no publication has occurred.
- Material streak icon change belongs only to Study Hub. Restored Reading Insights' original filled fire/trophy icons, orange/gold colors and milestone emojis. Retained unrelated pre-existing tracker, reduced-motion and shape changes in that file.
- The ongoing size build compiled before this small icon restoration; run Kotlin compilation after it finishes to verify the final editable source, without assembling another APK for this cosmetic change.

## Glass follow-up from device feedback

- User likes the file-browser pill, but sees a dark shadow/inconsistency in the larger player and navbar. Audited all three call sites against Last Wave's `drawInteractiveGlass` implementation.
- Found player/navbar retained Material Surface elevation shadows beneath transparent glass. They also derived glass tint from the selected pack's opaque-control opacity: enabling floating glass with another pack could give 85–90% tint versus file browser's 36%.
- Player/navbar now use the approved file-browser recipe (10 dp blur, 36% surface tint). Remove their additional Material shadow/border only while real glass is active; Backdrop supplies the edge highlight. Retain solid fallback styling and Notes split-shape handling. Leave file-browser pill unchanged.
- Verify final Kotlin compilation after the measurement build finishes. Device check needed on the same background for expanded/collapsed player and navbar, especially when floating glass is enabled with a non-glass theme pack.

## Verified final checkpoint

- Runtime/packaging measurement build passed. Final production and device-test Kotlin compilation then passed after Reading Insights restoration and glass corrections. Fourteen focused voice/reader JVM checks pass. No debug APK assembled.
- Final measured APKs: ARM64 51.26 MiB, ARMv7 43.40 MiB, universal 69.21 MiB. Relative to the same-worktree baseline, net savings after runtime upgrade are 4.02, 2.93 and 6.95 MiB. All APK signatures and 16 KiB ZIP alignments pass; required libraries are present and unused entry points absent.
- Further DEX compression has an estimated 10.71 MiB payload saving; production setting remains unchanged pending phone install/startup/storage measurements. No extra experimental APK assembled. Size findings are in `docs/RELEASE_SIZE_REVIEW_2026-10-09.md`.
- Local size measurements only, with no install, publication or new public release. Measurement APK predates the last cosmetic changes; final editable source is compiled and ready for the user's own build.
- Next user checks: player/navbar dark shadow after this correction; same-reading Marcus playback/export against original noisy WAV; Studio and multiple voice downloads; incoming/outgoing Notes attachments; vocabulary and external book copies. See `docs/NEXT_UPDATE_DEVICE_CHECKS.md`. Baldrick's app icon remains pending.

## Broader design backlog clarification

- The approved future Reading Insights redesign remains unimplemented; it was missed in the short remaining-work summary. Its full plan is in `FUTURE_UI_PLAN.md`: durable session records, separate reading/listening, idle/pause exclusion, meaningful Overview/Books/History views and shared data for Home/goals/widgets. Restoring its icons does not complete that redesign.
- Home layout implementation and its existing goal-free visual smoke check are complete. Goal-enabled/first-use states, long titles, enlarged text, theme packs and weekly-pick actions/rollover still need review, as recorded in `NEXT_UI_REQUEST.md`. No new Home redesign is underway.
- Study Hub retains its existing study features and shared theme work; this update changed its streak indicator and repaired vocabulary lookup. A complete new Study Hub redesign is not claimed.

## Study Hub audit and overview — in progress

- User authorized checking Study Hub logic before its redesign. Review and corrections are recorded in `docs/STUDY_HUB_LOGIC_REVIEW.md`.
- Correct quiz answer resolution/counts/score conversion, atomic flashcard database/fallback reads and writes, serialized mutation/UI updates, startup study refresh, AI cancellation and visible errors, preview cache invalidation, due-card summary and durable recent-history updates.
- Following that audit, implement an Overview with actual saved collection counts, Material icons, scheduled review, real decks, explicit AI book selection and paste actions. Keep section pages focused on their material.
- Phone reconnected. Build one isolated `.checks` app/test APK for final persistence, navigation and visual verification. Do not replace or clear the user's normal/preview app. No public release is created.

## Study Hub, Home and Added — completed checkpoint

- Study Hub audit and Overview implementation are complete. Combined app/test assembly passes; 35 focused JVM tests pass (15 Hub, 12 AI parser, 4 AI study service, 4 vocabulary).
- All 8 Android checks pass on the connected ARM64 phone: 3 study persistence/concurrency cases, 2 Overview/navigation cases, and 3 Home/Added cases. The initial rapid-mutation test stalled before assertions during ActivityScenario launch; a direct main-thread view model harness with cleanup now passes the actual persistence assertions.
- User's final scope was to polish Home and My Library → Added, then finish. Home progress is consistent with the library, Recent excludes the continue-reading book, and first-use goal setup is accessible. Added has counts/reset, scroll-to-top for changed filters, query-aware empty results, search controls that accommodate larger text, and separate source/time/progress lines in tiles.
- Actual Home, Added and Study Overview visually checked. A 15-minute goal persisted through force-stop/relaunch, then the original no-goal setting was restored. Home Classics/Add scroll clear of floating navigation. Large-text search and combined-filter reset passed Compose tests on the phone.
- Final phone review found duplicate sort labeling and hidden tile time/progress; corrected both. Final app assembly passes after these cosmetic corrections. Isolated checks APK installed; normal and preview apps/data preserved. No public release created.
- Final evidence/limits: `docs/STUDY_HUB_LOGIC_REVIEW.md`, `docs/HOME_ADDED_POLISH_REVIEW.md`; ignored logs/screenshots in `.checks/study-hub-review`. Full Reading Insights redesign remains future work. App icon remains pending Baldrick; broader download/share/audio/theme acceptance remains documented separately.

## Final reader glass, navigation and animation review — completed

- Applied shared Kyant glass to extracted-text and actual-document playback panels; native PDF keeps its Android controls and draws the same material from the PDF fragment underneath. Both Liquid Glass and Glass in other themes use these paths.
- Notes navigation uses two genuinely separate rounded glass layers. Study subsection Back returns to Overview; Notes subview Back returns to All Notes; root Back returns Home. Added requested outlined menu icons and removed the circled Study introduction/subtitle.
- Device verification found and corrected a synchronous scroll reset during Pager layout. Final app/test assembly passes; all 12 Android cases pass, including the new glass/PDF/Back tests and the previous 8 regressions. The earlier 35 focused JVM cases passed. Inspected the reader/PDF and both Notes capsule screenshots. Normal/preview data are preserved; no release publication.
- User's final animation checklist asks for discovery only. Completed `docs/ANIMATION_OPPORTUNITIES_REVIEW.md`: two optional opacity transitions for verified voice installation and the daily-goal checkmark, five rejected candidates, explicit coverage of each hunt class. No animation source changes for that audit.
- Final details and evidence: `docs/GLASS_AND_NAVIGATION_FOLLOWUP.md`; `.checks/study-hub-review/glass-back-final-device-tests.txt`. Wider voice/share/export/network acceptance remains in `docs/NEXT_UPDATE_DEVICE_CHECKS.md`; full Reading Insights redesign and Baldrick's icon remain deferred.

## Explicit final animation implementation and release request — completed 10 October 2026

- User now explicitly authorizes both reported status fades, a subtle startup spinner on playback controls/widgets, and a local release build. Public publication remains outside scope.
- Added per-book transient preparation feedback, real system/neural output-start acknowledgement, immediate cancel handling, reduced-motion fallback, widget/native PDF feedback and media-session buffering state. Deferred offline model construction keeps startup work off the main thread.
- Implemented the Installed-state fade and goal-center crossfade using shared effects tokens and stationary layout. Final debug/release compilation and signed release assembly passed, plus 30 focused JVM cases and all 17 phone checks across the suite and corrected accessibility-selector retest.
- Release APKs: ARM64 51.32 MiB, ARMv7 43.46 MiB, universal 69.27 MiB; all signature and 16 KiB ZIP-alignment checks pass. Package/version remain `com.veritas.reader` / 2.6.0 (40). APKs are in `app/build/outputs/apk/release/`. Local build only; no public publication or production-app installation.
- Details: `docs/PLAYBACK_FEEDBACK_RELEASE_CHECK.md`. Preserve normal/preview app data; do not install the production release without a request.

## 10 October 2026 — onboarding, vocabulary and Notes checkpoint

Screen 3 now uses the shared app slider. Scene/page/progress/Continue motion respects reduced motion, and onboarding quests remain enabled. Web lookup durably saves context before browser handoff and resolves meanings through dictionary lookup with a verified Wiktionary fallback; older entries have Find/Refresh meaning. Notes share text/PDF and individual recordings separately, and a complete-note ZIP restores through Backup & restore. Legacy AMR extraction preserves source audio; new recordings use AAC/M4A.

Final app/test assembly passed, all 14 focused JVM checks passed, and all 9 phone checks passed, including live definition persistence and actual encoded AMR extraction. Only the isolated `.checks` app was installed. No new release was built or published. Full evidence and remaining recipient/browser/offline acceptance are in `docs/ONBOARDING_LOOKUP_NOTES_FINAL_FIXES.md`.

Remaining-fix work has begun with `docs/READING_INSIGHTS_SESSION_AUDIT.md`: current document-open timing mixes modes, and background chunk timing can include preparation. The durable measured-session implementation remains outstanding. The backlog now retains the icon dependency, voice/Lite-hiss listening and saved-book acceptance alongside the larger features.

## 10 October 2026 - Listen and restored Reading Insights checkpoint

Listen follows the supplied reference with rounded-square artwork, glossy circular skip/bookmark controls, a wide Play/Pause pill, shared sentence-position slider and speed/voice/timer pills. Read sentences scroll and animate through opacity, scale and lift. Shared glass has a restrained glossy rim; Add/Note floating pills no longer stack duplicate borders/elevation.

The measured session ledger separates actual reading/listening, suspends preparation/buffering/pauses, checkpoints recovery and preserves corrections and backup deduplication. After the user's visual correction, Overview contains Streaks, Weekly App Activity and Reading Activity, with day indicators, eight-week app bars, a measured mode ring and Month/Year heatmap. Goals/timer/session tools live in History; per-book details live in Books. Normal and 320 dp / 1.5x text-size screenshots were checked.

Nineteen focused phone cases pass: 18 in `.checks/insights-graphics-final-phone.txt`, and the ambiguous large-text test selector was corrected and passed separately in `.checks/insights-compact-final-phone.txt`, without app changes. Debug app/test assembly passes. Earlier 17 focused JVM cases passed; the PPTX refinement then passed its expanded six-case parser suite.

ISO slides 8/12/14 were compared and source auto-fit clipping fixed. The authorized breast-milk report passes import/cover/logo/table/navigation checks. DOCX pagination remains approximate and exact source render is unavailable without LibreOffice. LAB 2 was inspected locally; automatic approval review rejected its phone transfer without explicit destination permission. Private fixtures are not bundled or committed.

Only `.checks` is installed; normal/preview data are preserved. Final local release packaging is recorded in `docs/LISTEN_REDESIGN_2026-10-10.md` after success. Publication remains held; stage 13 real-model listening/export remains paused and broader Office fidelity remains excluded. Speech/RSVP/cast, sync, icon and remaining acceptance work stays in `FUTURE_UI_PLAN.md`.

## Final UI follow-up: safe areas and creative Insights

The user's screenshot showed Listen controls overlapping the status/navigation bars. Content now uses unconsumed safe-drawing insets and its existing 12 dp edge padding; the backdrop still fills the screen. A phone regression verifies top/bottom clearance and identical positions when a parent already consumes the insets.

Overview keeps Streaks, Weekly App Activity and Reading Activity. The creative pass adds a warm vector flame with drifting embers, a floating gold trophy/sparkle, background contours, gradient activity bars with entrance motion and a layered reading/listening ring with a one-second sweep and gentle arc movement. Labels/card geometry remain stationary. Reduced motion has no repeating transitions and shows the final ring.

Final app/test assembly passes (`.checks/insights-creative-final-build.txt`, 4m42s). All five focused phone cases pass (`.checks/insights-creative-final-phone.txt`): two Listen checks, Insights navigation/editing, compact graphics/week navigation and animation/reduced-motion frame comparisons. Final normal/compact Overview and both Listen glass screenshots were reviewed. Only `.checks` was installed.

The user elected to build the release themselves. The previous release optimizer was deliberately canceled; its daemon-disappeared failure is expected cancellation, not a source failure. Existing release APKs remain older. No final release was built/published or installed into the main/Preview apps for this final source pass.


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

- Final menu-only phone regression passes (`.checks/sidebar-close-final-phone.txt`): normal and reduced-motion closure sets dismissal within 224ms of the controlled animation clock, removes the Dialog and allows an immediate Home button click. The test explicitly commits disposal before reopening, so stopped-clock false/true coalescing cannot retain a closed drawer. Final header, hero, theme and menu checks are complete; no release build was performed at that checkpoint.

### Final release and original-project handoff

- The latest user instruction authorizes the signed local release, original-folder update and generated-file cleanup, preserving all important project files.
- Final launcher artwork replaces the earlier purple with Midnight Light's stellar indigo blue (#4338CA), deep indigo shading and existing ivory earbuds/pages/background. It is a fixed brand asset; Home remains the heavy neutral text-only Vern header. The built-in imagegen edit prompt and source are retained in `docs/brand`.
- Release checks, preserved crash mappings and synchronization/cleanup evidence are retained in `docs/RELEASE_BUILD_2026-10-10.md` and `docs/verification/2026-10-10`.
