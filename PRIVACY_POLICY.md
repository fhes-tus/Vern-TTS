# Veritas Reader — Privacy Policy

**Effective Date:** May 18, 2026

Veritas Reader ("we", "us", or "our") develops the Veritas Reader Android application as a local, offline-first utility. This Privacy Policy describes how we handle user information, data storage, permissions, and third-party integrations when you use the app.

---

## 1. Overview & Core Philosophy
Veritas Reader is built on a **privacy-by-design** approach:
*   **Offline-First:** Your documents, notes, highlights, and habits are stored locally on your device.
*   **No Tracking:** We do not collect, monetize, or track your reading habits.
*   **No Analytics or Ads:** The app contains no advertising SDKs, tracking pixels, or telemetry frameworks.

---

## 2. Information Handled by the Application
The application handles the following data strictly locally on your Android device:
*   **Imported Documents:** Local text files, PDFs, DOCX files, EPUBs, shared text clips, and document images.
*   **Extracted Text:** Text extracted from your files via local PDFBox parsing and ML Kit text recognition.
*   **User Annotations:** Bookmarks, highlights, sentence-level notes, pronunciation rules, sleep timer configurations, and collections.
*   **Reading Statistics:** Daily reading time, streak progress, and library format distribution statistics.
*   **Web URLs:** Web links input by the user to parse articles.

---

## 3. Data Storage & Security
*   **Local Storage:** All reading files, extracted text database files, and system preferences are stored inside the application's private storage sandbox (`Context.filesDir` and `SharedPreferences`).
*   **Backup Integrity:** Stored database configurations use a resilient double-write backup system to prevent file corruption on sudden device restarts.
*   **Cloud Synchronization:** The application does not feature a cloud account registration system or synchronization servers in this build.
*   **System Backups:** Android may back up preferences, the reading database, and eligible app files according to your device backup settings. Original imported documents, restore staging files, and API credentials are excluded from cloud backup. API credentials are also excluded from device transfer, encrypted with a device-bound Android Keystore key, and must be entered again on a new device. Full backups created explicitly in Vern can include original documents and note attachments.

---

## 4. Hardware Integrations & Services

### Optical Character Recognition (OCR)
The application uses local machine learning models (Google Play Services ML Kit) to extract text from images and scanned PDF files. This processing runs entirely on-device; page images are never uploaded to remote servers.

### Text-to-Speech (TTS) engine
Playback is synthesized via your Android device's active Text-to-Speech (TTS) engine (such as Google Speech Services). 
*   **Synthesis Context:** The active sentence text is passed to the system TTS engine API to read the content aloud.
*   **Third-Party Engine Privacy:** Depending on the TTS engine you select, the provider may download voice models or route requests according to their own privacy policies. You can configure or restrict these settings directly in your Android system preferences.

---

## 5. Network Usage
Veritas Reader requests the standard `INTERNET` permission. Network requests are initiated **only** under the following conditions:
1.  **Web Imports:** When you explicitly paste a URL to import and extract text from a web article.
2.  **External Actions:** When you trigger external commands such as translating selected text, asking an AI app, or opening a web search.

The application does not run any background network uploaders, trackers, or telemetry scripts.

---

## 6. Sharing with Third-Party Applications
Veritas Reader enables you to share text sections, AI prompts, or translations with external apps. These actions are strictly user-initiated (e.g., clicking "Ask AI" or "Translate").
*   Once text is shared with an external target, that receiving application's own privacy policy applies.
*   We advise caution when sharing private, legal, or sensitive texts with external cloud services or generative AI engines.

---

## 7. Device Permissions
To function correctly, the application requests the following permissions:
*   `INTERNET`: Required to import web pages and access external translation/share endpoints.
*   `POST_NOTIFICATIONS`: Required on Android 13+ to display media control notifications.
*   `SCHEDULE_EXACT_ALARM`: Required to schedule exact reminder alerts on notes.
*   `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: Required to maintain active text-to-speech audio playback when the screen is turned off or when navigating to other apps.

---

## 8. User Control & Data Deletion
You retain complete control over your data. You can:
*   Delete individual documents, notes, collections, or statistics from the app interface at any time.
*   Clear all reading metrics and streak counters in the settings hub.
*   Wipe all application data and files instantly by navigating to **Android Settings > Apps > Veritas Reader > Storage > Clear Data**.
*   Delete all application directories and local files by uninstalling the application.

---

## 9. Children's Privacy
Because Veritas Reader stores all documents locally and does not collect or transmit personal information, it does not knowingly collect information from children. Parents should supervise file imports on shared family devices to ensure sensitive files are not stored locally.

---

## 10. Policy Changes & Updates
We may update this Privacy Policy to reflect app updates or security enhancements. When changes are made, we will update the Effective Date at the top of the policy.

---

## 11. Contact Information
For privacy questions, feature requests, or technical inquiries regarding Veritas Reader, please contact:
*   **Developer Support:** support@veritasreader.example.com
