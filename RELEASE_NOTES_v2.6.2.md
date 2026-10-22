# Vern v2.6.2 (Build 43)

**Vern TTS v2.6.2 (Build 43)** is an extensive visual hierarchy, edge-to-edge system protection, rendering performance, security hardening, and cross-platform milestone delivering **Standardized Material 3 Typography & AMOLED True-Black Contrast**, a dedicated **Edge-to-Edge System Bar ProtectionLayout**, **Hardware-Accelerated Cover Extraction & Memory Optimization**, **Physical 3D Page Curl & Refined Reading Gestures**, **20 Dynamic Icon Palettes & Synchronized Splash Themes**, animated **Funky Cards**, **Zero-Bloat Play-Safe Security Hardening**, and the standalone **Vern Desktop Workstation** (Tauri 2 / React / Rust SQLite).

---

### 🎨 1. Standardized Visual Hierarchy & Semantic Material Typography
* **Semantic Material 3 Typography:** Standardized typography across 94 UI screens, dialogs, sheets, and card components. Replaced arbitrary font sizing with strict Material 3 type roles (`headlineSmall`, `titleLarge`, `titleMedium`, `bodyMedium`, `labelSmall`) for immediate visual hierarchy.
* **AMOLED True-Black Contrast:** Redesigned text and border contrast rules for OLED and pure black themes. Headers, subtitles, secondary metadata, and icon buttons preserve WCAG AA legibility across light, warm paper, dark slate, and AMOLED black backgrounds.
* **Proportional Wordmark & Compact Chrome:** Reduced Home wordmark to **26 sp** with a **28 dp** emblem (4 sp above tab titles) with a minimum 48 dp header height. Shaved **4 dp** of vertical height from both the floating player capsule and bottom navigation across all theme packs to maximize reading canvas real estate.
* **Predictable Hierarchy Tests:** Added automated UI test suites (`VisualHierarchyTest.kt`, `StudyHierarchyUiTest.kt`) ensuring cards, headers, and deck items strictly adhere to type scaling rules.

---

### 📱 2. Edge-to-Edge Protection & System Bar ProtectionLayout
* **App-Owned ProtectionLayout:** Introduced `VeritasSystemBarProtection.kt` with a custom `ProtectionLayout` that renders app-owned background tints behind transparent system bars across all display orientations, including landscape side navigation bars and display cutouts.
* **Safe Insets Architecture:** Replaced deprecated window color setters with coordinated `WindowInsetsCompat.Type.systemBars()` and `displayCutout()` listeners across `MainActivity`, `VeritasPdfViewerActivity`, and `VeritasPdfViewerLayoutHelper`.
* **Platform Scrim Suppression:** Automatically disables Android's artificial navigation bar contrast scrim (`isNavigationBarContrastEnforced = false`) on Android 10+ (API 29+), preventing muddy double-scrimming over custom paper themes and ensuring pure edge-to-edge color fidelity.
* **Cutout-Aware PDF Viewer:** Original PDF viewer chrome, top action bar, and bottom reading deck smoothly pad around camera holes, notches, and rounded display corners.

---

### 🖼️ 3. Hardware-Accelerated Cover Extraction & Memory Optimization
* **Pre-Allocation Size Scaling:** Upgraded `CoverExtractor.kt` to leverage Android's modern `ImageDecoder.createSource` API. Image dimensions are inspected from headers before allocating pixel memory, scaling oversized artwork directly to thumbnail bounds (`COVER_WIDTH`).
* **Hardware EXIF Orientation:** Respects native EXIF orientation flags embedded in user book covers and camera imports, preventing rotated or skewed thumbnails.
* **OutOfMemory (OOM) Prevention:** Employs `ImageDecoder.ALLOCATOR_SOFTWARE` and strict byte limits during cover extraction, preventing heap exhaustion when users import multi-megapixel raw photos or graphic-heavy PDFs.
* **Guaranteed Bitmap Recycling:** Extracted bitmaps are immediately recycled within structured `try-finally` blocks upon WebP encoding.

---

### 📖 4. Physical 3D Page Curl & Reading Gestures Refinement
* **Curved Mesh & Dynamic Shading:** Refined physical-style page curl using a denser curved mesh, realistic paper-backside translucency, and a moving cast shadow that follows the user's fingertip.
* **Tuned Gesture Timing:** Automatic page turns execute with a gentler 1,050 ms arc; manual swipe settling locks in at 900 ms. Reduce Motion preferences instantly disable decorative curling in favor of instant or light slide transitions.
* **Isolated Touch Interception:** Stationary pages retain full live text scrolling, taps, double taps, and native text selection without neighbouring pages intercepting touches. Pinch-to-zoom survives font updates and retains gesture ownership until both fingers release.
* **Smart Chrome Auto-Hide:** Extracted-reader top and bottom bars no longer hide prematurely when the viewport auto-scrolls to follow spoken sentences. Chrome auto-hide now triggers exclusively upon deliberate vertical user drags.
* **Decoupled Audio & Reading Positions:** Reading page synchronization and chapter progress follow the currently visible book independently, ensuring background audio playback of another title never overwrites the active reader position.

