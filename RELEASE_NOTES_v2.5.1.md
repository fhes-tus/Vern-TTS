# Vern TTS v2.5.1 (Build 39)

Vern TTS v2.5.1 resolves Google Play Console crash/ANR advisories and delivers new reading typefaces, 180° landscape rotation, and outline navigation.

### 🛡️ Stability & Performance
* **Play Console Crash Fixes:** Resolved audio focus interruption crashes (`ForegroundServiceStartNotAllowedException`) and background execution timeouts.
* **ANR Elimination:** Balanced text layout measurement to eliminate main-thread UI freezing on dense pages.
* **Background Preloader:** Optimized PDF page loader thread priority to eliminate GC lock contention.
* **Security Advisory:** Bumped `androidx.glance` to 1.1.1 to resolve CVE-2024-7254.

### 📖 Reading & Typography
* **New Dedicated Typefaces:** Added **Gazette** (Slab Serif), **Crisp** (Geometric Sans), and **Typewriter** (Monospace) with dynamic font binding.
* **180° Sensor Landscape:** Reader views now support full upside-down rotation for comfortable left/right-hand charging.
* **PDF Outline Navigation:** Resilient table of contents resolution and sentence index mapping.
* **Comfort Headroom:** Added 132dp animated headroom when top bars are visible.

### ✨ Polish & UX
* **Reading Progress:** Sentence and page positions reliably persist across app restarts and view toggles.
* **Dismissible Hero:** Added swipe-to-dismiss for the home continue card.
* **Paper Tones:** Standardized tone names across readers with clean Android system toasts.
* **Community:** Integrated official website links and updated repository references.

---

### 📦 Downloads

* **64-bit ARM (`arm64-v8a`) Release APK:** `Veritas-Reader-v2.5.1-arm64-v8a-release.apk` *(Recommended)*
* **Universal Release APK:** `Veritas-Reader-v2.5.1-universal-release.apk`
* **32-bit ARM (`armeabi-v7a`) Release APK:** `Veritas-Reader-v2.5.1-armeabi-v7a-release.apk`
* **Google Play Bundle:** `Veritas-Reader-v2.5.1-release.aab` (versionCode 39)
