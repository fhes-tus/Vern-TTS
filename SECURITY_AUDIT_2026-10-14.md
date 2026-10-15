# Vern 2.6.2 security audit — 14 October 2026

**No evidence of an intentional backdoor was found in the reviewed application code. Security weaknesses were found and remain open.** This is a source/configuration audit with selected dependency checks and a controlled debug-device probe, not a guarantee that every dependency, native library, Android version or website is safe.

The audit reviewed 295 Kotlin/Java source files, the manifest, browser/downloader, incoming shares, file sharing, credential storage, backups, update flow, voice archive handling and resolved debug runtime dependencies. The current ARM64 debug APK is 2.6.2 / 42, SHA-256 `31FB91FA34ED023816CAEFC2AB72BCA85E852AD2E04BB398CF396D5BB2A935B6`. Existing release packages were not rebuilt or certified by this audit. Application behavior has not been patched as part of this audit.

## Findings that should be addressed first

### S1 — Medium: internal playback commands exposed to other apps

`PlaybackService` is exported without a component permission in `app/src/main/AndroidManifest.xml:429`. `PlaybackService.kt:155` dispatches raw intent actions for Play, Pause, Stop, Jump, Speak selection, playback settings and timers without authenticating their origin. In particular, settings can be persisted for the active document, and Play can fall back to the active book or reading queue without the caller knowing a document ID.

An external caller could interfere with reading/listening, change progress or settings, or request spoken text. Android foreground/background service restrictions affect when a third-party app can start the service; this is a local cross-app issue, not proof that an ordinary web page can send these commands.

A harmless external ADB-shell Stop request to the debug preview was accepted: [device probe](docs/verification/2026-10-14/security-external-service-probe.txt). This confirms the exposed service entry point; it is not a complete exploit demonstration from an ordinary malicious APK. The missing permission and caller validation are confirmed in source.

**Fix:** retain legitimate Media3/system/Bluetooth controls, but route application-only commands through an authenticated app-only path. Simply disabling service export or adding a blanket signature permission could break those controls. Do not use `Binder.getCallingUid()` inside `onStartCommand` as proof of the original app's identity: the framework delivers the service request. Also review controller permissions against the actual Media3 version, rather than assuming all session clients should receive private commands. [Android MediaSession callback documentation](https://developer.android.com/reference/androidx/media3/session/MediaSession.Callback).

### S2 — Medium: browser downloads can consume unbounded storage and automatically enter the importer

`ui/screens/BookCatalogBrowserDialog.kt:129` accepts download callbacks and URL patterns, streams the response with unrestricted `input.copyTo(output)` at line 186, then imports it. There is no streamed byte quota, successful-response/MIME validation, free-space check or temporary-file cleanup on failure. A read timeout limits an individual blocked read, not total duration or total bytes.

The navigation callback at line 397 intercepts book-looking URLs without requiring a main-frame user gesture. It also recognizes broad `/download/` URL patterns. A malicious or redirected page can therefore trigger an unwanted download/import; an HTTPS page can still return a huge or malformed file. Download titles are sanitized, which helps prevent filename traversal; it does not limit file size.

**Fix:** validate the initial/final destination and redirects, require an appropriate user action or confirmation for downloads, validate status and supported content, enforce a running byte quota and cancellation, use unique temporary files, and clean up rejected/partial downloads. Review cookie forwarding on cross-origin redirects: the downloader manually attaches the original URL's Cookie header, while redirect handling is delegated to `HttpURLConnection`. Cross-origin cookie leakage has **not** been reproduced in this audit.

### S3 — Medium: archive/document import lacks aggregate resource limits

`DocumentExtractor.kt:271` feeds DOCX/PPTX/EPUB import paths from `readAllBytes`; the reader at line 890 has no input limit. Live byte-array parsers then use unbounded `zip.readBytes()` for XML entries, for example `EpubDocumentParser.kt:42`, `DocxDocumentParser.kt:42` and `PptxExtractor.kt:69`. Small compressed files can expand into very large memory allocations and crash the app. PPTX image extraction also has unbounded entry reads.

There are stronger limits in `OriginalArchiveEntries` for the separate original-document viewing path, and the backup restore path has its own limits. Those protections do not cover every live extraction path. Voice TAR extraction rejects links and traversal, but its downloaded and expanded byte totals are also uncapped.

**Fix:** apply compressed input, entry count, per-entry and aggregate expanded-byte budgets across every importer and voice archive path, including cancellation checks. Use bounded streams rather than first loading whole untrusted documents. This is a denial-of-service/resource-exhaustion finding; arbitrary code execution was not demonstrated.

## Browser privacy and hardening

### S4 — Low: third-party tracking cookies enabled

`BookCatalogBrowserDialog.kt:359` explicitly calls `setAcceptThirdPartyCookies(this, true)`. Third-party content can participate in tracking while the user browses archive sites. This is a privacy weakness, not evidence of a hidden backdoor.

**Fix:** disable third-party cookies by default and test archive/login compatibility before creating any necessary exception. Explicitly stop and destroy the WebView on dismissal; the current `AndroidView` has no explicit WebView release cleanup. Continuing tracking after dismissal has not been demonstrated.

### S5 — Low: broad FileProvider roots increase accidental sharing risk

`res/xml/file_paths.xml:5` maps the complete internal files and cache directories with `path="."`. The provider is correctly **not exported**, and grants are required, so this does **not** allow another app to read all private files automatically. It makes a mistaken URI grant much more consequential and exposes more paths than the sharing features need.