---

### 🌈 5. 20 Dynamic Icon Palettes & Adaptive Startup Splash Themes
* **20 Icon Colour Palettes:** Expanded launcher customization with 20 curated color schemes alongside **Follow my colour theme**, which dynamically binds the launcher icon to your active theme pack and light/dark mode.
* **Adaptive Startup Splash:** Android 12+ splash screens automatically match the active launcher icon palette, featuring user-selectable entrance animations (**Bloom**, **Fade**, **Rise**, and **None**) with a live in-settings preview. Backward-compatible static window backgrounds support Android 9–11.
* **Centered Launcher Geometry:** Centered launcher emblem at **52 dp** with expanded inner padding within the 66 dp safe circle, ensuring sharp display across circle, squircle, and teardrop OEM launchers. Full monochrome themed-icon support for Android 13+.

---

### 🎴 6. Funky Cards & Modern Design Suite
* **Funky Cards Overhaul:** Renamed Scattered Cards to **Funky Cards**, featuring refined subtle tilt angles, micro-offsets, and smooth settling arrivals across Library, Notes, Classics, Home, and Study cards.
* **Accessibility Fallbacks:** High Contrast modes automatically straighten all card angles; Reduce Motion settings suppress entrance animations while preserving card order.
* **Store Showcase Suite:** Generated comprehensive high-resolution Play Store graphics, clean Vern marks, and a full set of verified device screenshots (`release/v2.6.2/store-screenshots/`) showcasing page curl, audio playback, study decks, and insights.

---

### 🔒 7. Zero-Bloat Play-Safe Security Hardening
* **REQUEST_INSTALL_PACKAGES Removal:** Eliminated the high-risk package install permission from `AndroidManifest.xml` in favor of secure, policy-compliant Google Play updates and explicit installer intents for direct APK users.
* **Strict Download & Decompression Budgets:** Added HTTPS redirect validation, origin-specific download cookies, and total byte caps for browser downloads, voice models, and APK updates. Archive extraction enforces entry counts and expansion ratios to prevent zip bombs.
* **FileProvider Sandboxing:** Restricted `FileProvider` authorities strictly to app-managed media, originals, captures, and staged exports.
* **Credential Hygiene & Bot Hardening:** Completely sanitized embedded tokens in scripts and Cloudflare Workers, introducing authenticated webhooks and sanitized API errors for the Telegram release community bot.

---

### 🖥️ 8. Vern Desktop Workstation (Tauri 2 / React / Rust SQLite)
* **Dedicated Desktop Implementation:** Introduced [`desktop/`](desktop/README.md), a companion desktop reader built with Tauri 2.12, React 19, TypeScript 6, Vite 8, and Rust.
* **Native Rust SQLite Storage:** Rust-owned persistent database with content-hash deduplication, schema migrations, and validated reading positions—independent of browser storage limits.
* **Web Worker Document Import:** Multi-threaded worker pipelines for UTF-8/UTF-16 text, EPUB spine ordering, and PDF page extraction with compressed/decompressed limits.
* **Neural Speech Pipeline:** Staged, concurrency-locked Piper and Kokoro voice synthesis pipelines with checksum verification and Web Audio PCM playback.
* **Cross-Platform CI:** Added [`.github/workflows/desktop.yml`](.github/workflows/desktop.yml) with automated build matrix for Windows, macOS (Intel & Apple Silicon), and Linux.

---

### 🧪 Verification Summary
* **Android Unit Test Suite**: Executed 77 test suites with 485+ unit tests passing (100% success rate, 0 failures, 0 regressions).
* **Desktop Test Suite**: Vitest and Playwright test suites (text bounds, EPUB spine ordering, reader note anchoring, audio synthesis scheduling) passing 100% offline.
* **In-Place Upgrade & Startup Verification**: Verified in-place upgrade, splash theme transition, and About screen metadata on physical hardware.

---

### 📦 Release Artifacts & Verification

* **Android App Bundle (Google Play):** `Vern-v2.6.2-upload.aab` (58.9 MB)
  - **SHA-256:** `9BD66592CE9ACCD00F35AD9FC4E6C399D773E4ACE28E305A51510BD730C17635`
* **Universal Release APK:** `Vern-v2.6.2-universal-release.apk` (61.8 MB)
  - **SHA-256:** `E76DCFDE71CB6BC49CD34686D5812FF4A726DF1B58A7F5831696BA6988B8087D`
* **ProGuard / R8 Mapping File:** `mapping.txt` (151.4 MB)
  - **SHA-256:** `47FC76F7FD22749D75F900657C7EBFBFD84A158D2EE35BC08A55C56748E917D4`
* **Local Signer Certificate SHA-256:** `3B143A77A70253305A496595432461B018FFCB3792E82A96905BD10243424DB8`
* **Play App Signer Certificate SHA-256:** `C54A80058D6D9142C1179818DE7CB206BF0119E246403BEAC5FFA23686297E45`
