# Changelog

All notable changes to the Veritas Reader application will be documented in this file.

---

## [Unreleased]

---

## [2.6.0] - 2026-10-05

### Added
*   **Library and Classics:** Shared My Library / Classics sections, portrait cover cards, searchable horizontal genre shelves and sorting, preserving catalog additions and their bundled covers.
*   **Live playback:** Cover-based player above navigation; tap the book to open its live position and use separate play/pause. Home's repeated row play buttons are removed; reading another book preserves the audio session.
*   **Catalog downloads:** Durable per-book progress, cancel/retry and stable catalog identity with atomic duplicate prevention. Website-only catalog entries open their source instead of importing HTML as a book.

### Fixed
*   **Non-blocking Imports and Batch Reliability:** Remove the dark full-screen import overlays from the reader host and file browser. Persist batches as sequential WorkManager jobs, continue after rejected files, deduplicate selections, keep queued readings in selected order, report actual completion/failures, and restore batch progress when the app reopens. Back dismisses the browser without cancelling extraction.
*   **Book Details:** Build an overview from an abstract or representative complete sentences across the document, with optional explicit AI summarization. Load covers off the UI thread and keep longer details scrollable.
*   **Original Documents:** Load EPUB/DOCX media on demand, parse Word blocks incrementally, decode images in the background, and improve viewport use and table scrolling. PPTX follows actual presentation order and notes relationships; supported slides preserve positioned source objects, with readable fallbacks for complex layouts.
*   **Pitch and Neural Export:** Remove whole-sentence punctuation pitch boosts, settle rapid voice-setting gestures, restore book pitch on ordinary Play while respecting pending/manual controls, and apply duration-preserving pitch adjustment to Kokoro/Piper PCM. WAV export supports selected neural voices without substituting system TTS.
*   **Library and Startup:** Use targeted SQL for secondary edits, avoid catalog reads per playback sentence, coalesce widget updates, load startup data off the UI thread, and keep deleted samples deleted. Update navigation history explicitly when screens change.
*   **Credential Storage:** Migrate AI credentials to Android Keystore encrypted storage, exclude it from backup, and align the privacy policy with Android backup behavior.
*   **Playback and Selection Regressions:** Reuse the audio-focus client so repeated Play requests cannot pause the app itself. Route cold Read from Here through normal engine initialization. Preserve native selections during highlight updates and search the actual displayed text buffer.
*   **Reader Page Loading:** Expose text after a short illustration preparation window; late uncertain illustrations use the page-end fallback. Reuse one parsed PDF, probe columns only when an illustration needs them, and remove explicit page-number prose from new extraction while hiding/silencing legacy prefixes. Blank pages now say only “No readable text on this page.”
*   **Phone File Browsing:** Show folders and indexed files while the full discovery scan continues, publish progress snapshots, cancel obsolete scans, and cache recent folder results.
*   **Outlines:** Preserve embedded bookmark labels, match contents titles to actual body pages, reconstruct fragmented contents rows, retain repeated chapters in collections, and reject prose, bullets, checklists and adjacent short numbered-list candidates. Smart Outline now includes a validated page-jump field and a Material vector title icon.
*   **Reading Symbols:** Expand clear mathematical and scientific notation, contextual Roman numerals, ISO dates, numeric ranges, Greek symbols, common Latin abbreviations, and recognized chemical formulas for English voices. Map spoken offsets back to the displayed source. LaTeX display replacements now respect whole command names.
*   **Notes Reliability:** Serialize autosaves under a stable note identity, confirm disk persistence, display save status, preserve drafts across recreation/backgrounding, and protect concurrent note/annotation mutations. Undo/Redo restores the real document including attachment and checklist changes; Share and Make a Copy use the current complete draft. Pinned notes sort first and vocabulary records stay out of general notes.
*   **Note Attachments:** Copy selected media off the UI thread using bounded buffers and publish complete files only. Full ZIP backups now include referenced app-owned note media and remap restored image, audio, video, and inline paths through the existing restore transaction.
*   **Add Menu Feedback:** Add a visible pressed fill and scale to Add Something actions.
*   **PDF Columns and Chapter Headings:** Reconstruct complete visual rows before detecting gutters; preserve headings spanning columns and read interrupted column bands in order. Verified against the reported Sherlock Holmes pages. Existing extracted copies need fresh extraction to receive the correction.
*   **Inline Illustrations:** Place supported images between prose blocks without changing sentence identities. Bound initial media preparation, keep stable image frames, and cache image/anchor data together. Late, ambiguous and rotated PDF placements retain the bottom-of-page fallback for that visit.
*   **Vocabulary Source Sentences:** Web lookup uses the selected word's sentence rather than playback's highlight. Concurrent additions/removals preserve entries and context; failed definition fetches show an unavailable message.
*   **Neural Playback Output:** Reject stale synthesis/results after interruption, handle partial/error writes, wait for rendered audio with bounded completion checks, and handle playback-counter wrap. Align transient audio-focus pauses, leading silence, and background listening accounting.
*   **WAV Export Memory:** Stream and validate WAV parts with bounded PCM memory, cancellation cleanup, and completed-file publication. Neural voices use their PCM synthesis path; mixed Android/neural casts report an explicit limitation.
*   **System Voice Punctuation:** Normalize Unicode question/exclamation marks and pause punctuation without shifting text offsets. Preserve quoted question endings in table cells. Add optional punctuation expression with adjustable strength in Narration Studio for Android voices; apply the same preparation to selection reading, preview, and system audio export. Natural question intonation remains voice-dependent.
*   **Import Status Messages:** Status toasts are shown once across app screens, replace older toasts, and stop when the app goes into the background. Opening dialogs use the actual document title; dismissing them cancels the pending load.
*   **Progressive PDF Imports:** Publish the first five readable pages before processing larger background batches. Dismiss the blocking import overlay once ready pages open, refresh the reader as later batches arrive, and finish without reopening the book or interrupting a return to the library. Preserve source-page markers, normalize page ranges, reuse the worker's document identity on restart, and keep partial readings after extraction failure without silently creating a second copy. Playback refreshes its text when more pages arrive.
*   **Import Navigation:** Older imports no longer automatically reopen a document after the reader has moved elsewhere. Finished import observers stop collecting, and simultaneous imports retain their own notifications and pending state.
*   **Extracted Text Display:** Refresh reader formatting and highlights in place while preserving Android selection spans. Pause automatic scrolling during text selection and include paper-tone changes in rendered formatting updates.
*   **Progress Persistence:** Update the document's progress row without rewriting the whole library or replacing extraction's current sentence count with a stale player count. Text repairs preserve incomplete-import state.
*   **Restore Validation and Recovery:** Validate schemas, sections, IDs, filenames, duplicate entries, and size limits before restoring. Stage document files and ZIP assets, retain previous text until success, and roll back staged output and metadata on handled failures. A durable file/preference journal and SQLite commit marker recover interrupted publication when the repository reopens.

