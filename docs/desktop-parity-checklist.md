# Desktop parity work ledger

The user's 20 October instruction is to wire the phone features through desktop frontend and backend before choosing removals. Keep features in scope; do not equate a visible control with a working feature. Android source is authoritative. Native behavior needs native evidence. This ledger is a working audit, not a completion claim.

## Current pass — 4 November 2026

Current source has concise app copy, partial Android JSON/ZIP restore, recorded session/timezone conversion, recent-book history and supported preference transfer. Checks: 26 frontend unit tests, 50 current Windows native library tests, all 50 current browser cases, build/lint/format and all-target Clippy passed. Android legacy aggregate activity, remaining settings/covers/rich data and actual-device/installed-dialog acceptance remain open. The user reconfirmed complete parity before the PC release; prioritize missing implementation before another packaging pass. The retained guarded 178-input MSI/NSIS and preview ZIP predate these latest source changes; installed acceptance remains pending. Current ZIP SHA-256 is 82ff51aa0cba915622606732d76f6b9cb239ceabf0a57281979725635f5a2446; earlier previews are preserved. Browser and unit evidence is scoped below; production completion remains unproven. Historical entries retain their original date/scope.

| Area | Desktop wiring | Verification / remaining work |
| --- | --- | --- |
| Four main tabs | Home, Library, Study, Notes; phone visual order | Browser navigation passed; native shell opened |
| Embedded destinations | Added/Classics in Library; vocabulary/AI in Study; listening from Home/player/settings | Browser reachability passed; continue checking contextual reader actions |
| Appearance | Six phone families; light/dark/system; extra/accessibility palettes; custom accent; reduce motion | Native validation and actual Windows app restart passed (Dracula/dark/custom accent/reduce motion). |
| Classics | 36 direct text editions, covers/search/genres/list; native download/cancel/install/provenance | Actual native Alice download, source retention and app restart passed. The earlier preview launch token blocked sockets (Windows 10013); direct approved native launch succeeds. Retry UI is wired. Borrowing-only sources and alternate editions remain in scope |
| Reader | Independent book toolbar/player, expanded canvas, single/two-page/scroll, Enter jumps, typefaces/spacing/paper, trackpad sizing, text selection, fullscreen and source views | Browser interactions and real Windows reader checked. Physical trackpad hardware and exact Office/publisher layout remain unverified. |
| Library | Import, metadata/collections, favourites, sorting/text search, confirmed single/batch removal, pasted text, 40-entry recent-book history and ordered reading lists with archive/restore | Browser list/deletion flows and native ordering/provenance/backup/deletion-pruning tests pass. Broader import options and full installed-window acceptance remain. |
| Notes | Anchored notes/bookmarks; personal notes/checklists/notebooks/Trash; 30-version history and bounded image/audio attachments | Native transactional, revision/media/archive tests and browser workflows pass. Real-window acceptance of every newer rich-note action, reminders and remaining note settings are open. |
| Study | Study overview collections, manual/imported cards, due review/SM-2 Lite; quiz creation/editing/practice/explanations/native scoring and results | Native validation/scoring/reopen/backup and atomic card import tests passed. JSON/table/Q&A reply previews wired. Local generation, summaries/key points, rich quiz media and API generation remain |
| AI handoff | Editable prompts/context, task/provider selection, clipboard, fixed native provider URLs, retained in-app draft | Browser clipboard/provider bridge and retained draft checked. Quiz prompts and Study reply import entry added. Reader selection seeds editable handoff context; real external provider launch acceptance remains |
| Vocabulary/dictionary | Reviewed vocabulary, source links and flashcards; pronunciation rules; explicit Wikipedia/Wiktionary/Google Translate handoffs through bounded native URLs | Native URL validation and the current 48-case browser suite include origin/query/size/control-character checks, explicit sending, retained drafts/errors and fresh selection seeds. Native OS-browser launch for these new actions is untested. Offline dictionary packs and automatic definition retrieval remain. |
| Listening | Queue, sleep timer, prefetch, 12 English Lite/Piper packages, 28 English Studio/Kokoro speakers, explicit installed Windows voices and passage WAV | Developer verifier passed all 13 neural packages, then repeated with process networking denied in a verified zero-capability AppContainer; retained MSI Microsoft Zira/Studio Heart previews reached WebView audio playback. Installed-GUI/fresh-user download and full Windows broker/host-wide offline acceptance, remaining phone voice variants, pitch/DSP, media/headset and output safety remain. |
| Transfer | Validated desktop JSON plus full ZIP archives (metadata v5) retaining sources/pages, highlights, catalogue links, reading lists, note revisions/media; text/notes/passage WAV exports | Native validation/transaction tests and populated full-archive runtime/reopen checks pass, including original/media bytes and revision/list order. Partial Android migration now converts supported records and retains the complete source backup for export; remaining Android conversion, installed dialogs, local sync packs, whole-book/queue audio and automatic backup remain. |
| Reading activity | Native measured reading/listening sessions, Android span/timezone conversion with unknown historical anchors, goals/streaks, weekly/per-book insights, corrections/exclusions/manual history and CSV | Native clock/validation/reopen/backup tests and browser workflows pass; actual Windows listening sessions observed. Reminders remain. |
| Import/browser/editor | Native chooser, bounded workers, retained original source views; persisted PDF page-range/cleanup preferences | Real retained PDF/EPUB/Office browser cases pass; browser IPC mocks do not establish native chooser behavior. Approved-folder browsing/sorting, encoding choices, advanced crop/columns/OCR, article import and extracted-text editing remain. |
| Packaging/platform features | Unsigned MSI/NSIS and explicit isolated profiles; canonical repair-source filenames; opt-in signing path | Retained MSI shortcut/repair/saved-data migration/reopen and Microsoft/Studio previews passed on this Windows host. Rebuilt preview installed acceptance remains pending. SDK SignTool is present; issued certificate/private key is absent. Full notices/source obligations, clean host/offline acceptance, other OSs and desktop lifecycle/media work remain; no updater/publication enabled. |

