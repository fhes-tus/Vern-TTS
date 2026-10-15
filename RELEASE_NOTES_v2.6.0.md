# Vern v2.6.0 (Build 40)

**Vern TTS v2.6.0 (Build 40)** is a major milestone release introducing an expansive **Classics Book Catalog & Library**, sleek **Floating Live Playback Capsule**, comprehensive **Notes & Multi-Notebook Organization** with revision history, advanced **Pronunciation & Symbolic Speech Engines**, **Narration Studio & Cast Management**, and refined **Liquid Glass UI Styling with Unified Status Bar Tinting**.

---

### 📚 1. Classics Library & Expansive Catalog
* **Shared Library & Classics Shelves:** Seamless switching between your personal library and curated public domain classics, featuring authentic portrait covers, searchable genre shelves, and custom sorting.
* **Resilient Background Downloads:** Durable WorkManager-based book downloading with per-book progress, cancel/retry states, atomic duplicate prevention, and offline cover caching.
* **Non-Blocking Progressive Imports:** Rapid 5-page initial reader display while background batches continue; sequential non-blocking batch imports with automatic progress restoration.

---

### 🎧 2. Live Playback & Speech Architecture
* **Floating Playback Capsule:** Redesigned live playback bar hovering over navigation, featuring interactive waveform animation, direct cover navigation to current reading positions, and independent audio session lifecycle.
* **Pronunciation Rules Engine:** Literal Unicode word/phrase boundary matching, flexible whitespace handling, longest phrase priority, and real-time voice preview.
* **Narration Studio & Cast Management:** Character-based dialogue attribution, customizable voice and delivery assignment per role, and system/neural pitch-preserving PCM processing.
* **Mathematical & Symbolic Speech:** Spoken expansion of complex mathematical notation, chemical formulas, Greek symbols, Roman numerals, and scientific units.

---

### 📝 3. Notes & Notebooks System
* **Multi-Notebook Organization & Filtering:** Organize notes into custom notebooks alongside Voice Memos, checklists, reminders, and pinned items.
* **Trash Lifecycle & Version History:** Safe note deletion with trash recovery, permanent purging, and up to 30 revisions per note with instant one-tap rollback.
* **Rich Attachments & Photo Capture:** Direct in-app camera capture and bounded media buffering with comprehensive backup/restore packaging.

---

### 🎨 4. Design & UI Architecture
* **Liquid Glass & Dynamic Theme Packs:** Refined glass surfaces with continuous corner reflections, AMOLED true-black optimization, and pack-tailored typography and sheet shapes across Material You, One UI, and Vern styling.
* **Unified Status Bar & Top Bar Tinting:** Cohesive color alignment between status bar and top bar across all theme packs with dynamic luminance calculation, ensuring clock, battery, and notification icons always retain high contrast.
* **Movable Circular Batch Import Floater:** Streamlined, draggable circular indicator displaying real-time progress and file count with subtle perimeter ring; docks smoothly along screen edges and vanishes automatically upon completion.
* **Streamlined Onboarding Flow:** User-centric onboarding focusing on reading interests, live device TTS auditions, and speech rate calibration.

---

### 🧪 Verification Summary
* **Kotlin Compilation**: `compileReleaseKotlin` passed with 0 errors.
* **Automated Unit Tests**: `testReleaseUnitTest` / `testDebugUnitTest` executed -- 100% passed.
* **Full Release Assembly**: `assembleRelease` passed with 0 errors (all release APK variants generated, aligned, and signed).

---

### 📦 Downloads & Recommended Architectures

* **64-bit ARM (`arm64-v8a`) Release APK:** `Veritas-Reader-v2.6.0-arm64-v8a-release.apk` (55.2 MB) — *Recommended for 99% of modern Android phones & tablets.*
  - **SHA-256:** `7E8F86E6089EDBE02367404A7AB1634E80224DD94F1ADE0F69A6B565E9B8288C`
* **Universal Release APK:** `Veritas-Reader-v2.6.0-universal-release.apk` (76.1 MB) — *Compatible with all supported Android devices.*
  - **SHA-256:** `E71A27C54B778CF8625EA37EB983135FAAEE08A54C712475480E9DF12D837A15`
* **32-bit ARM (`armeabi-v7a`) Release APK:** `Veritas-Reader-v2.6.0-armeabi-v7a-release.apk` (46.3 MB) — *For legacy 32-bit devices.*
  - **SHA-256:** `5646A483A39ED488C75D5A66789C445B9DC51B5A6525E076EB9239FAA2CB9C9F`
