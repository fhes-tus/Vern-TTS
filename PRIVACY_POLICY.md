# Veritas Reader — Privacy Policy

**Effective Date:** October 15, 2026

Veritas Reader ("we", "us", or "our") develops the Veritas Reader Android application as a local, offline-first utility. This Privacy Policy describes how we handle user information, data storage, permissions, and third-party integrations when you use the app.

---

## 1. Overview & Core Philosophy
Veritas Reader is built on a **privacy-by-design** approach:
*   **Offline-First:** Your documents, notes, highlights, and habits are stored locally on your device.
*   **No Tracking:** We do not collect, monetize, or track your reading habits.
*   **No Analytics or Ads:** The app contains no advertising SDKs, tracking pixels, or telemetry frameworks.

---

## 2. Information Handled by the Application
The application stores the following data locally on your Android device. Optional online actions described below can send selected content or requests to the chosen provider:
*   **Imported Documents:** Local text files, PDFs, DOCX files, EPUBs, shared text clips, and document images.
*   **Extracted Text:** Text extracted from your files via local PDFBox parsing and ML Kit text recognition.
*   **User Annotations:** Bookmarks, highlights, sentence-level notes, pronunciation rules, sleep timer configurations, and collections.
*   **Reading Statistics:** Daily reading time, streak progress, and library format distribution statistics.
*   **Web URLs:** Web links input by the user to parse articles.

---

## 3. Data Storage & Security
*   **Local Storage:** All reading files, extracted text database files, and system preferences are stored inside the application's private storage sandbox (`Context.filesDir` and `SharedPreferences`).
*   **Backup Integrity:** Library changes use local database transactions. Explicit portable backups can include books, notes and managed attachments; keep important exported backups outside app-owned storage.
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
Veritas Reader requests the standard `INTERNET` permission for these features:
1. **Books and web content:** Catalog searches, book and cover downloads, the in-app browser, web-article imports and link previews contact the selected sites. Sites receive ordinary connection information such as the requested URL and IP address; browser sites may use first-party cookies.
2. **Voices and updates:** Voice-model downloads and release checks contact the configured model hosts, GitHub or their download delivery services. Downloaded neural voices run on the device. Android speech-engine network behavior depends on the installed engine and selected voice.
3. **Optional AI, translation and vocabulary:** When you enable or invoke these features, selected text, prompts, document excerpts, lookup terms or requests may be sent to the provider you choose. Direct API integrations use the credentials you supply; external-app shares use that app's own privacy policy. Avoid sending sensitive content to a provider unless you intend to share it.
4. **Background completion:** Selected operations, including vocabulary lookups and enabled update or content checks, can complete in the background. They do not constitute an account-based library synchronization service.

The app has no advertising or telemetry service. Online providers apply their own privacy policies to the requests and content they receive.

---

## 6. Sharing with Third-Party Applications
Veritas Reader enables you to share text sections, AI prompts, or translations with external apps. These actions are strictly user-initiated (e.g., clicking "Ask AI" or "Translate").
*   Once text is shared with an external target, that receiving application's own privacy policy applies.
*   We advise caution when sharing private, legal, or sensitive texts with external cloud services or generative AI engines.

---

## 7. Device Permissions
To function correctly, the application requests the following permissions:
*   `INTERNET`: Used for the optional network features listed above.
*   Storage access: User-selected document access and, when granted, broader file-browser access support importing and managing documents. Access is governed by Android permissions.
*   `RECORD_AUDIO`: Used when you record voice notes. Recordings are stored locally unless you explicitly share or export them.
*   `REQUEST_INSTALL_PACKAGES`: Used to open Android's installer for an app update you choose to download. Android verifies the package and asks for installation consent.
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
The app has no account-registration or advertising service. Parents should supervise file imports, sharing and optional online features on shared family devices; selected third-party providers have their own privacy terms.

---

## 10. Policy Changes & Updates
We may update this Privacy Policy to reflect app updates or security enhancements. When changes are made, we will update the Effective Date at the top of the policy.

---

## 11. Contact Information
For privacy questions, feature requests, or technical inquiries regarding Veritas Reader, please contact:
*   **Developer Support:** myreader.veritas@gmail.com