## Android registry coverage

Audit each definition in `app/src/main/java/com/veritas/reader/VeritasFeatureRegistry.kt` against its implementation and desktop entry points:

- Audio output safety; headset control mapping; sleep timer.
- Encoding options; PDF import controls; file browser; file sorting.
- Pronunciation rules; Voice Studio; offline study tools.
- AI app handoff; translation handoff; offline dictionary; Wikipedia lookup.
- Extracted-text editor; bookmarks/notes; reading lists; reading history.
- Local sync pack; queue audio export; download monitor.

The registry is not exhaustive: also inspect `CoreModels.kt`, Settings Hub, Library/Notes/Study screens, reader tools, import/backup workers and platform services. Legacy PPT work remains paused under the earlier specific user request. Keep that exception recorded; do not infer any additional removals.

## 29 October 2026 — Reading workspace and native settings

Settings now directly expose display, reader, notes, accessibility, pronunciation, AI/study, PDF import, sync/backup and About destinations. Reader fonts, spacing and paper tones; notes paper/editor/checklist/layout defaults; accessibility motion/transparency/bionic/section/contrast controls; PDF page ranges and cleanup; and preferred assistant are wired to native settings. Source thumbnails persist in schema v8, and missing covers reuse Android's generated jackets. Native backing and the Windows preview are updated; no dead OCR, cloud-sync or provider-generation switches were presented as implemented.

Remaining work continues to include Android backup/sync migration, full source/media backups, whole-book audio, richer note media/reminders/revisions, OCR/crop/advanced PDF columns, local/API study generation, reading-list catalogues, fuller voice/media controls and platform/distribution acceptance. The prior legacy-PPT pause remains in force.

### Finishing pass: library selection and spoken text

- Wired: right-click/keyboard selection entry, normal browsing without checkboxes, individual/batch confirmed deletion, favorite/queue/collection actions, finished-last sorting across shelf orders and read-status filter.
- Wired: actual-output spoken ranges in reading/PDF/source text, manual selection kept independent, double-click fix, selected Listen, selection toolbar placement and deferred page following during selection. Browser action checks pass on mocked libraries; actual Windows CSS Highlight support was observed. Full cross-platform/native GUI acceptance is not implied.

### Voice-source expansion — 30 October 2026

- Wired: installed Windows/Microsoft discovery and native synthesis, explicit source selection, locale/gender labels, custom preview/stop/refresh, fixed Windows Speech settings link; 28 shared-package English Studio speakers with stable v1.0 mapping.
- Verified here: six Microsoft voices and five Studio representatives at normal/faster rates, obsolete/cancelled work, native preference reopen tests, real WebView2 Microsoft playback and saved/restored selection. 35 choices discovered; ten user books/positions preserved. Browser navigation/reader/voice scenarios pass. This does not establish every speaker's subjective quality, online/Narrator voice availability, other operating systems or production readiness.