---

## [2.5.1] - 2026-09-25

### Added
*   **Typography Overhaul & Custom Typefaces:** Introduced **Gazette** (Slab Serif), **Crisp** (Geometric Sans), and **Typewriter** (Monospace) with dynamic `TextView.typeface` runtime binding for superior reading comfort.
*   **180-Degree Upside-Down Sensor Landscape:** Configured `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` across reader views to support effortless device flipping and comfortable one-handed landscape holding.
*   **Resilient PDF Table of Contents & Outlines:** Implemented `PDNamedDestination` / `PDPage` destination resolution and `ReaderTextModelCache` sentence index mapping for instant navigation through PDF document outlines.
*   **Dismissible Hero Continue Card:** Added fluid swipe/close dismiss functionality for the home hero continue card.
*   **Official Website Integration:** Added direct navigation to the official Vern TTS website (`https://fhes-tus.github.io/Vern-TTS/`) across About dialog and User Manual.

### Changed
*   **Reading Layout Headroom:** Set animated reading headroom to 132dp when top app bar is visible to prevent text occlusion.
*   **Asset Footprint Reduction:** Trimmed legacy unused font files and streamlined asset packaging, reducing binary size.
*   **Uniform Paper Tone System:** Standardized paper tones across all readers to Sepia, Bone, Dark slate, and Default with clean Android system toasts.

