# Vern desktop — first milestone

A separate Tauri 2 application using React/TypeScript and Rust. The library, reader, Voice Studio and notebook reuse Android Vern's icon, fonts and warm-paper visual language. Android and the old `veritas-pc` experiment are preserved.

## Prerequisites

Use [Tauri's current prerequisites](https://v2.tauri.app/start/prerequisites/). Check installations before installing:

```powershell
git --version
node --version
npm --version
rustc --version
cargo --version
rustup show
```

- [Git](https://git-scm.com/downloads/win)
- [Node LTS and npm](https://nodejs.org/en/download)
- Windows: [Microsoft C++ Build Tools](https://visualstudio.microsoft.com/visual-cpp-build-tools/), **Desktop development with C++**, MSVC and Windows SDK. The installer needs administrator consent. [WebView2 Evergreen Runtime](https://developer.microsoft.com/en-us/microsoft-edge/webview2/) is also required.
- [Rust/rustup](https://rustup.rs/), stable MSVC toolchain on Windows: `rustup default stable-msvc`.
- macOS: Xcode Command Line Tools. Linux: WebKitGTK 4.1 and the packages listed in Tauri's distro-specific prerequisites.

The speech binding downloads a matching prebuilt static library while building. No Python, Node runtime or separate speech installation is needed by installed-app users. A compiler and SDK are needed by developers. CMake is not needed for the selected prebuilt sherpa archive; if the TLS/native dependency build reports a CMake requirement, install it through the C++ workload and record why.

## Run

```powershell
cd desktop
npm ci
npm run tauri dev
```

This workspace also has a local Rust installation under ignored `.tools/`. `./scripts/native.ps1` adds it to the current process environment and launches Tauri; it does not modify the global PATH. Build Tools and the Windows SDK are now installed on this development host.

`npm run dev` provides a **read-only browser preview**. It has no alternate database or simulated speech.

The desktop uses the phone's four main destinations: **Home · Library · Study · Notes**. Library contains **Added** and **Classics**. Study contains flashcards, vocabulary/pronunciation and **AI handoff**. Listening queue and Voice Studio are reached from Home, the player or Settings. The header gear opens **Settings**; the reading toolbar has its own **Reader settings** button.

**Library → Classics** shows 36 direct Project Gutenberg titles with Android cover art, search, genre filters and cover/list layouts. Add/cancel/retry/read actions are connected to Rust downloads, the import worker and SQLite. Original bytes retain the Gutenberg licence. Native download acceptance and observed network errors are recorded in progress.md; catalogue rendering alone is not proof that a download succeeded. Source access/borrowing-only entries and alternate editions still need desktop equivalents.

**Settings → Theme colour** offers Sage, Midnight, Dracula, GitHub, B/W Gradient and Neon, with light/dark/system appearance, accessibility presets and a custom accent. Native settings persist these choices along with fonts, size, page layout/view and reduced motion. Fixed-mode palettes explain their supported mode. Original document pages retain source colours.

**Study → AI handoff** prepares editable prompts from a passage or your context. Choose a task and assistant, preview/copy the prompt, then explicitly open the provider's website. Paste and send there. Drafts survive in-app navigation. Paste card/quiz replies into Study Hub for review before saving; replies are not read automatically. Native AI generation remains parity work.

Import TXT/MD/LOG (UTF-8 or BOM-marked UTF-16), EPUB, PDF, DOCX or PPTX up to 32 MB using **Import book**. EPUB follows spine order; PDF provides extracted text in page order; PPTX uses the presentation's slide relationships and order. The library retains the original file alongside extracted text in Rust-owned SQLite. Older text-only imports can attach their source by reimporting the same file without losing notes or progress. Image-only PDFs open in original view; OCR and speech for scanned text remain unavailable. Legacy PPT work remains paused at the user's earlier explicit request. Original files are read, never modified. **Add text** creates a library item from pasted or typed text through the same import worker.

The reader offers **Single page**, **Two pages** and **Scroll**, with selectable text and passage-linked note, vocabulary and flashcard actions. **Original document** renders actual PDF pages with a selectable text layer and zoom. Word and EPUB views rebuild source paragraphs, tables and embedded raster images; PowerPoint uses slide order, text and image positions. These source-based views do not reproduce every publisher font, Word pagination, PowerPoint theme/group transform or EPUB stylesheet. Speaker notes, OCR and article scraping remain deferred.

**Fullscreen** or **F11** gives the book the entire native window, hiding the sidebar, player and notebook; reader controls fade away and reappear with pointer movement or keyboard input. **Escape** exits. Page arrows, **PgUp/PgDn**, **Home/End**, horizontal trackpad motion, **Shift + mouse wheel**, **Alt-drag** and page-edge drags turn pages or two-page spreads. Touch swipes use the same page-turn path. Ordinary mouse dragging selects text; **Shift + arrows** extends selection and **Ctrl/Cmd + C** copies normally. Page and app transitions respect reduced-motion preferences.

Click a passage to save your position. Add an anchored note or bookmark in the reader. Positions, favorites, notes and preferences live in Rust-owned SQLite under Tauri's platform application-data directory (`com.vern.reader.desktop`; Windows normally `%APPDATA%`). The desktop database is independent of Android's schema.

Use **Book details & collection** on a shelf card to edit its title/author, create or assign a collection, or remove the book after confirmation. Removal also deletes its linked annotations and flashcards, while retaining the original file. Library search includes extracted text and offers a link to the first matching passage. Shelf filters support collections/favorites and sorting by recent reading, title or author.

**Notes & bookmarks** gathers saved items across the library. Filter by book/type, search, edit or delete items, and return to the exact source passage. Bookmarks in the reader toggle at the current passage.

**Study Hub** supports manual flashcards, named decks, search, editing/deletion, and due-card review with answer reveal and Again/Hard/Good/Easy ratings. **Create flashcard from passage** in the notebook links a card to its source. Rust persists review state with Android's `SpacedRepetitionScheduler` rules; edits retain the review schedule. Imported Android cards can retain a whole-book link without inventing a passage. Quizzes and reviewed card/quiz reply imports are wired; direct AI generation, study export and richer study content remain. The current database is schema 15, adding recent-book history to the earlier library, study, reading activity, list, note and retained Android backup tables.

**Library > History** keeps the 40 most recently opened or played books, one entry per book. Open resumes the current saved reading position. Removed books remain listed with Open disabled. Confirmed remove/clear affects only this list; books and measured reading sessions remain.

**Listening queue** holds books added from shelf cards. Reorder/remove entries and use **Play queue**; when narration finishes a book, the next queued item starts from its saved position. Completed queue entries are removed. The queue page also offers a 15/30/45/60-minute **Sleep timer**, which pauses narration across the app. A source stop is scheduled through Web Audio, while generation checks prevent stale or expired work from advancing playback. Playback prefetches one future passage; gapless playback and latency gains have not been verified.

**Reading tools** stores vocabulary with reviewed definitions, optional source links and flashcard creation. Word tools captures selected text; reader notebook actions capture the current passage. Wikipedia, Wiktionary and Google Translate open through a bounded native handoff only when you choose an action; the displayed text is sent to that site. Choose translation languages in Google Translate, then review and paste any meaning you want to save. Offline dictionary packs and automatic definition retrieval remain unported. Pronunciation rules match literal words/phrases without case sensitivity, prioritize longer overlapping phrases, and affect only native speech. Empty replacements skip a phrase. Changing rules stops playback so previously prepared audio is discarded.

In **Settings → Sync & backup center**, **Save desktop backup** exports a ZIP archive with metadata schema `vern.desktop.backup.v5`, including library/study records, original files/page positions, covers, reading lists, recent books, note attachments/history, highlights and retained Android backups. Earlier desktop JSON and archive versions remain readable. **Restore backup** validates desktop or Android JSON/ZIP and shows counts before a transactional merge. Existing items retain their content/progress/review schedules. Vocabulary, pronunciation rules and queued books merge as library data; replacing reader/voice preferences is optional. Archives are bounded to 2 GiB, original entries to 32 MiB, and metadata to 64 MiB with 20,000 entries per top-level collection. Voice models are excluded.

Android migration converts supported readings/positions/originals, annotations/highlights, cards/schedules, playable quizzes/best scores, notes/notebooks/revisions, supported image/audio attachments, ordered lists, recent books, vocabulary and literal speech rules. Supported theme, reader, accessibility, note-default and assistant preferences transfer through the explicit apply-preferences choice. Index reconstruction runs in a worker; Rust validates and stores the result. Reading sessions retain recorded spans/timezones, exact measured durations, day totals, corrections, exclusions and zero-time records. Historical Android file revisions cannot prove current sentence anchors, so imported sessions keep those originals and open the book at its current position. Native history and CSV use explicit unknown positions. Android voice preferences become explicitly unavailable until a desktop voice is selected, with no engine substitution. The original metadata and included files are retained for re-export. Legacy aggregate activity, AI templates/history, reminders, richer media, pattern rules, covers and remaining settings still need complete desktop conversion; the preview records relevant limits. This is partial interoperability verified with fixtures and mocked-IPC browser flows, not an actual-device or installed-dialog acceptance claim. Retained backups can be exported, then deleted with confirmation; imported books, notes, recent books and converted session records are separate.

The same panel exports book text or anchored notes/bookmarks as Markdown. **Export passage audio (WAV)** in the reader synthesizes the current passage with the selected installed voice/rate/rules; whole-book audio export remains future work.

Open **Voice Studio** to choose an installed Microsoft voice on Windows, one of 28 English **Studio / Kokoro** speakers, or one of 12 **Lite / Piper** voices. Windows voices use the PC's installed speech languages through the native Windows API. Studio speakers share the existing Kokoro v1.0 download; Heart remains speaker 3. Preview custom text, search names/accents and use **Manage Windows voices** / **Refresh voices** after adding a Windows speech language. Windows voices are explicitly selected, with no silent engine substitution. Narrator-only and online voices are not necessarily exposed by Windows to other apps.

Neural downloads run at most two packages at a time, support cancellation, validate native model loading and retain archive licences. Interrupted staging restarts safely. Model files are hash-checked before loading. Speech highlights passages; the desktop does not yet port Android's pitch control or Kokoro DSP tuning. Windows audio uses the same PCM player, sleep timer, selection-reading and WAV-export path as neural audio.

Space plays/pauses; left/right arrows turn reader pages; Ctrl/Cmd F focuses search. Native keyboard text editing is preserved. Use Preferences for light/dark, typeface and size. Rate changes stop current speech; play resumes with the new rate.

## Checks and packaging

```powershell
npm run build
npm test
npm run lint
npm run format:check
npx playwright install chromium
npm run test:e2e
cargo fmt --manifest-path src-tauri/Cargo.toml --all -- --check
cargo test --manifest-path src-tauri/Cargo.toml
cargo clippy --manifest-path src-tauri/Cargo.toml --all-targets -- -D warnings
npm run tauri build
```

On this Windows host the downloaded Chromium could not spawn and Edge timed out; the existing Google Chrome passed the interface checks. To use it: `$env:PLAYWRIGHT_CHANNEL='chrome'; npm run test:e2e`. CI uses Playwright Chromium. For the local Rust toolchain, run `./scripts/native.ps1 tauri build` instead of the plain npm command, or prefix Cargo commands with `./scripts/native.ps1 cargo` (for example `./scripts/native.ps1 cargo test --manifest-path src-tauri/Cargo.toml`). PowerShell consumes a bare `--` passed to a script; preserve separators using an explicit argument array, for example `./scripts/native.ps1 -TaskArgs @('cargo','clippy','--manifest-path','src-tauri/Cargo.toml','--all-targets','--','-D','warnings')`.

For installed-build validation, set `VERN_PROFILE_DIR` in the launching process to an absolute, dedicated directory such as `C:\VernValidation\profile`. Both release and debug builds then keep Rust-owned SQLite/models and Tauri's WebView cache in that profile. Invalid or relative paths stop startup instead of opening the normal library. With no override, the normal application-data location is unchanged. Keep each profile private to one running Vern instance and never point this option at a general documents/downloads directory. The older debug-only `VERN_DEV_DATA_DIR` still overrides native data for existing development workflows; leave it unset when validating an installed release.

Native download/inference/persistence probe:

```powershell
cargo run --manifest-path src-tauri/Cargo.toml --example verify-native -- .artifacts/native --download
# Disconnect networking, then run again against installed assets:
cargo run --manifest-path src-tauri/Cargo.toml --example verify-native -- .artifacts/native
```

The probe uses a scratch database and writes a WAV for each family. It checks nonempty finite PCM and cancellation. This does not replace hearing playback in the Tauri window or testing its native file chooser. Actual results and blockers: [progress](../progress.md). Browser tests and frontend mocks are not native acceptance.

Installers are generated under `src-tauri/target/release/bundle/`. The GitHub Actions matrix is preparation for Windows/Linux/macOS x64/arm64, not a claim these have all passed. Signing, notarization, model licence audit, installers on clean machines and signed updater feeds remain release gates. See [architecture](../docs/desktop-architecture.md), [feature inventory](../docs/desktop-feature-parity.md) and [editor setup](../docs/desktop-development.md).

Keep each MSI's original generated filename when creating a share pack, for example `Vern_0.1.0_x64_en-US.msi`. Windows Installer may use the registered original name when locating a repair source: this host's repair failed after the package was renamed, then succeeded with identical bytes under its original name. Retain the matching installer and checksum for repair. Record the source-input manifest for each build; an older installer's successful launch does not validate source changes made afterward.

Windows packages are unsigned by default for local validation. A signed build requires an issued code-signing certificate with its private key installed in the current user's `Personal` certificate store, plus SignTool from the Windows SDK and the timestamp URL supplied by the certificate provider. Set the certificate thumbprint and timestamp URL in the current PowerShell session, then run:

```powershell
$env:VERN_SIGNING_THUMBPRINT = 'CERTIFICATE_THUMBPRINT'
$env:VERN_TIMESTAMP_URL = 'TIMESTAMP_URL_FROM_PROVIDER'
npm run tauri:build:windows-signed
```

The opt-in configuration calls `signtool.exe` and fails the build if the certificate, private key, Code Signing usage, timestamp service or signature verification is missing or invalid. It does not store the certificate or private key in the repository. Check the generated MSI with `Get-AuthenticodeSignature` before sharing it. A signed installer has not been produced until these steps run with a real certificate.

Study Hub now brings cards, quizzes, vocabulary, bookmarks and book notes together. Paste card/quiz replies for review before saving; quiz practice keeps native scores and explanations. Quizzes/results are included in desktop JSON backups. Local study generation and direct AI provider APIs remain in the parity ledger.

Notes includes personal text/checklist notes, notebook labels, pinning/colour, search/sorting/grid/list and recoverable Trash, beside anchored book notes/bookmarks. Native storage retains up to 30 previous note versions and bounded JPEG/PNG/WAV/MP3/OGG attachments (8 MiB each, 64 MiB per note). Full desktop archives include this history and media. Note reminders remain in the parity ledger.