- Voice packaging completion: final unsigned Windows MSI and NSIS builds passed with current embedded JS/CSS; no installer installation or production acceptance. Windows voices passed without Vern model files, but restricted execution also blocks the Windows speech broker, so a full offline Windows speech pass remains open. See progress.md for exact hashes and commands.


### Installed Windows validation — 31 October 2026 (in progress)

- Historical MSI/NSIS files are absent; rebuilding current source, keeping the recorded hashes as history. Added explicit isolated release-profile support and a read-only safeguard/copy of the two-book production library.
- Frontend build and 19 unit tests passed; native/full-browser checks, rebuilt MSI install/shortcut, copied-library migration/reopen and installed Microsoft/Studio previews continue. No installed acceptance or readiness claim yet. See progress.md and ignored desktop/.artifacts/msi-validation-20261031.


### Goal expanded — 31 October 2026

The user expanded the goal from installed MSI acceptance to finishing all desktop work and production readiness, with all required tests followed by cleanup because disk space is limited. Continue the remaining production-readiness/parity ledger; the earlier legacy-PPT pause remains the specific recorded exception. Cleanup must preserve source/lockfiles, libraries/settings/models, final installers/hashes and concise verification evidence. Initial volume observation: 30.31 GiB free of 237.65 GiB. No deletion has occurred in this pass.


### Verification checkpoint — 31 October 2026

- Wired and tested: isolated release profiles; shared checklist saving before attachments; voice failure-state locale/gender. Native full suite passed 31/31 and Clippy all-targets passed; final library/binary/doc retry avoids the active verifier image lock.
- Rich full-archive/media/revision/list fixtures pass natively. The actual two-book saved library also passed copied archive/settings round trip (no retained originals in that profile). Existing production AppData remains preserved.
- Browser independent run: 41/42; sole stale note-layout assertion corrected, then settings/note versions/checklist flows passed 3/3. Not a clean single 42/42 claim. Frontend build/19 unit tests/format pass; lint has one existing Fast Refresh warning.
- Native discovery returns 46 choices: 12 Lite, 28 shared-pack Studio and 6 Microsoft. All six Microsoft voices passed two-speed synthesis/cancellation without Vern model files; fresh Heart/Lessac pass native synthesis. Additional pinned packages continue. Installed shortcut/restart/voice UI and full offline acceptance remain pending.
- Installer checkpoint is being built from an identical frozen source copy; all remaining production criteria stay open. Cleanup is deferred until final tests, with final installers, compact proof, models and saved data protected. Exact commands, failed attempts and platform limits are in progress.md.

### Installed Windows validation and share pack — 31 October 2026

- **Validated:** Current Vern 0.1.0 MSI installed to an isolated desktop folder with exit code 0. Start Menu and Desktop shortcuts resolve to the installed executable. The shortcut launched a visible Vern window and relaunched successfully.
- **Saved data:** The isolated copy migrated from SQLite schema 6 to 13 with integrity ok. The original two books/positions and saved settings hash survived relaunch. One additional book row appeared and was preserved because its origin was not determined. The original AppData database, WAL and SHM hashes still match the pre-install snapshot.
- **Installers:** MSI 17,752,064 bytes, SHA-256 96a38e63d97506bec65d8bacd3e1bbe3786e04c6c0bfa7465c53f68657e78386; NSIS 13,575,764 bytes, SHA-256 625e6df0921aefe31504c0f8a5fe826cbead94fd0879c6a0e1f446ef503cfa87. Both are unsigned. MSI install was exercised; NSIS was built and hashed only.
- **Share asset:** .artifacts/release-pack-20261031/Vern-0.1.0-windows-x64-release.zip, SHA-256 36daf0a37734ba87b00ab814180332e3f3de015f81b890da6feb2b53b3e1cae3. Archive extraction verified both installer hashes. Pack includes the project Apache 2.0 license and checksum file, but third-party dependency notices remain to assemble. It is a validation preview from the local working tree, not a tagged or production release.
- **Tests passed:** Frontend build; unit tests 21/21; lint (one existing Fast Refresh warning); format check; native format; Rust tests 33/33; all-target Clippy with warnings denied; browser E2E 42/42 on Windows Chrome. E2E required stopping only the owned Vite WebServer process after all 42 cases printed ok; the runner then exited 0.
- **Voice evidence:** Native backend synthesis covered all six Microsoft voices plus Studio Heart and Lessac. Browser voice selection/preview wiring passed. Manual preview interaction in the installed app is unverified because native desktop automation timed out.
- **Still open for production readiness:** public code signing (no private-key certificate is installed), full offline Microsoft broker acceptance, third-party notices, other Windows hosts and macOS/Linux, plus the remaining Android parity items listed above. Restricted broker returned 0x800455A0; no upload, publication, updater enablement or release tag was made. Full commands and observed failures are recorded in progress.md.