### Fixed
*   **Audio Focus & Transient Pause Crashes:** Kept foreground service active during transient audio focus interruptions (calls, navigation alerts) to eliminate `ForegroundServiceStartNotAllowedException` on playback resume.
*   **Foreground Service Watchdog Timeouts:** Added resilient try-catch handling to `startForegroundNow` to prevent background execution restriction termination and `ForegroundServiceDidNotStartInTimeException`.
*   **UI Thread Text Measurement ANRs:** Switched `TextView` break strategy to `BALANCED` and hyphenation frequency to `NONE` across readers, eliminating main-thread freezing and layout hangs on dense pages.
*   **Page Preloading GC Lock Contention:** Lowered `DocumentPageImageLoader` thread priority to `THREAD_PRIORITY_BACKGROUND` and serialized adjacent page caching.
*   **Reading Progress Persistence:** Fixed state tracking to ensure sentence and page positions reliably persist across activity recreation and reader switches.
*   **Security Vulnerability Fix:** Upgraded `androidx.glance` to 1.1.1 resolving Google Play Console advisory for CVE-2024-7254.

---

## [2.5.0] - 2026-09-18

### Added
*   **Vern TTS Rebrand & Identity:** Formally established the "Vern TTS" identity across app titles, onboarding, drawer footers, and about sections, honoring the root word *Vernehmen* (German: to hear/perceive).
*   **Authentic Published Book Covers:** Bundled authentic published book cover artwork for all 35 curated classic volumes from Project Gutenberg, rendering crisp cover art in library cards, Book of the Day, hero cards, and reader page 1.
*   **Synchronous Cover Caching Pipeline:** Decodes catalog covers synchronously during download, entirely eliminating initial placeholder flashes.
*   **Intelligent Heuristic Book Synopsis:** Implemented smart narrative synopsis extraction for storage-imported documents (PDF, EPUB, TXT, DOCX), skipping Gutenberg headers, publisher licenses, copyright boilerplate, and tables of contents to capture the first substantive prose paragraph.
*   **Bidirectional 3-Way Paper Tone Sync:** Real-time synchronization of paper tone choices across Extracted Text, Actual Document, and PDF viewers via lifecycle-aware preference reloading.
*   **Reorganized PDF Playback Bar:** Restored the missing `Next` button and aligned controls in intuitive reading order: Previous → Play/Pause → Next → Rotate → Expand.
*   **Universal Hero Card Split Actions:** Cover tap on the Continue Hero Card now opens the comprehensive Book Details sheet for any document, while tapping the card body resumes reading.

### Changed
*   **Universal Drag-and-Drop Elevation & Reordering:** Elevated dragged cards with smooth floating depth (`zIndex = 100f`, `shadowElevation = 36f`, `scale = 1.06f`) and non-blocking glide animations for non-dragged items in Grid view; calibrated List view swap threshold to 90dp for stable reordering.
*   **Distinct Hold-to-Select Gesture:** Isolated hold-to-select from drag gestures so holding without movement enters selection mode without accidental reordering.
*   **Reader Tools Menu Streamlining:** Removed redundant "Add General Note" and "Add Sentence Note" items, keeping navigation fast and focused.
*   **Subtle Info Button:** Sized the (i) info button in list cards to a discreet 24dp container / 15dp icon to match grid view.

### Fixed
*   **Dynamic Rotate Icon Lag:** Replaced slow sensor requests with explicit portrait/landscape configurations in PDF and Document viewers, eliminating rotation latency.

---

## [2.4.0] - 2026-09-14

### Added
*   **Universal Classic Books Bookstore & Catalogue:** Completely redesigned into a universal digital bookstore with 36 curated public domain masterpieces spanning 7 categories (*Family & Youth*, *Mystery*, *Adventure*, *Romance*, *Life & Habits*, *Quick Reads*, and *Epic Classics*).
*   **Bookstore Shelf (2-Column Grid) vs. List View Toggle:** Added a dynamic view switcher to explore realistic cloth-bound covers on a bookshelf grid or browse detailed title rows.
*   **Interactive Book Details Sheet & Spotlight Hero:** Integrated daily spotlight hero card, quotes, reading times, and a bottom sheet previewing synopsis, 3D cover, and 1-tap library download/open.
*   **Classic Books Catalog Guided Tour Integration:** Seamlessly integrated into the in-app interactive walkthrough with spotlight cutouts, auto-scrolling viewport, and voice narration.
*   **Zero-Bloat Expected Read Time Indicators:** Added calculated reading duration pills (e.g. `~15m`, `~1h 20m`) to subtitle metadata across all Library cards (`DocumentCard`, `DocumentTileCard`, `HomeRecentBookGridItem`, `RecentImportItem`) with zero card height increase.
*   **Dual Google Play & GitHub In-App Updater:** Added source-aware update routing detecting installer origin (`com.android.vending`), prioritizing compliant Google Play store updates for Play Store installations while preserving direct in-app APK download & installation for GitHub/sideload users.
*   **Visual Table Card Formatting in Extracted Text Reader:** Added theme-adaptive table cards (`TableCardBackgroundSpan`) with rounded container borders, horizontal row dividers, and bold accent headers across dark, sepia, and light themes while preserving 100% natural TTS narration without audio delimiter artifacts.

