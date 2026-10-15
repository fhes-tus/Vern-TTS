# APK and Google Play updates

Checked directly in Play Console and with Android `apksigner` on 15 October 2026. Vern uses package `com.veritas.reader` in both production distribution routes; debug previews have a separate suffix.

| Certificate | SHA-256 |
| --- | --- |
| Current Play app signing key | `C54A80058D6D9142C1179818DE7CB206BF0119E246403BEAC5FFA23686297E45` |
| Play upload key and locally built 2.6.1 APK | `3B143A77A70253305A496595432461B018FFCB3792E82A96905BD10243424DB8` |

These are different keys. Signing a bundle and APK with the same local keystore proves the upload identity, but Play signs its distributed APKs with its app signing key. This explains the observed signature conflict. A lower version alone is not enough to permit a cross-source update.

For future compatible distribution, upload the authorized bundle normally, then obtain its **Google Play-signed universal APK** from the selected bundle's Downloads page in Play Console. Publish that APK on GitHub and the website. Verify the actual package, version code and signer before publication. `tools/check_apk_distribution.ps1` rejects upload-key APKs when checked for the Play channel. Keep any separate legacy APK channel explicitly labeled.

Existing installations signed with the upload key need their own migration decision. Changing a source setting, upload key or version name cannot make Android accept an unrelated certificate. A supported signing lineage would need separate platform and Play validation; none has been established between these two identities. Do not uninstall users' apps as a test. Export a portable backup and externally saved originals before any user-approved uninstall or migration.

The in-app updater checks package name, newer version code and compatible verified signing history before launching Android's installer. A mismatch is reported without uninstalling the current app. Android still performs its own final verification and requires installation consent.

Current source is 2.6.2 / code 42. Local release assembly is authorized. Play upload and rollout remain pending. Compare the eventual code with the latest uploaded Play build and verify track eligibility before release. Internal App Sharing uses its own signing mechanism and should not be used to establish normal track compatibility.

References: [Google Play app signing and cross-store APK distribution](https://support.google.com/googleplay/android-developer/answer/9842756), [Android app signing](https://developer.android.com/studio/publish/app-signing).

## Selected release route

The owner selected: **build the AAB → upload to Play Console → download its Play-signed universal APK → publish that APK on GitHub**. Do not publish locally signed ABI APKs alongside it as interchangeable update assets; automatic asset selection could otherwise select the incompatible key again. Local APKs are retained for testing the legacy installation channel.

Play Console currently shows closed and internal testing active, with open testing and production inactive. Google's documentation permits changing the app signing key before an open-testing or production rollout, but the accessed account displays an administrator-permission requirement. Any such change would need the owner/admin and separate validation of affected closed-test users. No Play signing key was changed in this task.
