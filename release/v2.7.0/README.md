# Vern Play release pack - v2.7.0

## Upload-ready assets
- text/play-console-paste.txt: tagged en-US release notes; 469 / 500 Unicode characters (tags excluded).
- text/release-notes-en-US.txt: plain release notes.
- graphics/app-icon-512.png: new big-head cream V on indigo, 512 x 512 PNG, square with no baked-in corner mask.
- graphics/feature-graphic-1024x500.png: 1024 x 500 RGB PNG, no alpha.
- text/: title (4/30), short description (59/80), full description (2034/4000).

## Supporting material
- existing-screenshots/: four carried-forward screenshots from the project's existing store set. These are not new captures of this build; review them against the final app before uploading. New home/navigation fixture captures are under verification, not store screenshots.
- verification/: Samsung debug UI captures, focused phone test results and release verification. These are verification fixtures, not store screenshots.
- builds/: signed app bundle and universal/ARM64/32-bit release APKs.
- symbols/: exact-build APK and bundle R8 mappings and available native symbols.

## Release handoff
Source configuration is versionName 2.7.0, versionCode 45. This script prepares listing assets only. When release builds are authorized and complete, place the signed bundle/APKs under builds/ and retain verification, SHA-256 hashes and separate exact-build APK/bundle mappings. Before uploading, compare versionCode 45 with the latest uploaded Play build; Play requires a higher code for a new upload. No publication is performed by this script. Local APKs use the upload key, which differs from the Play app signing key; use Play-signed APKs for cross-source compatibility and run tools/check_apk_distribution.ps1. See docs/UPDATE_COMPATIBILITY.md.
Review the Play Console support contact, privacy-policy URL, content rating and Data safety answers against this release. The policy uses myreader.veritas@gmail.com and discloses optional online features and ML Kit SDK diagnostics. No account declarations or rollout settings have been submitted.

## Official specifications checked
- Release notes: https://support.google.com/googleplay/android-developer/answer/9859348
- Store assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Icon: https://developer.android.com/distribute/google-play/resources/icon-design-specifications