### Changed
*   **Reader Gestures & Paging Polish:** Restored pre-refactor extracted text paging synchronicity; decoupled selection focus from pager layout to allow natural horizontal swipe dismissal.
*   **AMOLED Display Settings Placement:** Relocated AMOLED Mode toggle into the Reader Display Preferences menu alongside paper tones.
*   **Google Play Exact Alarm Compliance:** Replaced `USE_EXACT_ALARM` with standard, inexact notification scheduling in compliance with Google Play Store policy.
*   **Dynamic Bundle Build Optimization:** Configured automatic ABI split suppression during Android App Bundle creation for Google Play Console compatibility.

### Fixed
*   **Text Selection Page Snapping Bug:** Resolved Android View focus trap where selecting text on a page caused the reader to jump or snap back when swiping or jumping to another page; off-screen views now reliably drop focus and clear selection handles.
*   **Onboarding Quests Missions UI:** Restored theme-adaptive mission card layout and celebration persistence.

---

## [2.3.2] - 2026-09-08

### Added
*   **Precision Slider Controls & Micro-Nudge Buttons:** Added discrete `0.05` step quantization, haptic tick feedback, and circular `[-]` and `[+]` micro-nudge buttons to Speed and Pitch sliders across Compose Reader, Original Document View (`ActualDocumentView`), and PDF Viewer (`VeritasPdfViewerActivity`), eliminating jitter and making exact value targeting effortless.
*   **Extended 10sp Minimum Font Size:** Extended the reading font size scale down to `10sp` (10sp – 28sp) across Reader Screen, Original Document View, and Settings Hub with full configuration persistence.
*   **Interactive Homepage Visual Charts:** Transformed the Homepage "Library Source Distribution" and "Time Allocation" donut charts into responsive interactive visualizations featuring angle touch hit-testing, slice pop-out animations, tactile haptic feedback, real-time center metric focus, and full drill-down dialogs (`DonutChartDetailDialog`) with 1-tap library filtering and reading stats navigation.
*   **AI Quiz Lab Mastery Charts & Visual Analytics:** Integrated an interactive Mastery & Score Distribution Donut Chart (Mastered 100%, Proficient 70-99%, Needs Review <70%, Unplayed) along with a segmented progress composition bar into the Quiz Lab Metrics dialog for clear retention and performance tracking.

### Changed
*   **Expanded Playback Bar Spacing & Sizing:** Increased expanded playback bottom sheet height to 290dp and added generous vertical spacing (10–14dp) between slider rows across Compose Reader, Original Document View, and PDF Viewer for a clean, uncluttered layout.

### Fixed
*   **Architecture-Aware In-App Updater:** Fixed in-app updater asset selection logic on devices supporting both 32-bit and 64-bit ABIs to strictly prioritize 64-bit (`arm64-v8a`) binaries over 32-bit (`armeabi-v7a`), preventing accidental 32-bit installations on modern 64-bit devices.

---

## [2.3.1] - 2026-09-07

