# Future fixes

Updated 10 October 2026. This file distinguishes implemented changes from remaining acceptance. Implementation history and verification evidence are in [NEXT_UPDATE_WORK_LOG.md](NEXT_UPDATE_WORK_LOG.md). The 10 October device checks use the separate `.checks` app. The user's latest instruction authorizes a local signed release build, updating the original project and cleanup of generated worktree files. Publication remains outside this task. Final build verification is recorded in [release report](docs/RELEASE_BUILD_2026-10-10.md).

## Current final-fix requests — 10 October 2026

- **Web lookup acceptance:** durable background lookup, Wiktionary fallback, source attribution and Find/Refresh meaning are implemented. A live phone check confirms a real meaning, original sentence/location and repository reload; a two-phase force-stop/restart check also passes. Still exercise the complete browser handoff in the regular reader and native PDF, offline/reconnect, and retry of older entries. See [verification](docs/ONBOARDING_LOOKUP_NOTES_FINAL_FIXES.md).
- **Notes sharing acceptance:** separate text/PDF, individual recordings and importable complete-note ZIP are implemented. Phone checks pass URI access, playable legacy AMR extraction, unchanged originals, incoming attachments and ZIP restoration. A ten-page synthetic PDF retains all 360 lines, with first/last pages visually checked. Actual WhatsApp recipient reading/playback remains a hands-on check.
- **Onboarding acceptance:** the shared slider and scene/page/progress/button motion are implemented; normal/reduced-motion navigation and saved tuning pass phone checks. Quests remain enabled: dismissed incomplete progress and completed/celebrated state survive reload in the phone check. Subjective motion and preview voice quality remain hands-on checks.
- **Further release shrinking investigation is complete:** the isolated debug experiment reduces download size but increases installed code storage. Keep production packaging unchanged. See the measured tradeoff in [size review](docs/RELEASE_SIZE_REVIEW_2026-10-09.md).

## Reading Insights — remaining acceptance

The session ledger and Overview/Books/History redesign are implemented. Overview restores Streaks, Weekly App Activity and Reading Activity, with an animated flame/trophy, day indicators, weekly bars, a layered reading/listening ring and Month/Year heatmap. The final creative pass adds restrained motion and tonal colour, with reduced-motion fallbacks. Goals and timer/session tools live in History; per-book detail lives in Books. Reading and listening use separate measured sessions; confirmed audio start/end excludes preparation and buffering. Visible silent reading uses adjustable inactivity or an explicit timer, and audio owns the clock exclusively. History supports manual entries, corrections, exclusion and restoration of original measurements. Home, goals, monthly book totals, widgets and backup/restore consume the same records; old aggregate history stays labelled legacy/unknown.

- Verify real-engine listening over long pause/resume, seek, audio interruption and foreground/background transitions. The phone event-clock test and system/synthetic neural startup tests pass; this is not human real-model listening acceptance.
- Exercise process death/restart and timezone/month rollover on the phone. Monotonic timing, midnight splitting, interrupted-session recovery and restore deduplication have focused automated coverage.
- Per-location first-visit/revisit/unknown duration classification is implemented in Books, respecting source revisions, seeks, checkpoints, exclusions and corrections.
- CSV session export is implemented in History, and a 10,000-session classifier check passes. Review long-history responsiveness under actual device use.
- Five widget layouts pass phone rendering at two sizes in light/dark mode (20 renders), including compact icon-only actions. Broaden idle-policy and per-book interaction acceptance on actual reading material.

Implementation and evidence: [Reading Insights audit](docs/READING_INSIGHTS_SESSION_AUDIT.md).

## Original-document comparison — stage 9

- Phone checks cover ISO 14971 slides 1, 2, 3, 5, 8, 12, 14, 21, 29, 40, 53 and 67, including images, diagrams and text. Source auto-fit clipping is fixed; controls are reachable and Heel Stick text is selectable. Ten slides were visually compared with locally rendered source images. The source renderer returned blank images for slides 1/2; those two have device/parser checks only. This is representative coverage, not exact fidelity for all 67 slides or fallback variants.
- Investigate repeated/fragmented labels against the original deck; preserve intentional diagram repetitions. Do not blindly deduplicate strings.
- Complete exact DOCX source comparison when a source-rendering runtime is available. The authorized breast-milk report passed import/cover/table/navigation phone checks, but Word pagination remains approximate. LAB 2 was inspected locally; its phone transfer was rejected by automatic approval review without explicit destination permission.

