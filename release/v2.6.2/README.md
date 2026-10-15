# Vern 2.6.2 release pack

Version **2.6.2**, build **42**, package `com.veritas.reader`. Android 9+; target SDK 36. App source is commit `4bdcb1d`.

## Verified local builds

- `builds/upload/Vern-v2.6.2-upload.aab`: signed upload bundle.
- `builds/local-apks/Vern-v2.6.2-universal-release.apk`: locally signed universal APK generated from that exact AAB with bundletool 1.18.3. Supports ARM64 and 32-bit ARM.
- `symbols/bundle/mapping.txt`: exact R8 mapping for both artifacts. The APK comes from this bundle, so there is one shared mapping. Native symbols are embedded in the AAB.
- `release-manifest.json` and `SHA256SUMS.txt`: artifact sizes, hashes, build identity and signing identities.
- `verification/`: final build, signature, alignment and Samsung startup evidence. See `RELEASE_VERIFICATION.md` for scope and remaining checks.

Earlier direct APK builds and their mappings were moved to the recoverable milestone archive after final copy corrections. They are not the current release candidates.

## Publication handoff

No Play upload, rollout or public GitHub app release was performed. The local APK uses the existing upload/legacy APK key; it updates compatible existing APK installs, including the main Vern app tested on the Samsung.

For the agreed future distribution route, upload the AAB to Play Console, download the **Play-signed universal APK**, then use that APK for GitHub downloads. Validate it with `tools/check_apk_distribution.ps1`. This aligns future Play/GitHub installs; existing installations signed with the older APK key still need a migration decision. See `docs/UPDATE_COMPATIBILITY.md`.

Before submission, check build 42 against the highest uploaded Play version code, review Data safety against the bundled ML Kit SDKs and optional online features, and confirm permission declarations and store metadata. The privacy support address is `myreader.veritas@gmail.com`. No Console declarations were changed.

## Listing material

- `text/play-console-paste.txt`: tagged en-US release notes, 454/500 characters excluding tags.
- `text/release-notes-en-US.txt`: plain notes; full project changes are in `RELEASE_NOTES_v2.6.2.md` at the repository root.
- `graphics/app-icon-512.png`: square Play icon without a baked-in mask.
- `graphics/feature-graphic-1024x500.png`: feature graphic.
- `existing-screenshots/`: historical store screenshots, not fresh captures of this build; review before submission.

The website and bot retain the currently published 2.6.1 download links until a new public app release exists.
