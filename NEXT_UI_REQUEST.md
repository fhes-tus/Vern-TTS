# Home and Added — final polish completed

Updated 9 October 2026. The user asked to finish Home and My Library → Added after the Study Hub audit/Overview, then stop this update's design work.

Home now shares library progress calculation, excludes Continue reading from Recent books, and offers goal setup before the first reading session. Added has result counts/reset, clearer empty search results, larger search controls and separated tile metadata.

Validation: combined app/test assembly, 35 focused JVM tests and 8 Android tests passed. The actual Home, Added and Study Overview were inspected in the isolated `com.veritas.reader.checks` app. Goal changes survived an app restart and Home's bottom actions scroll clear of navigation. The final cosmetic corrections also compiled and were packaged successfully. Normal and preview apps were preserved.

Details: [Home/Added review](docs/HOME_ADDED_POLISH_REVIEW.md) and [Study Hub review](docs/STUDY_HUB_LOGIC_REVIEW.md). Broader acceptance of all theme combinations, weekly network/download flows and Monday rollover remains available for later testing.

The final follow-up also updates extracted-text, actual-document and native-PDF playback glass in both glass modes, restores separated Notes navigation, fixes Study/Notes Back hierarchy, supplies the requested menu icons and removes the circled Study introduction. Final app/test assembly and all 12 phone checks pass. See [glass/navigation verification](docs/GLASS_AND_NAVIGATION_FOLLOWUP.md).

The user's last animation checklist was completed as a search-only report: [animation opportunities](docs/ANIMATION_OPPORTUNITIES_REVIEW.md). Two optional status fades were identified; no animation implementation was added.

Following explicit user authorization, both status fades and playback startup feedback are implemented. Local signed release assembly, 30 focused JVM cases and all 17 phone cases are verified as of 10 October 2026. All three release APK signatures and 16 KiB alignments pass; the ARM64 APK is 51.32 MiB. See [final playback/release checkpoint](docs/PLAYBACK_FEEDBACK_RELEASE_CHECK.md).

The full Reading Insights redesign remains in [FUTURE_UI_PLAN.md](FUTURE_UI_PLAN.md). No release publication is authorized or performed.
