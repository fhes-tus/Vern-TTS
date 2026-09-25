# Vern TTS v2.5.1 (Build 39)

**Vern TTS v2.5.1** is a high-stability and polish update addressing critical Google Play Console crash/ANR advisories, introducing a major **Typography & Typeface Overhaul**, **180-Degree Upside-Down Sensor Landscape**, **Resilient PDF Outlines & Table of Contents**, persistent reading progress tracking, and official website integration.

---

### 🛡️ 1. Google Play Console Crash & ANR Resolution
* **Audio Focus & Transient Interruption Resilience**: Keeps the playback foreground service alive during transient audio interruptions (such as incoming calls or navigation alerts) to eliminate `ForegroundServiceStartNotAllowedException` upon resuming speech playback.
* **Watchdog Timeout Mitigation**: Added protective try-catch handling in `startForegroundNow` to prevent system background execution restrictions from terminating the application (`ForegroundServiceDidNotStartInTimeException`).
* **Main-Thread Text Measurement ANR Fix**: Switched `TextView` break strategy to `BALANCED` and hyphenation frequency to `NONE` across reader views, eliminating UI-thread layout freezing and ANRs on dense book pages.
* **Background Page Image Preloader**: Lowered `DocumentPageImageLoader` thread priority to `THREAD_PRIORITY_BACKGROUND` and serialized adjacent page preloads to eliminate GC lock contention.
* **Security Advisory Resolution**: Bumped `androidx.glance` to 1.1.1 to resolve the Play Console security advisory for CVE-2024-7254.

---

### ✍️ 2. Typography Overhaul & Custom Typefaces
* **Three Dedicated Reading Typefaces**:
  * **Gazette**: A robust Slab Serif engineered for editorial comfort and long-form fiction.
  * **Crisp**: A modern Geometric Sans optimized for technical clarity and high scanning speed.
  * **Typewriter**: A monospaced font tailored for study notes, code references, and focused line-by-line reading.
* **Dynamic Typeface Binding**: Live runtime binding directly onto reader `TextView.typeface`.
* **Asset Optimization**: Trimmed legacy unreferenced font binaries, reducing APK package footprint.

---

### 🔄 3. 180-Degree Sensor Landscape & Enhanced Reader Headroom
* **Full Upside-Down Landscape Support**: Configured `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` across reader screens, allowing natural 180° orientation flipping for comfortable left/right-handed reading while plugged into a charger.
* **132dp Dynamic Reading Headroom**: Smooth animated headroom adjustment when top navigation bars are revealed, ensuring top lines are never obscured by system or app bars.

---

### 📑 4. Resilient PDF Table of Contents & Outline Navigation
* **Destination Resolution**: Implemented `PDNamedDestination` and `PDPage` destination mapping for instant table of contents jumping in complex PDFs.
* **Sentence Index Synchronization**: Mapped PDF outline items through `ReaderTextModelCache` to synchronize extracted text speech indices with actual document pages.

---

### 💾 5. State Persistence & UI Polish
* **Reading Progress Reliability**: Fixed position persistence across activity recreation and reader switches so your current page and exact sentence index are always preserved.
* **Dismissible Hero Continue Card**: Added fluid dismiss action to the home hero continue card.
* **Paper Tone Harmonization**: Standardized names across all reader views to Sepia, Bone, Dark slate, and Default with clean Android system toasts.
* **Community & Website Links**: Integrated the official Vern TTS website (`https://fhes-tus.github.io/Vern-TTS/`) and updated the GitHub repository URL (`https://github.com/fhes-tus/Vern-TTS`) across the About dialog and User Manual.

---

### 🧪 Verification Summary
* **Kotlin Compilation**: `compileDebugKotlin` passed with 0 errors.
* **Automated Unit Tests**: `testDebugUnitTest` executed 27 task suites — 100% passed.
* **R8 Compatibility**: Configured `android.enableR8.fullMode=false` ensuring safe JNI binding and reflection protection.

---

### 📦 Downloads & Recommended Architectures

* **64-bit ARM (`arm64-v8a`) Release APK:** `Veritas-Reader-v2.5.1-arm64-v8a-release.apk` — *Recommended for 99% of modern Android devices.*
* **Universal Release APK:** `Veritas-Reader-v2.5.1-universal-release.apk` — *Compatible with all supported Android hardware.*
* **32-bit ARM (`armeabi-v7a`) Release APK:** `Veritas-Reader-v2.5.1-armeabi-v7a-release.apk` — *For legacy 32-bit devices.*
* **Google Play Bundle:** `Veritas-Reader-v2.5.1-release.aab` (versionCode 39) — *For Google Play Console submission.*
