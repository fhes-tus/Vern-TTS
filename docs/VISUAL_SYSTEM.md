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

The Home feature label uses titleSmall; its book title is the focal point. The compact supporting reading card uses a quiet titleSmall label, a titleLarge metric and bodySmall explanation. Its four pages use distinct graphics: actual daily progress, active-day tiles, a goal clock and real weekly reading bars. Pages enter from the right and exit left; reduced motion disables the transition and automatic rotation. All pages reserve their measured height at the current font scale. Home sections use 12 dp gaps; Recent books keeps its heading and entries together with a 4 dp gap.

Study prioritizes the due review action, saved decks, compact material counts and creation tools. The six material actions use paired compact rows with subtle individual backgrounds. A scheduled-queue percentage must not be presented as review-session completion. Empty and populated queues have explicit descriptions.

Added grids use 18 dp side gutters and 12 dp gaps; shelf covers share Classics geometry (144 dp stage, 80 by 120 dp artwork) and cover motion. The full-width cover background joins a compact metadata base without an upper outer rim. Every tile reserves two title lines, one format line and the progress slot, keeping card heights consistent regardless of title length, missing format or reading progress while respecting font scaling. Cards have zero elevation and use the same surface and cover-stage colours as Classics. Details, overflow, selection, progress and drag reordering remain available. Imported-book Details previews use the shared cover motion. Glass navigation strengthens individual tab backgrounds while preserving the outer glass capsule.

Listen groups bookmark, fullscreen and text size beside the reading position. Fullscreen replaces collapse, and text sizing uses the app slider with accessible range controls. Extracted-reader tools leave composition while Listen is active. Customization offers Pages or Vertical scroll for extracted text; both use the existing native selectable prose, table and illustration renderer. Vertical page jumps also work for pages without text. The native Original PDF overflow exposes PDF settings > Swipe / Scroll with its own persisted preference. Horizontal page turning yields to selection, multi-touch and zoomed panning; vertical scrolling remains native AndroidX behavior. The Compose PDF fallback also retains the existing page-turn effects.

Reading Activity shows current-week and selected-month/year rings side by side at ordinary widths and text sizes, stacking at narrow widths or large text. The calendar date/view selection drives the selected-period ring; the week summary remains independent.

This refinement is a debug-preview change. The previously verified release pack remains an immutable record of its own source/build; new release artifacts need a new verification pass after design acceptance.

Checked on 16 October 2026: 485 JVM tests passed and eight focused Samsung device tests passed, covering Home goal controls, real weekly chart data, stable rotating-card bounds, 320 dp layouts at 1.5× font scale, Added filters/search, Study actions in dark/AMOLED, and synchronized tab swiping. The finished debug preview was installed in place and Home was inspected on the Samsung. These checks do not constitute an exhaustive visual review of every screen or device size.