### Generated-output cleanup — 31 October 2026

After all listed final checks, removed desktop/src-tauri/target, desktop/node_modules, desktop/dist, desktop/test-results, the frozen source copy and temporary native verifier. Measured C: free-space increase: 11,407,020,032 bytes (about 10.62 GiB). Preserved both lockfiles, Rust tooling, unsigned release ZIP/checksums, installed validation app, profile/library/voice models, and production AppData. Rebuilds will require npm ci and a fresh Tauri build.


### Installed MSI acceptance recheck — 2 November 2026

- [x] Retained 0.1.0 MSI bytes/signature rechecked: SHA-256 `96a38e63d97506bec65d8bacd3e1bbe3786e04c6c0bfa7465c53f68657e78386`, 17,752,064 bytes, `NotSigned`.
- [x] Real installed Vern window opened through the interactive desktop with an explicit isolated profile; four saved books were displayed. Restricted-shell missing-window probes are inconclusive, rather than proof of startup failure.
- [x] Installed Microsoft Zira and Studio Heart Preview controls reached `Previewing voice` and WebView `Audio playing`; Heart used the installed Kokoro v1.0 package. Subjective audio quality and networking-disabled behavior were not checked.
- [x] Existing MSI repaired with exit 0, preserving the validation library. A renamed MSI repair first failed with 1603/1316/2203 because Windows Installer sought the original filename; retrying identical bytes as `Vern_0.1.0_x64_en-US.msi` succeeded. Keep this original filename in future packs.
- [x] Repaired Start Menu shortcut resolved to the isolated installed executable and visibly reopened the four-book library. SQLite integrity, book-position and settings hashes stayed identical before previews, after repair and after relaunch.
- [x] Separate preserved two-book schema-6 copy migrated to schema 13 through the installed shortcut; both books/positions, two annotations, one personal note and settings hash survived unchanged.
- [x] Desktop shortcut visibly reopened the two-book migrated copy; positions, saved 1x/Lessac, annotations, note and settings hashes remained intact.
- [ ] Validate a fresh current-source bundle: 13 of the installer's 152 recorded build inputs differ from the current worktree. These runtime results apply to the retained preview MSI.
- [ ] Remaining production gates stay open: full outstanding parity, current native/build checks, offline Microsoft broker acceptance, complete notices/model terms, trusted signing and other-platform/host acceptance. Exact commands, failures and captured evidence are in `progress.md` and `desktop/.artifacts/msi-validation-20261102/`.

- Production-copy check: originals and saved settings are intact, but live schema is now 13 and WAL/SHM differ from the historical baseline. Earlier whole-file-unchanged claims no longer describe current state; the cause/timing is unproven. See progress.md for the stable-copy inspection and earlier launcher limitation.


### Current-source check recovery — 2 November 2026

- [x] Locked offline npm install; frontend build; 21/21 unit tests in one-worker mode; lint and formatting. Initial parallel reader test timeout is retained in progress.md.
- [x] Native formatting and 33/33 Rust tests, followed by successful binary/doc-test targets. Pinned Sherpa archive fetched and its observed hash recorded; Rust source/lockfiles remained unchanged during verification.
- [x] Fixed development worker dependency discovery and inefficient large-fixture JSON transport. The same real files, worker parser, assertions and timeouts remain. Initial browser result was 39/42; a later partial retry was 4/5; exact failures are retained.
- [x] Fresh-cache full browser run passed 42/42 in 5.3 minutes, with exit 0 and automatic cleanup of only the owned Vite process. A separate traced cold cover check passed in 9.4 seconds. These do not substitute for the real native MSI/voice evidence recorded above.
- [ ] Post-fix lint/format passed; collect all-target Clippy completion and create and validate a current-source Windows bundle. The retained MSI's native acceptance does not prove a later source bundle.
- [ ] Fresh-profile voice-download GUI check was interrupted by the user's Escape key. Catalogue code offers per-voice Lite downloads and one shared approximately 126 MB / 28-speaker Studio package; models remain separate downloads. Desktop interaction stays stopped while headless work continues.
- [ ] Production gates and unfinished parity remain open. No signed release, updater enablement or publication. Full commands, unsuccessful attempts, source hashes and limits are recorded in progress.md and `desktop/.artifacts/msi-validation-20261102/`.


