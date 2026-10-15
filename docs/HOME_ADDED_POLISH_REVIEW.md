# Home and Added polish — 9 October 2026

The user requested a final polish of Home and My Library → Added after the Study Hub logic review. The full Reading Insights redesign remains future work.

## Changes

- Home's Continue reading progress uses the same saved-position calculation as the library, including the last sentence. Recent books excludes the continue-reading book and keeps the next two most recently updated books.
- A new reader without activity or a goal can choose a daily goal directly on Home. Readers with activity retain the existing recorded-time card. Goal selection, editing and disabling share one dialog, and the remaining-time caption handles one minute correctly.
- Added shows the saved item count, or the filtered count against the total, with a Reset action for combined search/status/format/collection/list filters. Selection retains its separate batch-action summary.
- Changing search, filters or sorting brings the list to the top. An empty search result names the query and retains Clear filters.
- Search uses a minimum height rather than a fixed text box height, with a larger clear action. Existing cover placeholders, list/grid modes, weekly book preview and floating-control padding are retained.
- Added tiles place the source label on its own line so a long author/source does not hide estimated time and progress. The results row avoids repeating the existing sort control.

## Validation

Combined app/test assembly passed, along with 35 focused JVM tests and all 8 phone tests. `HomeAndAddedUiTest` contributes 3 cases: goal changes and disabling, editing/clearing search at 1.5× text size in a 320 dp layout, and recovery from combined filters. Device checks run in `com.veritas.reader.checks`; the user's normal and preview applications are preserved.

Actual Home was inspected with a cover and a generated cover placeholder. Recent books no longer duplicates Continue reading. Setting a 15-minute goal survived force-stop/relaunch; the original no-goal setting was restored afterward. Scrolling to the bottom leaves Classics and Add clear of floating navigation. Added's actual tile layout was inspected. The final two cosmetic corrections found in that review passed app assembly and the updated isolated APK was installed successfully. Functional tests were run before these final two rendering-only corrections.

This polish does not implement a reading/listening session ledger or change how existing reading time is measured. Weekly download/network behavior, Monday rollover and every theme combination are broader acceptance checks rather than claims from these focused UI tests.
