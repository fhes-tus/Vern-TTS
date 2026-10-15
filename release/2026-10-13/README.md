# Vern Play release pack - 13 October 2026

## Upload-ready assets
- text/play-console-paste.txt: tagged en-US release notes; 390 / 500 Unicode characters (tags excluded).
- text/release-notes-en-US.txt: plain release notes.
- graphics/app-icon-512.png: new big-head cream V on indigo, 512 x 512 PNG, square with no baked-in corner mask.
- graphics/feature-graphic-1024x500.png: 1024 x 500 RGB PNG, no alpha.
- text/: title (4/30), short description (58/80), full description (1027/4000).

## Supporting material
- existing-screenshots/: four carried-forward screenshots from the project's existing store set. These are not new captures of this build; review them against the final app before uploading. New home/navigation fixture captures are under verification, not store screenshots.
- verification/: focused UI captures and implementation notes.

## Release handoff
Source configuration is versionName 2.6.1, versionCode 41. No bundle is included or built for this pack. When preparing the bundle, its versionCode must exceed the latest uploaded Play build. Build-generated mapping/native-symbol files must come from that exact release build; do not reuse older files.
Keep the existing Play Console support contact, privacy-policy URL, content rating and data-safety answers unless the final release changes them. The repository privacy policy currently contains a placeholder support address; confirm a real contact before publishing that document. No account declarations or rollout settings have been submitted.

## Official specifications checked
- Release notes: https://support.google.com/googleplay/android-developer/answer/9859348
- Store assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Icon: https://developer.android.com/distribute/google-play/resources/icon-design-specifications