The slide/run-formatting source refinements and representative phone checks are implemented; broader visual acceptance remains. Reference details are in [FOLLOWUP_IMPLEMENTATION_NOTES.md](FOLLOWUP_IMPLEMENTATION_NOTES.md).

## Playback and study extensions — stage 11

- Shared source-mapped speech plan and cast JSON v2 with engine/locale identity are implemented and device-checked.
- Neural RSVP now follows the shared playback service: Android source-word callbacks where available, clearly labelled PCM estimates for neural voices, and sentence fallback. Focused timing and real-service temporary-pace checks pass; wider mixed-engine integration and human listening remain pending.
- Mixed Android/neural cast playback/export is implemented with one live output, bounded native ownership, explicit unavailable voices and streaming WAV normalization. Device checks use synthetic native voices and real offline Android output.
- Typed managed attachments and quiz media are implemented. Annotation database migration is additive; saved quizzes and their owned images/recordings now survive portable ZIP backup. Stable identities, size/hash checks and restore path rebasing are device-checked.

Implementation details are in [PLAYBACK_STUDY_EXTENSION_PLAN.md](PLAYBACK_STUDY_EXTENSION_PLAN.md). Shared speech planning, mixed casts, source-driven RSVP, typed owned media and portable quiz backups are implemented. Real-model/human acceptance remains the paused stage 13 gate.

## Notes follow-up

- User selected portable backup/restore for cross-device Notes. Full ZIP archives carry managed attachments; automatic cloud/WebDAV/local-network sync is outside the agreed scope.
- Managed-media cleanup is implemented with preview, protected live/Trash/revision/recovery references, a one-day grace period and revalidation immediately before deletion. Phone tests cover shared files, sentence audio and newly reintroduced references. Recorder and backup leases now protect in-flight files; the snapshot/manifest captures references and identities under the library lock. Further ownership beyond these folders is outside current scope.

## Pending review and acceptance

- Latest user choice is a modern violet book with ivory earbuds and a warm ivory launcher background. The launcher has an Android monochrome companion. The latest Home correction uses a large, bold, text-only **Vern** in the same neutral `onSurface` color as Library/Notes titles. Midnight (System) is the fresh/unset default; saved explicit theme choices are preserved. The Home hero shares the Streaks card graphics while preserving its contents and actions. Real liquid glass remains: LastWave-style lens/highlight is restored across shared chrome, with phone checks passing.
- Check voice downloads, real-model sound quality and Lite hiss with human listening. The hiss improvement is not evidence of complete resolution.
- Check saved book copies, existing vocabulary entries and Notes recipient compatibility across actual supported formats.
- Broaden Home/Added acceptance across all theme packs, weekly network/download actions and Monday rollover. The final polish passed compilation, focused UI tests (including search at 1.5× text size/320 dp) and actual phone checks for goals, restart persistence, covers/placeholders and bottom-navigation clearance; see `docs/HOME_ADDED_POLISH_REVIEW.md`.
- Widget light/dark layout checks are complete; real launcher-host resizing, per-instance flashcard state and exact-book routing still benefit from hands-on acceptance.
- Listen redesign is implemented and passes dark Liquid Glass, light theme with glass enabled, compact/large-text/reduced-motion checks, plus glass navigation and native PDF checks. Broaden subjective acceptance across AMOLED and all theme packs.
- **Stage 13 export/listening acceptance is paused:** resume real-model export, backup/media round trips and human pitch/cadence listening only when requested.

Stage 12 broader Office fidelity is excluded by the user. This backlog does not authorize release publication or lift the paused stage 13 acceptance checks.


Scope confirmed 2026-10-10: cross-device Notes uses portable backup/restore; automatic cloud sync is excluded by the user. The selected book/earbuds variant supersedes the earlier Figma mark. Books and History share Overview-style motion, graphics, and book covers (five phone checks passed). The larger playback/study implementations are now present; the explicitly listed acceptance limits remain.

Legacy streak compatibility is fixed and phone-verified: old reading-day markers remain visible in streaks and Month/Year activity, without inventing durations or sessions. Old JSON backup imports remain idempotent.
