# Reader and notes follow-up — 1 October 2026

## Reported reader failures

Device logs showed Preview issuing a new audio-focus listener on each Play request. Android delivered a duck event to the previous listener in the same app, which paused speech immediately. The service now reuses its focus client, ignores retired callbacks, and uses the normal initialization path for cold Read from Here. Pause also clears pending initialization speech.

Native text selection was being disturbed by presentation-span updates and pager following. The reader freezes those changes during selection/touch and obtains selected text from the actual Android buffer. A phone regression starts real system utterances, repeats Play, selects “questions,” advances the playback highlight, and checks the Search action still receives that exact word.

New PDF extraction keeps internal source-page metadata without adding spoken “Page N” lines. Legacy leading labels are hidden and silenced without rewriting sentence offsets. Empty-page wording is shortened. Text is available after a 300 ms media preparation window; placements discovered later stay at the page end for that visit. Image frames and sentence identities remain stable. One parsed PDF is cached, and illustration column probing is lazy.

File browsing publishes immediate folders and indexed file results before finishing recursive discovery. Obsolete scans cancel, recently visited folders are cached, and existing entries remain usable during a background scan. The deeper scan still runs, so progress appearing quickly does not mean every file was already found.

Audio export intentionally stops playback while generating speech; its initial status now explains this. Neural WAV export now streams Kokoro/Piper PCM; mixed Android/neural casts remain unsupported. See `TECHNICAL_DEBT_RECHECK.md` for the subsequent review and original-view work.

## Outlines

Embedded PDF titles retain their original numbering, including solitary Roman numerals and Contents bookmarks. Named destinations with numeric page references resolve too. Smart inference reconstructs sentence-split contents rows, prefers actual heading lines to earlier prose mentions, resolves physical reader pages, and uses a verified printed-page offset only when multiple entries agree. Repeated chapters in story collections remain distinct; weak running headers are removed before deduplication. Unicode titles are preserved. Ordinary sentence candidates and artificial truncations are rejected; an unstructured document receives location milestones rather than invented chapter titles. Smart Outline includes a page-number field with range validation.

Text inference cannot guarantee a semantic outline when extraction has lost all structural evidence. Rotated/ambiguous inline PDF images retain a page-end fallback.

## Symbols and pronunciation

`SpokenText` composes pronunciation-rule replacements with symbol expansion while retaining original source offsets. This supports highlighting and resume positions after longer spoken substitutions. English voices receive clear math/operator names, Greek symbols, powers/subscripts, canonical contextual Roman numerals, ISO dates, numeric ranges, recognizable chemical formulas and all 118 element names in chemical contexts. Ordinary “He,” “I,” “IN,” and similar text is preserved. Foreign prose and ambiguous dates stay with the selected voice. Latin abbreviations are expanded; arbitrary Latin passages do not receive invented English phonetics. Chemical formulas are read by element and count, without claiming systematic compound naming.

The display formatter also processes longer LaTeX commands first and avoids replacing prefixes inside unknown command names.

## Notes and annotations

Saving reserves a note ID before asynchronous work, processes requests in order, locks read-modify-write operations, confirms the SharedPreferences disk commit, and guards older UI completions. A single debounced save replaces overlapping autosave loops, with background flushing and saved-state restoration. Save failures remain visible and the editor remains open for retry.

Undo/Redo uses bounded document snapshots, including attachments and checklist mode. Checklist switching preserves current content; inserting media leaves checklist mode while retaining task text. Attachment removal updates metadata, and merging text around a removed block keeps a separator. Share and Make a Copy use the complete live draft. General notes no longer preload unrelated annotated books, pinned notes sort first, and vocabulary storage records do not appear as general notes.

Selected image/video files copy on an I/O dispatcher, using 64 KiB buffers, unique names, stream cleanup and completed-file publication. Full ZIP backups preserve referenced notes-store media and remap restored paths transactionally. JSON-only backups contain text/metadata and cannot carry binary attachments. Sentence-note audio and quiz media portability remain separate older backlog items.

This improves the existing notes foundation. Larger product additions such as notebooks/tags, trash and revision history, cross-device editing, and attachment garbage collection are still future work; this change does not claim parity with a full dedicated notes service.

## Verification

The first reader repair batch passed 324 JVM checks and two targeted phone regressions. The outline/symbol/editor batch passed 334 JVM checks and all 28 Android tests in the isolated `.checks` app. Final attachment/contents-format verification and installation are recorded in `IMPLEMENTATION_PROGRESS.md` once completed. Tests never uninstall the user's Preview package.

## Deferred actual-document feedback — 1 October 2026

The user explicitly requested that this feedback be recorded for a later implementation pass. No renderer changes are made for this report yet. This remains part of the existing work, alongside the outstanding items in `TECHNICAL_DEBT_RECHECK.md`.

- Original view should adapt to the page/content's vertical length rather than stretch short slides into a tall full-screen card. Center short pages/slides in the available safe viewport; if a full-height container is retained, center the content within it. Keep genuine source slide geometry/aspect ratio where available. Use 4–6 dp outer gutters/borders, distinguishing these from source-document internal margins.
- Keep slide number and previous/next controls close to the slide/page, below system status-bar insets. They must not sit far above the visible content or be clipped by the status bar. Retain reachable zoom controls without obscuring content.
- Investigate duplicate and fragmented PPTX text against the actual source rather than blindly deduplicating repeated labels. The reported “Scope of the Plan” view shows both split labels (e.g. “Design &” / “Development”) and combined copies (“Design & Development”), and repeated “Production,” “Warehouse,” “Activate,” and “Dispose.” Preserve intentional repetitions and diagram relationships.
- “The Heel Stick” has excessive empty vertical space in Preview; the release screenshot shows the preferred compact, centered card. Some Preview slides already retain a good source layout, so fixes must preserve that behavior instead of forcing every slide through the reading fallback.
- DOCX original view currently feels indistinguishable from extracted text. A later pass should preserve more source structure, styles, spacing, tables and image placement to make its original-view purpose clear, while acknowledging the current pagination approximation.
- The current theme/contrast safeguard returns a reading fallback, which changes source layout. The requested direction is to resolve inherited colors and, where necessary, choose readable foreground/background contrast while preserving layout. Keep the fallback only for cases whose structure cannot be rendered reliably; do not silently abandon an otherwise usable layout merely because of color inheritance.

Source deck: `C:/Users/wwwfe/Downloads/Telegram Desktop/5B Medical Device Risk Management Using ISO 14971.pptx`.

Screenshot references (four attachments were supplied, although the message refers to image 5):

- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_161232_Vern Preview.png` — Heel Stick, tall empty card.
- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_161539_Vern Preview.png` — Scope of the Plan, duplicated/fragmented labels.
- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_163408_Vern TTS.png` — release app's compact centered Heel Stick reference.
- `C:/Users/wwwfe/AppData/Local/Temp/Screenshot_20261001_163307_Vern Preview.png` — positioned source slide with detached/clipped top controls.

Future acceptance checks should compare this exact deck's source content and screenshots, cover both positioned and fallback slides, exercise short/long content and system insets, and verify no regressions in selection, zoom, navigation or extracted-text reading. No specific duplication root cause is confirmed by this saved report alone.