### Added
*   **Unrestricted Battery Optimization Dialog:** Direct Material 3 pop-up explaining background speech restrictions with 1-tap redirect to app battery usage settings (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `APP_BATTERY_USAGE`, and App Info `APPLICATION_DETAILS_SETTINGS`), ensuring uninterrupted background audio playback when the phone screen is locked.
*   **Sleek Circular Slider Across All Readers:** Modernized page navigation, speech rate, pitch, and font size sliders across Reader Screen, PDF view (`VeritasPdfViewerActivity`), Original Document view, and Voice Studio with a sleek 18dp circular thumb, 4dp track, surface border ring, and smooth haptic feedback.
*   **Automatic Veritas Voice Engine Routing:** Fully automatic engine detection for downloaded offline Veritas voice models (Piper & Kokoro), seamlessly initializing native neural synthesis directly without falling back to robotic system TTS.
*   **User Manual Deep-Link Redirections:** Comprehensive wiring for all in-app User Manual CTA actions, redirecting users directly to the Classics Catalog, Storage Manager, About dialog, Study Hub, Notes Studio, Reading Lists, and Library.

### Changed
*   **Theme-Fitting Delete and Clear Actions:** Harmonized delete and clear icons, dropdown menu items, and confirmation actions to seamlessly match the active color scheme (`onSurfaceVariant`) instead of harsh persistent red error highlights.
*   **Reader Tools Menu Streamlining:** Cleaned up Reader Tools overflow by removing redundant options and integrating the direct Document Details inspector.

### Fixed
*   **Offline Veritas Voice Fallback:** Resolved an issue where selecting downloaded Veritas voices was falling back to the device's default system TTS engine due to engine package mapping; added auto-detection and resilient buffer initialization in `PlaybackService` and `VoiceManager`.
*   **Duplicate Delete Confirmation Dialogs:** Removed redundant nested confirmation alerts in the Library Recent section and Library tab so deletion confirms cleanly with a single prompt.
*   **User Manual Dialog Stacking:** Fixed User Manual modal remaining open over target screens when activating feature shortcuts.

---

## [2.3.0] - 2026-09-07

### Added
*   **In-App Free Book Repositories & Downloader:** Full in-app browsing and automatic download interception for 5 global digital book repositories: *Project Gutenberg*, *Standard Ebooks*, *Open Library*, *ManyBooks*, and *Ocean of PDF*. Downloads (EPUB, PDF, TXT) are automatically sandboxed and imported directly into the Veritas reading library without leaving the app.
*   **Study Guide PDF Exporter:** Automatic generation and export of styled PDF study guides from flashcard sets, study notes, and document highlights.
*   **PDF Table of Contents (TOC) Extraction:** Interactive outline drawer allowing instant chapter and section navigation within PDF documents.
*   **Glance AppWidget Android 16 Redesign:** Rebuilt Flashcard, Study Dashboard, Quick Capture, and Player widgets with responsive Glance Column layouts, zero-latency touch callbacks, and multi-deck review support.

### Changed
*   **Sandboxed Web Security:** Enforced strict enterprise-grade sandbox policies across all in-app browsers (`allowFileAccess = false`, `allowContentAccess = false`, `safeBrowsingEnabled = true`, blocking non-HTTP/HTTPS schemes).
*   **Study Hub Navigation Pipeline:** Refactored home tab routing with dedicated `targetHomeTab` navigation, eliminating race conditions when jumping from AI Study Studio or home widgets directly into Study Hub.

### Fixed
*   **Library Tab Swipe Stabilization:** Eliminated circular state feedback oscillations between page state and navigation tab selections, and removed top bar height animations during horizontal swiping for smooth, glitch-free swiping.
*   **Flashcard Widget Touch Unresponsiveness:** Resolved `Null RemoteViews` errors on Android 16 / One UI 8 by eliminating AdapterView/RemoteViewsFactory dependencies, guaranteeing immediate touch responses for deck selection, card flipping, and grading.

---

## [2.2.0] - 2026-08-22

### Added
*   **Device-Wide File Discovery:** A MediaStore-backed index now finds compatible documents anywhere on shared storage in a single query, across every mounted volume including SD cards and USB OTG, with a deeper filesystem sweep behind it for files the media index does not cover.
*   **File Deletion from the Browser:** Long-press selection now offers deletion for one file or many, with a confirmation that names the files and states plainly that this removes them from phone storage rather than from Veritas alone.
*   **Delete Previews:** The confirmation shows a downsampled thumbnail and folder path for each image, since two photos can look alike and the path is often what identifies the right one.
*   **Broader Format Recognition:** Legacy PowerPoint (`.ppt`), macro-enabled Office documents (`.pptm`, `.docm`), `.xhtml`, HEIC/HEIF/AVIF photos, and a fuller plain-text set (`.log`, `.json`, `.xml`, `.yaml`, `.srt`, `.vtt`, and others) are now recognised by the browser, the system picker, and the launcher's "Open with" list.
*   **Idle Chrome Collapse:** The original-document view retires its toolbar and player bars after five seconds without a touch, and restores them on a tap.