### Windows bundle checkpoint — 2 November 2026

- [x] Current recorded checks: frontend build, 21 frontend unit tests, 33 Rust tests, native formatting, post-fix lint/format and all-target Clippy with warnings denied.
- [x] One complete cold-cache browser run passed 42/42 in 5.3 minutes and exited normally with owned-server cleanup. Earlier failed runs remain documented.
- [ ] Fresh unsigned MSI/NSIS build is running against a 177-file input manifest with before/after hash checks. Hashes/signatures will be recorded after it actually succeeds.
- [ ] Fresh-bundle installed UI acceptance remains pending; desktop interaction was stopped by the user's Escape key. Retained-MSI shortcut, copied-data migration/reopen and both installed voice previews remain separately proven.
- [ ] Production/notice, signing, offline-broker and remaining parity gates remain open. Native Clippy succeeded; a subsequent reporting-only PowerShell boolean typo was corrected and recorded in progress.md.


### Current-source Windows preview pack — 2 November 2026

- [x] All current-source required gates passed: frontend build/unit 21, Rust unit 33, browser 42 (Chrome serial), lint, formatting and all-target Clippy. Earlier failures and recovery commands remain recorded; browser IPC mocks do not establish native acceptance.
- [x] Guarded current Windows build exited 0 with 177 input hashes unchanged. MSI SHA-256 `e34e1594bd24c50364750563d4f1d17f8ea5df5b8dedbef7a25714a23d2bda78`; NSIS SHA-256 `f774708500d322afc3e09bc81fe091e76e5e6c30ff3cec7689705340e5e3b7fb`. Both unsigned.
- [x] Local validation ZIP assembled with canonical installer filenames, project licence, available notices, build-input/status manifests and verified installer-entry hashes. `.artifacts/release-pack-20261102-current/Vern-0.1.0-windows-x64-preview.zip`, 32,732,271 bytes, SHA-256 `b9ce7a11e9a7d839654f28a67dc9569181eb40bcfd13cc87af65a25691920c04`. No model binaries or personal library/profile copied.
- [x] Read-only MSI table inspection and Dark payload extraction completed. New ProductCode `{CFB6DC76-E5C2-4CAF-9C41-5DFBD981BD75}`, unchanged app version 0.1.0. Dark returned 0 with seven dialog/control decompilation warnings; wizard behavior is not verified. Compare installed bytes with the MSI's own extracted payload because bundling NSIS patches the root executable afterward.
- [ ] Rebuilt MSI actual install/update, its shortcuts, saved profiles/settings and one Microsoft/Studio voice. Earlier MSI passed; acceptance cannot be carried forward automatically. Desktop controls remain stopped after the user's Escape; resumption requested.
- [ ] Fresh-user download flow, full networking-disabled Windows broker, NSIS install, clean second host/platform coverage and other outstanding desktop feature rows.
- [ ] Complete runtime/model notices and source obligations, trusted signing and production readiness. Collected 836 notice files across 578 source-package components; 15 lack a notice file, 45 optional npm packages are absent, and the native static archive is excluded. This is not an exact binary SBOM or completed licence review. No runtime/model substitution, publication or updater permissions.


### Current native inference follow-up — 2 November 2026

- [x] Current developer verifier, using the isolated profile's existing packages: SQLite scratch close/reopen, twelve Piper packages and Kokoro Heart all passed non-silence, finite samples and obsolete-generation cancellation. Exit 0; thirteen WAV PCM/hash measurements retained. No fresh downloads or subjective listening claimed.
- [x] Saved validation library/settings unchanged after the verifier: schema 13/integrity ok, four books and prior position/settings hashes. The verifier uses a distinct scratch SQLite file.
- [ ] Networking-disabled repeat: a verifier-only outbound firewall rule was attempted, but Windows denied creation before inference. A separate read confirms no rule remains. Wrapper error handling was corrected; no bypass or inference-offline success claimed. Windows speech broker remains separately unverified offline.
- [x] Supplemental notice evidence collected for 12/15 previously missing source-package notices, in a separate folder; existing preview ZIP hash unchanged. Three full-text evidence gaps, native static archive/model obligations and complete licence audit remain open.
- [x] Thirteen generated native WAVs removed after compact byte/hash/PCM measurements, 1,638,076 logical bytes. Exact file targets/free-space measurements retained; no library/settings/model/installer/source removal.
- [ ] New bundle's installed-window/shortcut/data/voice acceptance, remaining feature implementation, broader generated-output cleanup, signing and clean-host/platform coverage. Desktop input is still stopped pending permission after physical Escape. No production completion or publication.


