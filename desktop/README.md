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

**Study → AI handoff** prepares editable prompts from a passage or your context. Choose a task and assistant, preview/copy the prompt, then explicitly open the provider's website. Paste and send there. Drafts survive in-app navigation. Replies are not automatically imported; native AI generation and quiz/reply import remain parity work.

Import TXT/MD/LOG (UTF-8 or BOM-marked UTF-16), EPUB, PDF, DOCX or PPTX up to 32 MB using **Import book**. EPUB follows spine order; PDF provides extracted text in page order; PPTX uses the presentation's slide relationships and order. The library retains the original file alongside extracted text in Rust-owned SQLite. Older text-only imports can attach their source by reimporting the same file without losing notes or progress. Image-only PDFs open in original view; OCR and speech for scanned text remain unavailable. Legacy PPT work remains paused at the user's earlier explicit request. Original files are read, never modified. **Add text** creates a library item from pasted or typed text through the same import worker.

The reader offers **Single page**, **Two pages** and **Scroll**, with selectable text and passage-linked note, vocabulary and flashcard actions. **Original document** renders actual PDF pages with a selectable text layer and zoom. Word and EPUB views rebuild source paragraphs, tables and embedded raster images; PowerPoint uses slide order, text and image positions. These source-based views do not reproduce every publisher font, Word pagination, PowerPoint theme/group transform or EPUB stylesheet. Speaker notes, OCR and article scraping remain deferred.

**Fullscreen** or **F11** gives the book the entire native window, hiding the sidebar, player and notebook; reader controls fade away and reappear with pointer movement or keyboard input. **Escape** exits. Page arrows, **PgUp/PgDn**, **Home/End**, horizontal trackpad motion, **Shift + mouse wheel**, **Alt-drag** and page-edge drags turn pages or two-page spreads. Touch swipes use the same page-turn path. Ordinary mouse dragging selects text; **Shift + arrows** extends selection and **Ctrl/Cmd + C** copies normally. Page and app transitions respect reduced-motion preferences.

Click a passage to save your position. Add an anchored note or bookmark in the reader. Positions, favorites, notes and preferences live in Rust-owned SQLite under Tauri's platform application-data directory (`com.vern.reader.desktop`; Windows normally `%APPDATA%`). The desktop database is independent of Android's schema.

Use **Book details & collection** on a shelf card to edit its title/author, create or assign a collection, or remove the book after confirmation. Removal also deletes its linked annotations and flashcards, while retaining the original file. Library search includes extracted text and offers a link to the first matching passage. Shelf filters support collections/favorites and sorting by recent reading, title or author.

**Notes & bookmarks** gathers saved items across the library. Filter by book/type, search, edit or delete items, and return to the exact source passage. Bookmarks in the reader toggle at the current passage.

**Study Hub** supports manual flashcards, named decks, search, editing/deletion, and due-card review with answer reveal and Again/Hard/Good/Easy ratings. **Create flashcard from passage** in the notebook links a card to its source. Rust persists review state with Android's `SpacedRepetitionScheduler` rules; edits retain the review schedule. Quizzes and reviewed card/quiz reply imports are now wired; direct AI generation, study export and richer study content remain. Schema v6 upgrades existing desktop libraries with original-source storage, catalogue provenance, quizzes and personal notes/notebooks; this is not Android backup interoperability.

**Listening queue** holds books added from shelf cards. Reorder/remove entries and use **Play queue**; when narration finishes a book, the next queued item starts from its saved position. Completed queue entries are removed. The queue page also offers a 15/30/45/60-minute **Sleep timer**, which pauses narration across the app. A source stop is scheduled through Web Audio, while generation checks prevent stale or expired work from advancing playback. Playback prefetches one future passage; gapless playback and latency gains have not been verified.

**Reading tools** stores vocabulary with your own definitions, optional source links and flashcard creation. Reader notebook actions capture the current passage as the vocabulary source. Online dictionary/translation lookup is still unported. Pronunciation rules match literal words/phrases without case sensitivity, prioritize longer overlapping phrases, and affect only native speech. Empty replacements skip a phrase. Changing rules stops playback so previously prepared audio is discarded.

In **Reading preferences**, **Save desktop backup** exports supported desktop books/data to JSON. **Restore desktop backup** validates the file and shows counts before a transactional merge. Existing items retain their content/progress/review schedules. Vocabulary, pronunciation rules and queued books merge as library data; replacing reader/voice preferences is optional. Backups have a 64 MB/20,000-item-per-top-level-section limit; voice models and original files are excluded. Android JSON/ZIP migration remains unported and Android backups are rejected explicitly. The same panel exports book text or anchored notes/bookmarks as Markdown. **Export passage audio (WAV)** in the reader synthesizes the current passage with the selected installed voice/rate/rules; whole-book audio export remains future work.

Open **Voice Studio** and download Lessac (**Lite / Piper**) and Heart (**Studio / Kokoro**). Downloads run at most two at a time, support cancellation, validate native model loading and keep archive licences. Interrupted staging restarts safely. Model files are hash-checked before a voice is loaded for synthesis. Speech highlights chunks, not individual words. The desktop does not yet port Android's pitch control or Kokoro DSP tuning.

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

Native download/inference/persistence probe:

```powershell
cargo run --manifest-path src-tauri/Cargo.toml --example verify-native -- .artifacts/native --download
# Disconnect networking, then run again against installed assets:
cargo run --manifest-path src-tauri/Cargo.toml --example verify-native -- .artifacts/native
```

The probe uses a scratch database and writes a WAV for each family. It checks nonempty finite PCM and cancellation. This does not replace hearing playback in the Tauri window or testing its native file chooser. Actual results and blockers: [progress](../progress.md). Browser tests and frontend mocks are not native acceptance.

Installers are generated under `src-tauri/target/release/bundle/`. The GitHub Actions matrix is preparation for Windows/Linux/macOS x64/arm64, not a claim these have all passed. Signing, notarization, model licence audit, installers on clean machines and signed updater feeds remain release gates. See [architecture](../docs/desktop-architecture.md), [feature inventory](../docs/desktop-feature-parity.md) and [editor setup](../docs/desktop-development.md).

Study Hub now brings cards, quizzes, vocabulary, bookmarks and book notes together. Paste card/quiz replies for review before saving; quiz practice keeps native scores and explanations. Quizzes/results are included in desktop JSON backups. Local study generation and direct AI provider APIs remain in the parity ledger.

Notes includes personal text/checklist notes, notebook labels, pinning/colour, search/sorting/grid/list and recoverable Trash, beside anchored book notes/bookmarks. Personal notes and notebook labels, including Trash, are backed up natively. Media, reminders and revisions remain in the parity ledger.
