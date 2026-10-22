# Desktop parity work ledger

The user's 20 October instruction is to wire the phone features through desktop frontend and backend before choosing removals. Keep features in scope; do not equate a visible control with a working feature. Android source is authoritative. Native behavior needs native evidence. This ledger is a working audit, not a completion claim.

## Current pass

| Area | Desktop wiring | Verification / remaining work |
| --- | --- | --- |
| Four main tabs | Home, Library, Study, Notes; phone visual order | Browser navigation passed; native shell opened |
| Embedded destinations | Added/Classics in Library; vocabulary/AI in Study; listening from Home/player/settings | Browser reachability passed; continue checking contextual reader actions |
| Appearance | Six phone families; light/dark/system; extra/accessibility palettes; custom accent; reduce motion | Native validation and actual Windows app restart passed (Dracula/dark/custom accent/reduce motion). |
| Classics | 36 direct text editions, covers/search/genres/list; native download/cancel/install/provenance | Actual native Alice download, source retention and app restart passed. The earlier preview launch token blocked sockets (Windows 10013); direct approved native launch succeeds. Retry UI is wired. Borrowing-only sources and alternate editions remain in scope |
| Reader | Single/two-page/scroll, fullscreen, text selection, mouse/keyboard/swipes, PDF original view, source-based Office/EPUB | Earlier native reader/restart checks passed. Latest cold-load timeout adjusted; current combined browser run recorded in progress.md. |
| Library | Import, metadata/collections, favourites, sorting/text search, remove, pasted text | Frontend/backend wired; broader import options and native acceptance remain |
| Notes | Anchored notes/bookmarks plus personal notes, checklists, notebook labels, colours, pinning, search/sort/grid/list, Trash/restore and permanent deletion | Native note/notebook/checklist/trash/reopen/backup tests and browser workflow passed. Real-window notes acceptance is recorded in progress.md. Media, reminders, revision history and full note settings remain |
| Study | Study overview collections, manual/imported cards, due review/SM-2 Lite; quiz creation/editing/practice/explanations/native scoring and results | Native validation/scoring/reopen/backup and atomic card import tests passed. JSON/table/Q&A reply previews wired. Local generation, summaries/key points, rich quiz media and API generation remain |
| AI handoff | Editable prompts/context, task/provider selection, clipboard, fixed native provider URLs, retained in-app draft | Browser clipboard/provider bridge and retained draft checked. Quiz prompts and Study reply import entry added. Reader selection seeds editable handoff context; real external provider launch acceptance remains |
| Vocabulary/dictionary | Manual vocabulary, source links, flashcard action; pronunciation rules | Dictionary packs/targets, Wikipedia lookup and translation handoff remain |
| Listening | Queue, sleep timer, one-passage prefetch, two neural voices, passage WAV | Native voice download/playback/cancellation acceptance still outstanding; full catalogue, pitch/DSP, media/headset mappings and audio-output safety remain |
| Transfer | Desktop JSON inspection/merge (including quizzes/results and personal notes/notebooks/Trash), text/notes/passage-audio exports | Android JSON/ZIP migration, local sync packs, rich/source backups, whole-book/queue audio and automatic backup remain |
| Reading activity | Saved positions and recent-reading order | Actual sessions/history, goals/streaks and insights remain; do not invent analytics |
| Import/browser/editor | Native file chooser and bounded import workers | In-app approved-folder browser/sorting, encoding choices, PDF ranges/columns/cleanup/OCR, article import and extracted-text editor remain |
| Packaging/platform features | Windows native icon assets and app shell | Installer/shortcut/tray/media keys/notifications/update/signing/platform acceptance remain. Keep phone-only features visible in the audit for appropriate desktop equivalents |

## Android registry coverage

Audit each definition in `app/src/main/java/com/veritas/reader/VeritasFeatureRegistry.kt` against its implementation and desktop entry points:

- Audio output safety; headset control mapping; sleep timer.
- Encoding options; PDF import controls; file browser; file sorting.
- Pronunciation rules; Voice Studio; offline study tools.
- AI app handoff; translation handoff; offline dictionary; Wikipedia lookup.
- Extracted-text editor; bookmarks/notes; reading lists; reading history.
- Local sync pack; queue audio export; download monitor.

The registry is not exhaustive: also inspect `CoreModels.kt`, Settings Hub, Library/Notes/Study screens, reader tools, import/backup workers and platform services. Legacy PPT work remains paused under the earlier specific user request. Keep that exception recorded; do not infer any additional removals.