### Reference tools and current-source gates — 2 November 2026

- [x] Explicit Wikipedia/Wiktionary/Google Translate handoffs wired through Rust; fixed destinations, encoded text and bounded requests. Editable selection drafts, source-linked reviewed vocabulary and fresh-seed handling implemented. No automatic definition save or frontend persistence.
- [x] Current frontend build, 21 unit tests, lint and formatting passed. Native formatting, full Rust suite (35 tests plus binary/doc targets) and all-target Clippy passed. Two new native tests cover external-link input/encoding boundaries.
- [x] Full serial Chrome suite passed 44/44 in 3.8 minutes; preserved earlier 1/2 focused failure caused by an ambiguous new-test selector. Browser checks mock native IPC and do not prove OS browser launch. Actual 1440/900/390 screenshots and corrected 1440/768/390 inspector frames were viewed. Inspector: zero failures, five documented existing-style warnings; no full accessibility claim.
- [x] Cached native archive size/hash independently matched the official GitHub release digest. Read-only checksum-verified 2.0.1 Rust source shows `data_dir` is ignored; current Piper archives lack lexicons. No blind runtime/model change or newer-runtime inference claim.
- [ ] Fresh guarded MSI/NSIS build with all 178 file hashes/membership checked before and after: active, frontend build passed; no new bundle success/hash claimed yet. Preserved earlier preview ZIP excludes these later changes.
- [ ] New installed bundle, shortcut/data/voice and OS reference-browser acceptance after desktop controls are resumed; fresh-user download flow, full offline broker acceptance, remaining dictionary/parity features, licences/signing and final cleanup. No publication or updater permission.


### Rebuilt preview checkpoint — 2 November 2026

- [x] Current unsigned Windows build completed with 178 input hashes/file membership unchanged. MSI SHA-256 `5c930f4293d8f4047fe5c46388f912982e14aa478e9c309715c48f39f7bd4ac7`; NSIS `155170ab30675d4bda83ace8d090357b1d24993ace52fc6832192f3f61adf572`.
- [x] Updated local preview ZIP verified: `.artifacts/release-pack-20261102-reference/Vern-0.1.0-windows-x64-preview.zip`, 34,487,927 bytes, SHA-256 `82ff51aa0cba915622606732d76f6b9cb239ceabf0a57281979725635f5a2446`. All file entries hashed; all 178 application inputs checked directly. No personal/model/credential/audio/debug payload. Full dependency corresponding source and legal completeness are not claimed.
- [x] Latest MSI tables and exact embedded executable inspected without installing. ProductCode `{3C23B32E-6FB9-4F9F-8722-EF2EA5FCD9CE}`; payload SHA-256 `5498797028aea84e4a3c437819c8c5fdf3f1226b4159a80122d501ecec8d1a85`. Dark exit 0 with seven decompilation warnings; wizard behavior remains unverified.
- [x] Fresh four-book/two-book isolated before-install hashes/integrity/counts recorded. No new MSI preservation claim yet.
- [x] Added native-source notices/provenance: 14 cache files, 13 build.rs link entries, 6 pinned recipes, 15 notice files across 7 upstream refs. Supplemental full-text evidence now 14/15; natural-compare remains. GPLv3/source obligations and exact binary membership remain open.
- [x] Removed only verified normal-directory Rust incremental/test output after checks/build completed: 1,482,158,112 logical bytes; observed whole-volume free increase 1,259,012,096 bytes. Kept all user data/models/installers/toolchains/main caches. The initial cleanup guard failed safely before deletion and was corrected; full final cleanup/backlog completion is still open.
- [ ] Latest MSI install/shortcut/library/settings/voice and reference-browser native acceptance, fresh-user downloads, networking-disabled checks, signing, complete notices/source availability, other-host/platform coverage and remaining desktop feature rows. Desktop input remains stopped pending permission after Escape; no publication or updater enabled.


### Process-network-isolated neural verification — 2 November 2026

