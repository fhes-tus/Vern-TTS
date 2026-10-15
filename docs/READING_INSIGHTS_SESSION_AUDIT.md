# Reading Insights: implementation and acceptance, 10 October 2026

The redesign now uses a versioned local session ledger. App activity remains a separate statistic in its restored Overview card. Existing aggregate durations remain legacy history with unknown mode; they are not retroactively labelled measured reading or listening.

## Timing and persistence

- One process-wide tracker serializes visible reader and actual audio ownership. UI/audio event wall, monotonic and timezone values are captured before repository IO and source-page parsing, so slow storage does not move the measurement boundary.
- System TTS starts at `onStart`; neural playback starts at confirmed output. Preparation, buffering, pause, interruption and seek suspend or close the appropriate session. Completion callbacks match utterance tokens to prevent an old callback ending newer speech.
- Silent reading requires the foreground visible text/PDF/Listen Read page. An artwork-only Listen page does not count silent reading. Idle timeout is adjustable, with an optional explicit timer. Listening and silent reading cannot overlap in combined totals.
- Stable session IDs, document/revision identities, source locations/pages and measured spans are persisted at checkpoints. Recovery closes an interrupted session at its last checkpoint. Dates split at local midnight using the recorded timezone.
- Explicit corrections and exclusions preserve original measured spans. Repeated writes/restore deduplicate IDs and retain newer corrections. Backups include the ledger and tracking settings; older replace backups without a ledger clear newer records instead of retaining unrelated history.
- Opening a book or crossing 90% no longer marks it finished. Completion follows actual final audio completion or explicit Mark finished.

## Interface

Overview restores the three requested cards: Streaks with current/best counts and seven day indicators; Weekly App Activity with its eight-week swipe/navigation chart; and Reading Activity with a measured reading/listening ring and Month/Year heatmap. Goals, timer policy, daily totals and session editing live in History. Books shows start/finish dates, progress, durations, sessions, Continue, explicit timer and Mark finished. History supports manual entries, correction, exclusion and restore-original. Home, goals, monthly book times and widgets consume the same measured/legacy data.

## Verification

Five focused JVM clock cases pass, covering monotonic active time, pause/buffering, exclusive ownership, idle limits, local-date splitting and immutable corrections. Four phone persistence/media cases pass, including session correction and repeated restore, actual event timestamps across preparation/audio/buffering/explicit reading, cleanup protection/revalidation, and sentence audio plus generic-file ZIP restoration. The Insights UI test passes Overview/Books/History navigation, manual correction and Continue. System TTS and synthetic neural startup/cancel tests pass alongside the redesigned Listen controls.

Evidence logs are ignored local artifacts: `.checks/listen-redesign-final-build.txt` and `.checks/listen-redesign-accepted-phone.txt`. The latter passes all 15 phone checks across Listen, glass navigation, Insights, sessions/media, native PDF, supplied slide fixture and playback startup. Private source fixtures are not bundled in the app.

The restored Overview passes its updated navigation/editing test and compact 320 dp / 1.5x text-size graphics/navigation check. Eighteen cases passed `.checks/insights-graphics-final-phone.txt`; the nineteenth initially used an ambiguous text selector and passes after a test-only correction in `.checks/insights-compact-final-phone.txt`. App code was unchanged. Normal and large-text screenshots were inspected.

## Remaining limits

Real-model human listening/export remains paused. Long real-engine interruptions, process-kill/timezone/rollover phone acceptance and broad widget acceptance remain. Per-location new-content versus rereading classification, optional CSV export and large-ledger performance work are unfinished. Neither an open page nor an explicit timer proves attention.

Release publication remains held, and broader Office fidelity remains excluded.

The final creative pass retains the three Overview cards and adds a warm animated flame, floating gold trophy, tonal card backgrounds, gradient bars and a layered measured-mode ring. The final five-case UI suite passes, including frame comparisons with normal/reduced motion; evidence: `.checks/insights-creative-final-phone.txt`. Release building was explicitly left to the user.
