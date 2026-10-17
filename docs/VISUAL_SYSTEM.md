# Vern interface agreement

The interface uses the selected Vern UI font with one shared hierarchy across theme packs. `ui/VeritasFonts.kt` defines the sizes, line heights, weights and tracking. Screens use named Material typography roles rather than overriding those values individually.

| Purpose | Role | Size / line height | Weight |
| --- | --- | --- | --- |
| Screen or sheet title | titleLarge | 22 / 28 sp | 600 |
| Main feature heading | headlineSmall | 22 / 29 sp | 600 |
| Section or list-book title | titleMedium | 17 / 24 sp | 500 |
| Compact card title | titleSmall | 15 / 21 sp | 500 |
| Body copy | bodyMedium | 14 / 21 sp | 400 |
| Longer explanatory copy | bodyLarge | 16 / 24 sp | 400 |
| Secondary metadata | bodySmall | 12 / 18 sp | 400 |
| Buttons and primary labels | labelLarge | 14 / 20 sp | 500 |
| Compact labels | labelMedium | 12 / 16 sp | 500 |
| Small chart/category labels | labelSmall | 11 / 16 sp | 500 |
| Prominent metrics | displaySmall | 28 / 36 sp | 600 |

The wordmark is a deliberate branding exception. Reader text, native document content and authored note formatting retain their own user controls and semantic emphasis. Selected states, highlighted spans and accessibility emphasis can retain purposeful weight changes. System font scaling remains enabled.

Theme packs retain their corner shapes and materials; they do not rescale or reweight headings. Light palettes retain their colour identities. Dark surfaces use a restrained neutral ladder with a small tint from the active accent, while readable text stays neutral. In AMOLED, the page is black and cards use the corresponding ordinary-dark page tone. High-contrast palettes retain their dedicated colours. Active controls carry stronger accents.

Home groups a time-aware named greeting, the current book, reading activity and recent books. Library groups filters with one result/sort row; book rows show reading status and estimated time remaining, with fuller metadata in Details. Details and overflow remain separate accessible actions. The floating navigation and playback layouts remain.

The Home feature label uses titleSmall; its book title is the focal point. The supporting reading card uses a quiet titleSmall label, a titleLarge metric and bodySmall explanation. Its weekly chart uses actual Monday–Sunday reading durations and weekday labels rather than repeating the headline count or suggesting invented growth. All rotating pages reserve their measured height at the current font scale. Home sections use 12 dp gaps; Recent books keeps its heading and entries together with a 4 dp gap. Decorative page artwork is omitted from this supporting statistic card.

Study prioritizes the due review action, compact material counts, saved decks and creation tools. A scheduled-queue percentage must not be presented as review-session completion. Empty and populated queues have explicit descriptions.

This refinement is a debug-preview change. The previously verified release pack remains an immutable record of its own source/build; new release artifacts need a new verification pass after design acceptance.

Checked on 16 October 2026: 485 JVM tests passed and eight focused Samsung device tests passed, covering Home goal controls, real weekly chart data, stable rotating-card bounds, 320 dp layouts at 1.5× font scale, Added filters/search, Study actions in dark/AMOLED, and synchronized tab swiping. The finished debug preview was installed in place and Home was inspected on the Samsung. These checks do not constitute an exhaustive visual review of every screen or device size.