- Ran `& '.artifacts/msi-validation-20261102/verify-neural-appcontainer.ps1'`, exit 0. Actual child tokens were checked before execution: AppContainer, zero capabilities, fresh matching SID and low integrity. Direct TCP was rejected with Windows WSAEACCES 10013. An empty-profile `--download` run passed SQLite then failed to reach the voice server, expected exit 1. No host network/firewall settings changed.
- Repeated the native verifier without `--download` using isolated copies of all thirteen cached packages: twelve Lite/Piper voices and Studio/Kokoro Heart passed finite/non-silent synthesis, cancellation and SQLite close/reopen, exit 0. Thirteen PCM WAV headers/counts/hashes retained in `appcontainer-neural-result.json`. This closes the process-network-denied developer neural gate on this Windows host. Other Studio speakers, installed GUI/fresh-user download, normal Windows broker and host-wide offline acceptance remain open.
- Fresh-PC source flow confirmed: absent packages remain visible with explicit downloads. Lite voices download individually; one ~126 MB Kokoro v1.0 package serves twenty-eight English Studio speakers. Models are not included in the installer. The application runtime needs no user-installed Python/Node or API key. Current installed-app and second-PC acceptance are still required.
- Test-only AppContainer and scratch copies were removed after owned children exited; 669,895,173 logical bytes deleted. Original model/data files, installers and source remain. No desktop input, publication, updater or signing change. Exact command/failure/scope details are recorded in progress.md.



### Populated native full-backup verification — 2 November 2026

- Offline/locked `cargo run --manifest-path src-tauri/Cargo.toml --example verify-transfer -- <checkpointed-copy> <isolated-artifacts>` passed for the retained two-book library and then a populated schema-13 fixture. Native export/validation/restore preserved every compared record/preference and all source/media bytes. The SQL-seeded rich fixture contained four books/two originals, four annotations, two highlights, two flashcards, one quiz, three notes/one notebook, three revisions, two attachments, two sessions, two ordered/archived lists and one catalogue link. This is not installed UI import/creation acceptance.
- First rich attempt correctly rejected the test fixture's empty bookmark. Fixed the fixture rather than the application validator; the corrected final native run exited 0. Post-process read-only SQLite reopen compared all thirteen tables, BLOB hashes and revision order; integrity and foreign keys passed. Archive SHA-256 `fb3b30e1801f17a80b83f421f1b809418f7e10db25c6ea22bfac8e83dbe166ba`, 170,036 bytes. Its actual metadata schema is `vern.desktop.backup.v3`; v1 JSON and v2 archives remain supported by source.
- Kept compact command/fixture/reopen evidence, then removed only the exact owned scratch copies/archives: seventeen files, 5,458,977 logical bytes. Real libraries/models, original checkpointed snapshots, installers and compiler caches are preserved. Current installed dialogs, Android transfer, automatic backups/sync, whole-book/queue audio and other release gates remain open. Exact commands/failure/limits are in progress.md.



### Parity-first work and concise app copy — 2 November 2026

- User reconfirmed the finish line: complete desktop parity, then the PC release. Missing implementation takes priority over repeated unchanged validation or another preview pack. Android migration is next; do not clear unfinished backlog entries as cleanup.
- Home now uses a single heading and direct labels; repeated promotional copy was removed from the main destinations and the backup panel. Shared fonts/tokens, functional download/privacy/confirmation text and navigation remain. Build/lint/format passed, full frontend unit rerun 21/21 and browser suite 44/44 passed; final focused preview repeats passed after actual narrow-layout/gradient fixes. Three-width captures and all six inspector frames were viewed; final inspector zero failures/five retained style warnings. These source changes are not in the previous MSI.
- Added the native Android JSON/full-ZIP reader, metadata validation and bounded private attachment staging, sharing existing ZIP validation with desktop archives. Three new tests plus the existing native suite passed (38 total), and all-target Clippy passed. **Android-to-PC restore still is not usable**: worker coordination, converted-position/data mappings and native single-use preview/merge remain to be wired. The UI says so explicitly. No native restore acceptance is claimed from the parser tests.
- Fresh native download attempt passed nine packages then failed to reach HFC Female's server; preserved that failed receipt. Retry after process reopen completed thirteen packages while preserving installed-marker hashes. Full model-file audit matched all pins and the identical retained packages previously tested with process networking denied. No extra fresh-profile offline repeat or GUI-download claim. Removed only the audited disposable profile (636,532,017 logical bytes), keeping thirteen manifests and logs/audits.
- Latest installer acceptance, broader remaining feature rows, trusted signing, linked-native/model/source distribution obligations and other hosts/platforms remain open. The scoped fifteen missing dependency notice texts are now collected, including the author's natural-compare MIT text; this does not close the overall licence audit. No publication/updater permission or new release pack.