### Changed
*   **Exact Page and Sentence Mapping:** Synchronisation between the reading position and the original-document view no longer scales reading progress across the page count. Both directions resolve through the document model, so the page shown is the page the sentence is actually on.
*   **Unified EPUB and DOCX Extraction:** Reading text for these formats is now produced by the same parsers the original view renders, so page markers and displayed pages derive from a single parse and cannot drift apart.
*   **Document Loading Split by Scope:** Parsing is performed once per document rather than repeated on every page turn, with page rendering and slide-image extraction handled separately.
*   **Rotation Preserves State:** Rotating no longer rebuilds the reader, so the current page, bar visibility, zoom, and view rotation survive the change.
*   **DOCX Table Reading:** Table cells are separated when read aloud instead of being run together into a single compound word.

### Fixed
*   **Neural Speech Engine Crash:** Stopping, seeking, or switching voice could free the speech engine while it was still generating audio, terminating the app from inside its native layer where the in-app crash reporter cannot observe it. Teardown now waits for generation to finish.
*   **Slide, Chapter and Page Highlighting:** The original-document view highlighted only the first line of every slide, chapter or page regardless of what was being spoken, and drifted onto the wrong page the further into a document playback ran.
*   **Original View Opening Page:** Switching to the original document opened on page 1 instead of the page being read.
*   **Page Swiping During Playback:** Swiping pages in the extracted-text view could hijack narration, and a page change arriving mid-scroll was discarded and never retried.
*   **Blank Pages:** Pages with no text of their own no longer move the reading position to a different page's sentence.
*   **Continue Reading From Here:** Selecting a sentence in the original view now begins from that sentence on that page, rather than restarting the document when no match was found.
*   **Sideways Pages After Rotating:** The rotate controls applied a page rotation on top of the screen rotation.
*   **Unrecoverable Full Screen:** Hiding the bars removed the only control that could bring them back on the PDF view; tapping the page now restores them.
*   **Slide Images Disappearing:** Embedded PPTX images were rebuilt on every page turn and vanished between slides.

### Removed
*   **Duplicate Document Parsing:** The separate EPUB and DOCX parsing paths inside the extractor have been retired in favour of the shared parsers, removing the possibility of the two views disagreeing.

---

## [2.1.0] - 2026-08-21

### Added
*   **Comprehensive Multi-Format Document Parsers:** Native offline parsing and text extraction for Microsoft Word (`.docx`), EPUB e-books (`.epub` 2 & 3 with chapter hierarchies), and legacy PowerPoint presentations (`.ppt`).
*   **RSVP Speed Reading Engine:** High-performance Rapid Serial Visual Presentation mode with customizable WPM (100–1000 WPM), dynamic punctuation pauses, and optimal recognition point (ORP) centering.
*   **Smart Resume Catch-Up:** Intelligent contextual re-orientation dialog summarizing preceding context when returning to a document after a hiatus.
*   **Study Guide PDF Exporter:** Instant generation of beautifully formatted PDF study guides compiling document highlights, tags, vocabulary notes, and annotations.
*   **Material You Home Screen Widgets:** Glance-powered and AppWidgetProvider widgets including Pinned Notes, Quick Audio Capture, Quick Note, Reading Streak/Progress, and Study Dashboard.
*   **Classics & Public Domain Catalog:** In-app browser for curated classic literature and direct reading list import.
*   **Voice Notes Studio:** Built-in voice note recording and dictation tools directly integrated within the notes editor.

### Changed
*   **Web Article Extractor Hardening:** Realistic browser request headers (`User-Agent`, `Accept`, `Accept-Language`), dynamic charset detection from HTTP headers and `<meta>` tags, and rich HTML sanitization.
*   **Background Playback Resilience:** Added OEM battery optimization whitelist settings helper to protect background TTS playback from aggressive task killers.

