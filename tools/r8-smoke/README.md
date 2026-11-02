# Minified Android release checks

The isolated target uses production R8 rules. The standalone framework probe
adds no app-symbol dependencies or extra target keep rules.

Requires an Android SDK, Android Studio JBR and the existing local release
signing configuration. The probe and target must have matching certificates.
Run one instrumentation session at a time with the phone unlocked.

From the repository root in PowerShell:

~~~powershell
./gradlew.bat --init-script tools/r8-smoke/release-app.init.gradle :app:assembleRelease --max-workers=1 --no-parallel
./tools/r8-smoke/build-framework-checks.ps1
adb install --user 0 -r .artifacts/android-release-checks/build/app/outputs/apk/release/app-arm64-v8a-release.apk
adb install --user 0 -r -t .artifacts/android-release-checks/framework/release-import-checks.apk
adb shell am force-stop com.veritas.reader.r8.harness.checks
adb shell am instrument -w -r com.veritas.reader.releasechecks.test/com.veritas.reader.releasechecks.ReleaseImportChecks
~~~

Read the emitted PASS/FAIL text; the shell exit code alone does not establish
success. The probe verifies reflective Room/input-merger/worker constructors,
new successful WorkManager imports for TXT/PDF/image OCR, distinct extracted
content and persisted document rows. It adds fixture books to the isolated
library. It never targets the normal com.veritas.reader package.

After replacing only the isolated APK with install -r, repeat with
-e verifyUpgrade true. For a focused retention/widget check, add
-e formats none -e verifyWidget true and -e retainedIds with the three earlier
fixture IDs separated by commas. This verifies their rows/content files and
Reading Tracker RemoteViews, then removes only its temporary widget host.
Same-code APK replacement is not an older-version schema migration test.

Generated builds and probe binaries stay under ignored .artifacts/. Keep
source, signing keys and the normal app's data. These checks were exercised on
Samsung API 36; they do not establish all formats, devices or audio quality.

For a separately built main release bundle, the read-only bundletool validation
task uses the AGP-supplied library:

~~~powershell
./gradlew.bat --init-script tools/r8-smoke/validate-bundle.init.gradle validateLocalReleaseBundle
~~~
