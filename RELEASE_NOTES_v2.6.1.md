# Vern v2.6.1 (Build 41)

Historical 2.6.1 release notes. The 13–14 October icon, customization, UI, gesture and navigation refinements belong to [Vern 2.6.2](RELEASE_NOTES_v2.6.2.md).

**Vern TTS v2.6.1 (Build 41)** is an extensive feature, reliability, and refinement update delivering **Bidirectional Notes Sharing & Multi-Format Export**, a persistent **Reading Sessions Ledger**, resilient **Background Vocabulary & Wiktionary Lookup**, **Save to Phone External Storage Export**, **Selective Liquid Glass** refraction styling, upstream **Kokoro 54-Speaker Voice Compatibility** with concurrency-locked voice downloads, **Interactive Onboarding Sliders & Gentle Scene Motion**, and **Optimized APK Packaging** with significantly reduced download sizes.

---

### 📝 1. Bidirectional Notes Sharing & Multi-Format Export
* **Seamless Incoming Shares:** Share text, web links, photos, audio, videos, and PDFs directly to Vern Notes from external apps. Handles single and multiple file streams with automatic deduplication.
* **App-Owned Storage Persistence:** Imported attachments are copied to app-owned internal storage on background IO with preserved filenames and MIME types before opening the editor—preventing data loss if the source file is deleted or moved by the sending app.
* **External Read Grants via FileProvider:** Outgoing sharing from both the note editor and notes list attaches files with secure `FileProvider` read permission grants and clean text formatting.
* **Multi-Format Export Options:** Notes with attachments offer rich export pathways: clean plain text, rendered PDF of note text, individual audio/media recordings, or a complete-note ZIP archive (including note metadata and attachments) fully restorable through Backup & Restore.
* **Audio Format Modernization:** In-app audio recordings now use high-fidelity AAC in M4A. Legacy AMR recordings stored in 3GP containers are cleanly extracted into genuine AMR files with standard headers, preserving original recording streams.

---

### ⏱️ 2. Measured Reading Sessions & Insights Ledger
* **Durable Session Ledger:** Introduces a versioned, local session ledger with stable session IDs, document identities, explicit reading modes, wall-clock timestamps, and monotonic active duration.
* **Silent Reading vs. Listening Separation:** Audio tracking begins precisely at confirmed audio playback start across both Android and neural TTS engines, halting at pauses, buffering, failure, or completion. Silent reading measures focused foreground reader visibility.
* **Interruption & Crash Resilience:** Frequent checkpoints and explicit close reasons ensure that unexpected app exits or process death close sessions at their last persisted checkpoint rather than improperly accumulating inactive time.
* **Local Midnight Rollover:** Preserves daily duration splitting at local midnight without corrupting historical aggregate metrics.

---

### 📖 3. Resilient Vocabulary & Wiktionary Lookup
* **Background WorkManager Resolution:** Looking up words on the web now saves the selected word, original sentence context, and source document before launching the browser. An independent WorkManager job resolves definitions in the background and updates the saved entry upon connectivity.
* **Dual-Source Dictionary Fallback:** If the primary dictionary service is unreachable or times out, lookups seamlessly fall back to Wiktionary with explicit source attribution.
* **In-Context Study Hub Integration:** Regular reader text and native PDF viewers share the durable save helper, allowing one-tap saving and refreshing of definitions into Study Hub vocabulary decks.

---

### 💾 4. Save to Phone & Document Storage
* **Permanent External Exports:** Export downloaded catalog books, TXT, EPUB, and PDF copies directly to external storage using the Android system document picker.
* **Uninstall Resilience:** Exported files reside in user-chosen device storage and survive app reinstallation or uninstalls.
* **Format-Accurate Fallbacks:** Preserves original document formatting on IO and cleanly exports extracted catalog content as genuine TXT.

---

### 🎨 5. Selective Liquid Glass & Onboarding Polish
* **Glossy Glass:** Shared backdrop blur, restrained translucent tint, subtle sheen and reflected rims on rounded controls. Add/Note floating pills avoid duplicate glass borders and elevation.
* **Dynamic Fallback:** Generic path animations and complex split navigation shapes gracefully fall back to ultra-smooth blurs, with full legacy fallbacks for high-contrast and reduced-transparency modes.
* **Interactive Onboarding Sliders:** Onboarding setup now features the unified `VeritasRoundSlider` and `VeritasSleekSlider` controls with accessible speed adjustments and persistent speech settings.
* **Gentle Scene Motion:** All five onboarding vector illustrations feature subtle scene drift with full Respect-Reduce-Motion support.
* **Material Study Hub Icons:** Outlined Material icons for reading streaks (fire, plant, trophy) and notebook revision history.

---

### 🎙️ 6. Kokoro 54-Speaker Voices & Concurrency Controls
* **Upstream Kokoro v1.0 Compatibility:** Updated package size validation to support the latest upstream 54-speaker voice packages alongside legacy 53-speaker exports.
* **Concurrency Locking & Queueing:** Robust per-package download locks deduplicate concurrent requests between Studio and voice selection, running up to 3 downloads concurrently while smoothly queueing additional requests.
* **Neural Speech Text Cleanup:** Unified Unicode NFC normalization, soft-hyphen stripping, and invisible copy-artifact cleanup prior to Kokoro and Piper neural synthesis.

---

### 📦 7. Packaging & APK Optimization
* **Streamlined JNI Packaging:** Excluded unused standalone C/C++ API binaries (`libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`) while keeping full ONNX Runtime and JNI bindings intact.
* **Size Reductions:** Releases are significantly leaner across all architectures:
  - **arm64-v8a**: reduced from ~55.3 MB to ~50.6 MB (-4.6 MB)
  - **armeabi-v7a**: reduced from ~46.3 MB to ~43.0 MB (-3.3 MB)
  - **Universal**: reduced from ~76.2 MB to ~68.2 MB (-7.9 MB)


### Final Listen and Insights refinements
- Listen uses rounded-square artwork, glossy circular skip/bookmark buttons, a wide Play/Pause pill and speed/voice/timer controls. Player/Read transitions and current-sentence emphasis respect motion settings.
- Reading Insights Overview restores Streaks, Weekly App Activity and Reading Activity with day indicators, weekly bars, measured reading/listening ring and Month/Year heatmap. Goals and editable sessions are in History; book details are in Books.
- Managed Notes media cleanup previews candidates and protects live, Trash, revision, recovery and active-recording references before revalidating deletion.
- PPTX text respects source auto-fit requests, avoiding clipping on the checked ISO slides. DOCX pagination remains approximate.
- Nineteen focused final phone checks pass, including compact/large text, glass, session/media persistence, playback startup and representative document canvases. Real-model human listening/export remains paused, and public release publication remains held.

The final visual pass adds a warm vector flame with embers, a floating gold trophy, gradient activity bars and a layered reading/listening ring. Continuous decoration stops under Reduce Motion. Listen controls clear the system bars while the background remains full-screen. The user is building the release; no final release APK is supplied from this pass.