### Fixed
*   **PDF Password & Encryption Handling:** Wrapped PDFBox and Android `PdfRenderer` with graceful error handling and informative notifications for password-protected documents.
*   **Kokoro Neural TTS Memory Guard:** Added low-memory pre-checks preventing native OOM crashes on memory-constrained devices by gracefully falling back to lightweight TTS engines.
*   **R8 / ProGuard Obfuscation Hardening:** Comprehensive keep rules safeguarding all serialized models, WorkManager Workers, CrashReporter, and Glance widget receivers in release builds.
*   **Neural Playback Teardown Race:** Pausing, stopping, seeking, or switching voice mid-sentence could release the `AudioTrack` while the synthesis thread was still writing into it — a crash inside the native audio layer that the app's own exception handler could never report. Teardown now parks the track with `pause()`/`flush()` and defers the release to the writer, so a stop is always safe no matter where playback is.
*   **Playback Stop Responsiveness:** The neural playback wait loop read its stop flag without a memory barrier, so a stop issued from the UI thread could go unseen and leave the sentence running to its timeout. The flag is now `@Volatile`.
*   **Audio Engine Scope Leak:** Each voice change and every service teardown left the previous audio buffer's coroutine scope alive for the rest of the process. `shutdown()` now cancels the scope instead of only its children.
*   **Look-Ahead Window Bounds:** The pre-buffer window could be computed with negative indices while reading a text selection, wasting synthesis passes on positions that do not exist in the document.
*   **Stale Version Fallbacks:** The update checker and the Settings "About" panel fell back to a hardcoded version string that had to be edited by hand each release. Both now read `BuildConfig.VERSION_NAME`.
*   **Unused Room Dependency:** Removed the declared-but-unreferenced `androidx.room:room-ktx` dependency and its ProGuard keep rules; persistence is entirely SharedPreferences and JSON.

### Removed
*   **Ambient Soundscapes:** Removed the ambient sound engine and its five bundled loop recordings. The feature had no reachable entry point in the interface, and the uncompressed WAV loops accounted for 23% of the release APK.
*   **Bouncy Castle Post-Quantum Tables:** Excluded the unused Picnic post-quantum signature parameter resources pulled in transitively by PDFBox's certificate handling.

---

## [2.0.0] - 2026-08-19

### Added
*   **Preloaded Classic Library:** Bundled the complete, formatted story and lessons of *"Who Moved My Cheese?"* by Spencer Johnson, M.D., with high-resolution cover artwork as the default book on install.
*   **Revamped Onboarding & Guided App Tour:** Interactive onboarding carousel with voice-assisted descriptions, celebratory animations, and a guided app tour that navigates and opens the Notes Studio, AI Study Hub, and Settings Hub.
*   **Persistent Onboarding Quest Checklist:** Floating mission checklist tracking core app milestones (guided tour, import, voice customization, bookmarks) with touch passthrough and persistent progress across app restarts.
*   **Intelligent Cover Extraction Engine:** Multi-page cover candidate scoring with aspect-ratio validation, text-presence verification, and automatic cover image repair for PDFs, EPUBs, DOCX, and PPTX documents.
*   **On-Device Neural TTS & Sherpa-ONNX Engine:** Integrated high-fidelity neural speech synthesis powered by Sherpa-ONNX, Kokoro, and Piper with streaming audio buffers and background media session sync.
*   **Custom Typography & OFL Fonts:** Bundled Atkinson Hyperlegible, Bitter Variable, Literata Variable, and Lora Variable fonts for dyslexia-friendly and long-form reading comfort.
*   **ABI Split Packaging:** Configured architecture-specific release APKs (`arm64-v8a`, `armeabi-v7a`, and `universal`), reducing the standard 64-bit release APK down to ~54 MB.

### Changed
*   **Library Home Architecture:** Refactored hero card continue-reading carousel and recent documents grid with dynamic cover aspect ratios and golden glow focus rings.
*   **Navigation & Spotlight Lifecycle:** Enhanced spotlight overlays to seamlessly coordinate across root composables and separate Android window dialogs (Settings and Insights).
*   **Speaker Announcement Polishing:** Streamlined and refined voice-guided tutorial speech prompts for clear, non-repetitive walkthroughs.

