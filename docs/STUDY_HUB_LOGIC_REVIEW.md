# Study Hub logic review — 9 October 2026

The user requested a logic review before a visual redesign. Existing source changes from the wider update are retained. This review covers the Hub's flashcards, quizzes, vocabulary, bookmarks, booknotes, recent reading history and AI generation.

## Corrections

- Quiz imports resolve whole answer text before option labels. `Carbon` no longer resolves to option C. Numbered choices keep their labels, JSON choices are trimmed, actual object labels are respected, and unmatched answers, duplicate choices and blank choices are rejected.
- Question totals derive from the question list, including after copying a quiz or loading inconsistent old metadata. Cards and metrics share the same legacy score conversion. Saving another attempt compares converted counts, so an old percentage cannot incorrectly defeat a better new result. Legacy percentage detection retains the previous UI convention: only scores exceeding the question count are interpreted as percentages.
- Invalid saved quizzes remain available for inspection/deletion. The player explains what needs repair instead of accepting an unanswerable quiz or silently closing an empty one. Empty quizzes do not count as mastered.
- Flashcard reads, writes, migrations and database/preference fallback share the repository lock. Reading while the last deck is deleted cannot copy the old preference backup into the newly empty database. One malformed fallback record no longer hides every other card; pasted legacy cards can omit a book ID.
- Flashcard and quiz mutations are serialized through persistence and UI publication. Rapid ratings, renames, deletes and scores cannot publish an older snapshot after a newer one. Startup reloads study state through the same coordinator. Empty imported cards are filtered before persistence; a genuine persistence failure produces a visible message.
- Book loading is part of the tracked AI request. Single-request and cancellation state run on the main dispatcher; generated study material persists within that request rather than a detached job. Callback-free Hub requests display failures. Cancellation cannot undo a database operation that already committed.
- The review queue contains scheduled flashcards only. Bookmarks and vocabulary do not have review schedules and therefore do not inflate its due count. The percentage means **not due**, rather than claiming daily completion. The queue refreshes with time while the Hub is open; Start Review opens the due cards.
- Bookmark/booknote sentence previews refresh when the stored text changes. Missing books no longer permanently cache an empty preview if they subsequently become available.
- Reading-history load/add/remove/clear operations and writes share the repository lock, protecting concurrent durable updates. This is recent-book history, not the future detailed reading-session ledger.

## Retained behavior and limits

- Vocabulary insertion/removal already uses atomic note mutations. The previous lookup repair still supplies definitions, source and context.
- Flashcard recall intervals and rating names remain unchanged. Reviewing all cards in a deck is still supported independently of the due queue.
- Scores represent the best saved result, not every attempt. There is no durable daily study-session ledger; the UI must not imply one.
- Deleted-book annotations and document notes follow the existing repository retention policy. This pass does not change that policy.
- Full Reading Insights redesign and its session ledger remain future work. The final Home/Added polish is recorded separately in `HOME_ADDED_POLISH_REVIEW.md`.

## Hub layout after the logic review

- The Hub opens to a dedicated Overview, with section navigation above its content. Switching sections returns the list to the top, and the selected section survives activity recreation.
- Overview shows the scheduled review queue, six Material icon collection cards with saved counts, real flashcard decks, paste actions and AI creation. Section pages show their own content without repeating the dashboard above every list.
- Removed the fallback that called ordinary library books "Active Decks" and counted bookmarks/vocabulary as flashcards.
- AI creation explicitly asks which book to use. It no longer silently selects the first book; missing configuration opens AI settings. The generation banner includes Cancel.
- Collection cards use the existing theme's solid panels and shapes. Floating glass controls retain the previously corrected implementation; this pass adds no glass to content cards.

## Validation

The first focused JVM run found a regression where numbered flashcard definitions were classified as numeric quiz choices; numeric quiz detection now requires an answer marker. Final build and focused tests passed: 35 JVM cases (15 Study Hub, 12 AI result parser, 4 AI study service and 4 vocabulary lookup), plus 8 tests on the connected ARM64 phone. Five phone cases cover the Hub's malformed fallback recovery, concurrent deletion/read, rapid ratings/renames/scores, collection navigation/counts and explicit AI book choice; three cover Home/Added.

The initial rapid-mutation device test timed out in `ActivityScenario.launch` before reaching assertions. It now creates the view model on the main thread and clears its ViewModelStore in `finally`; all its persistence assertions pass. The actual app also cold-started successfully (about 1.8–2.2 seconds in the final manual checks). Overview was visually inspected in the actual app. Tests/installations use `com.veritas.reader.checks`; normal and preview apps are preserved. Ignored logs and screenshots are in `.checks/study-hub-review`.

Additional manual acceptance can cover imported quiz formats, bookmark text edits, live AI cancellation/retry with an API key, and review deadlines. The focused persistence/navigation results above do not claim those external or time-dependent flows were all exercised manually.