### Android conversion and retained backups — 4 November 2026

- Native restore chooser and the real worker are now connected through an opaque, sequential conversion and the existing single-use preview/savepoint merge. Supported readings/positions/originals, notes/bookmarks/highlights, cards/schedules, playable quizzes/best scores, notes/notebooks/revisions and supported media, lists, vocabulary and literal rules convert into desktop records. Existing desktop progress/schedules survive repeated merge. Whole-book cards keep a book association without an invented sentence; imported quiz best scores keep last/attempt counts unknown until desktop practice.
- Schema 14 retains complete source metadata and included Android files for re-export, including unconverted data. Desktop full ZIP metadata v4 includes that provenance; v1–v3 remain readable. Hash-only archive paths, manifests/checksums, staging bounds, rollback and provenance-only confirmed deletion are covered. Retention is recoverability, not functional parity.
- Full native test command passed 42/42 plus main/doc harnesses; after the Clippy regex correction the two affected restore tests passed again. A later valid-Android-small-text compatibility fix passed all 43 library tests and all-target Clippy; unsupported size preferences keep the current desktop size with an explicit limit and the original value retained. Frontend has 26 unit tests; three new focused browser cases use the real indexing worker with mocked native IPC. The prior 44-case full browser run predates this source work; no 47-case full-run claim is made. The compact restore preview and final checkbox layout were inspected from rendered captures.
- Android session/timezone/revision mapping, opened-book/legacy activity history, AI template/history use, reminders, remaining settings/covers/rich media/pattern rules and actual-device/installed-dialog acceptance remain open. Scans require their originals in a full ZIP. Voice preferences from Android stay explicitly unavailable rather than selecting another engine. All remaining parity/release/cleanup rows stay open; the old installer/ZIP still predates this work.


### Android session history — 4 November 2026

Recorded Android reading/listening spans now become native sessions with their original timezone dates, measured totals, corrections, exclusions, manual flags and zero-duration records. Raw session revision/indices/pages/spans remain in the session itself and in desktop backups. Unsupported historical sentence links stay unknown; Insights opens the current book position and CSV leaves those passage fields blank. Long imported measurements survive exclusion unchanged. No page-count-to-time conversion or PC-timezone substitution was added.

Native library tests pass 46/46, including DST/midnight/skip-date/fixed-offset/order/zero/correction/tamper/reopen/backup cases; all-target Clippy and fmt pass. Frontend build, 26 unit tests, lint and format pass. Browser results are scoped in progress.md, including the corrected fixture failure and complete serial run. Bundled timezone data is IANA 2025b; actual-phone timezone-data comparison and native installed UI acceptance remain open. Opened-book/legacy activity history and the rest of the parity/release ledger remain in scope.


### Recent books and Android preferences — 4 November 2026

- [x] Native 40-entry recent-book history, Library navigation, search, current-position reopen, missing-book retention and confirmed history-only deletion. Schema-14-to-15 migration, persistence, quota/deduplication, detach/reconnect and backup/merge checks pass. Desktop full-backup metadata v5 carries `openedBooks`; v1–v4 remain readable.
- [x] Android recent-book records convert into usable entries; unsupported historical passage anchors remain unknown, with original records retained. Supported theme/mode/alias, RGB accent, reader font/layout/paper, accessibility, note defaults and assistant preferences transfer through native validation. Restore can apply preferences or keep current choices. Unknown choices retain current settings with explicit limits.
- [x] 50 current Windows native library tests; 26 frontend unit tests; all 50 current browser cases. Build/lint/format and native formatting checks are recorded in progress.md. Browser tests use mocked IPC; native preference conversion is covered by native persistence and restore assertions.
- [ ] Actual Android-device/export and current installed-native dialog acceptance. Legacy aggregate activity, remaining settings (including typography-baseline/narration differences), covers, rich media and AI data still need usable conversion. Complete remaining desktop feature rows before rebuilding the release.
- [ ] The design inspector timed out twice before measurement (`CDP Page.enable`). Actual responsive browser screenshots and functional checks passed; no inspector-success claim is made. Current-source packaging, trusted signing/source obligations, fresh-user/other-host acceptance and final disposable-output cleanup remain open. No publication/updater changes.