**Fix:** stage exports in dedicated share directories and restrict provider roots to those directories plus the required capture/export paths. Validate incoming content URIs against self-owned provider paths. Android explicitly recommends avoiding broad `.` ranges. [Android FileProvider security guidance](https://developer.android.com/privacy-and-security/risks/file-providers).

### S6 — Low / defense in depth: update files are not prevalidated by Vern

`ui/ReaderViewModelUpdates.kt:127` downloads the selected release asset, and line 225 opens Android's installer without first checking its application ID, signer, version or expected digest. The stream also has no byte quota.

**Fix:** verify that the APK belongs to Vern, has a compatible trusted signing certificate and expected version, and enforce download limits before opening the installer. Android's package installer still requires user approval and checks signatures when replacing an installed app: this audit found **no silent install mechanism**, and an APK signed by an unrelated key cannot simply overwrite Vern. Compromise of the release account/assets remains a separate trust concern; a hash hosted beside a compromised asset is not independent authentication.

## Dependency result

The [resolved debug dependency graph](docs/verification/2026-10-14/security-runtime-dependencies.txt) contains **`commons-lang3:3.14.0`**, pulled in through Commons Compress. This version is in the affected range for **CVE-2025-48924**, a stack-overflow denial of service involving `ClassUtils.getClass` on excessively long input. Apache's fix is 3.18.0. [Apache's published advisory](https://www.openwall.com/lists/oss-security/2025/07/11/1).

**Status:** affected dependency version confirmed; a reachable malicious-input path in Vern has not been established. There are no direct first-party calls to the affected `ClassUtils.getClass` method in the reviewed source. Release/R8 removal or retention was not assessed. Update to a compatible fixed version and test voice archive/import behavior; do not label the whole app remotely exploitable merely because the dependency is present.

The selected official advisory checks did **not** show these particular earlier issues affecting the resolved versions:

- Commons Compress is 1.26.1; the listed CVE-2024-25710/CVE-2024-26308 fixes are in 1.26.0. [Apache Compress security page](https://commons.apache.org/proper/commons-compress/security.html).
- Commons IO is 2.15.1; CVE-2024-47554 affects versions before 2.14.0. [Apache IO security page](https://commons.apache.org/proper/commons-io/security.html).
- PDFBox's 2026 embedded-file path traversal advisories concern its `examples` module. No copied `ExtractEmbeddedFiles` implementation was found in this app. Do not apply those example-only advisories to the Android core parser without checking the affected code. [Apache PDFBox security page](https://pdfbox.apache.org/security.html).

This is not an exhaustive CVE scan of all AndroidX/Google components or bundled native binaries.

## Protections found and backdoor checks

- The browser disables file and content access, enables Safe Browsing and forbids mixed content. Non-HTTP(S) navigation is blocked. No JavaScript bridge, TLS-certificate bypass or permissive hostname verifier was found. JavaScript for ordinary website functionality is enabled; that alone does not grant websites native app permissions.
- The browser permits HTTP(S) hosts rather than restricting navigation to archive domains. This is wider browsing/phishing exposure, not automatically native code execution. Add explicit HTTPS and origin policies, show the real origin clearly, and route unrelated sites appropriately. Initial URL loading and redirects should receive the same validation. Current target/minimum SDKs make cleartext network traffic blocked by default unless a configuration overrides it; the source did not declare such an override. An HTTP-loading branch alone is therefore not proof that plaintext traffic succeeds. [Android URI-loading guidance](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading).
- API credentials use AES-GCM with a device-bound Android Keystore key. Their preference file is excluded from cloud backup and device transfer. Manual backup serialization does not include the credential store. No production provider-key or private-key literal matched the limited source patterns.
- No first-party remote dex loader, shell-command launcher, listening server socket or Java object deserialization path was found in the searches. The expected native load is Sherpa JNI for local speech inference. Hardcoded network hosts correspond to AI providers, releases/models, dictionaries, book archives and user-facing links; a user-configured AI endpoint intentionally receives its configured credential and prompt.
- The current debug APK contains AndroidX path rendering, ONNX Runtime and Sherpa JNI native libraries. Their hashes match the local stripped build outputs. The ONNX source hash differs from the packaged hash because stripping changes the binary; both values are recorded in [the inventory](docs/verification/2026-10-14/security-inventory.json). Sherpa/ONNX source hashes match the local provenance record. The upstream download was not independently re-fetched and the native binaries were not reverse-engineered.
- The Samsung's actual WebView provider is **com.google.android.webview 156.0.8078.25**, as recorded in [the device snapshot](docs/verification/2026-10-14/security-webview-provider.txt). The app's hardcoded Chrome/128 user-agent does not identify its real engine or establish an old Chromium vulnerability.

## Verification and recommended order

The reproducible [source/APK inventory script](docs/verification/2026-10-14/security_inventory.py) records only redacted credential-pattern findings. It found zero matches for its limited secret/backdoor-call patterns; absence of those patterns is not proof of absence of malicious behavior. The exported-service probe affected only the separate debug preview. No intentionally oversized archive was run on the phone.

Prioritize the application-only playback boundary and bounded, validated browser/document downloads before release. Then narrow sharing roots, change cookie defaults with compatibility checks, update the affected transitive dependency, and add APK prevalidation. Add regression tests for unauthorized commands, redirected/unwanted downloads, resource budgets and legitimate media controls when implementing those changes.

Android lint was attempted offline for this debug variant, but after more than eight minutes it remained in Kotlin/FIR semantic analysis without producing a report. The optional check was canceled; **no lint pass is claimed**. The build log and diagnostic thread snapshot are in `docs/verification/2026-10-14/security-lint-build.txt` and `security-lint-threads.txt`. Source review, the dependency graph and the external-service probe completed independently. A successful lint invocation would not itself be a security certification.