### Fixed
*   **Quest Touch Interception:** Fixed gesture interceptor bug preventing clicks from reaching the floating mission checklist.
*   **Guided Tour Condition:** Fixed lifecycle bug where the interactive tour would fail to launch when onboarding was previously completed.
*   **ProGuard / R8 JNI Stripping:** Added comprehensive keep rules for Sherpa-ONNX, Kokoro, and native TTS classes in release builds.

---

## [1.1.0] - 2026-07-07

### Added
*   **PPTX Document Import & Extraction:** Added full support for importing and extracting text from PowerPoint presentations (`.pptx`) in both the Android app and KMP desktop client.
*   **Background Auto-Backups:** Integrated a scheduled background service using WorkManager to auto-backup library documents and notes.
*   **Daily Streak Reminders:** Added local notifications to remind users to maintain their reading habits.
*   **In-App Self-Updater:** Added automatic check-for-updates and post-update release notes popup.
*   **Background TTS Battery Exemption:** Added a request/settings hook for battery optimization exemptions to prevent Android from killing background TTS playback.
*   **Highlight Customization & AI Prompts:** Highlight coloring and grouping, selection sharing scopes, and advanced AI assistant prompts with page markers.

### Changed
*   **Vibrant Hero Card Toggle:** Added a toggle to settings to switch between subtle and vibrant accent gradient styles on the main dashboard.
*   **Overhaul & Reliability:** General performance improvements in rendering, theming, and layout animations.

### Fixed
*   **Share-Import NPE:** Resolved a crash occurring when importing from specific external share providers.
*   **Version Comparison Logic:** Corrected issues with parsing and comparing version strings for update prompts.

---

## [1.0.1] - 2026-06-16

### Added
*   **Resilient JSON Storage:** Integrated automatic backup files (`__bak` copies) for saved documents and notes. If a write operation is interrupted or corrupted, the app auto-recovers from the last-known-good backup instead of data vanishing.
*   **Inline Markdown Transforming:** Hidden markdown syntax characters are styled dynamically inline (Bold, Italic, Monospace, Headers).
*   **Remap Annotations on Edit:** An alignment algorithm that re-anchors bookmarks, highlights, and notes by matching sentence text when editing a document.
*   **General Note Reminders:** Exact alarm scheduling for general notes (using `SCHEDULE_EXACT_ALARM`) with automatic runtime fallbacks to inexact alarms when permission is restricted.
*   **Habit Tracker History:** Swipeable weekly reading statistics displaying an 8-week history of daily reading totals.
*   **Dark PDF Reading:** Custom color-matrix inversion filter for comfortable document viewing in dark mode.

### Changed
*   **UI Assets:** Replaced hardcoded text-based icons (`✕`, `⋮`, `✓`, `★`) with native Material 3 vector drawables.
*   **IME Keyboard Padding:** Added soft keyboard offset buffers to note editing sheets to prevent inputs from being obscured.

### Fixed
*   **Editor Caret Alignment:** Implemented bidirectional offset mapping (fixes cursor positioning jumps when navigating formatted rich text).
*   **Intent Restart Loop:** Cleared incoming stream intents after parsing to prevent infinite import loops during layout rotations or activity restarts.
*   **Spotlight Overlay Ordering:** Fixed onboarding spotlight overlays displaying behind popup dialogs on the insights page.
*   **Thread Safety:** Added `LIBRARY_WRITE_LOCK` to synchronize shared preference library updates between the UI thread and background media session thread.

---

## [1.0.0] - 2026-06-15

### Added
*   Initial stable release.
*   **Universal Document Import:** Support for TXT, EPUB, DOCX, PDFs, scanned pages, and web articles.
*   **Smart Text Extraction:** Text parsing via PDFBox, Android PDF rendering, and ML Kit OCR fallbacks.
*   **Text-to-Speech Playback:** Background media session integration, media buttons, Bluetooth/headset hook bindings, and custom pitch/rate controls.
*   **Native PDF Viewer:** Jetpack PDF fragment integration for rendering original page styles.
*   **Reading Habits Tracker:** Daily streak tracking and monthly session analysis.
*   **Dynamic Theming:** Deep theme sets including Material You, Liquid Glass, One UI style, and High Contrast.
*   **Home Widgets:** Playback control and cover widgets.
*   **ProGuard Optimizations:** Configured optimization rules for production builds.
