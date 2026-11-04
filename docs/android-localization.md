# Android interface languages

First preview implementation: 4 November 2026. The user selected French, Spanish, Hindi, Chinese and Arabic; Chinese uses Simplified Chinese (`zh-Hans`) in this first pack. English remains the complete resource fallback. These are preview translations, without speaker review.

## Ownership and behavior

- Android/AndroidX owns the app-language preference and its persistence. `AppLanguages` delegates to `AppCompatDelegate`, with `autoStoreLocales` for Android 9–12 and synchronization with Android 13+ per-app language settings. There is no second app-language value in the document/settings database.
- Settings → AI & Language → App language opens a radio picker. It lists “Use device language,” English, Français, Español, हिन्दी, 简体中文 and العربية. Apply commits a choice; Cancel leaves it unchanged. Resetting to device language supplies an empty locale list. Names use their own scripts, without country flags.
- The picker warns that preview translations include English fallback. The framework recreates the activity on a language change. MainActivity and WidgetMenuActivity use AppCompatActivity; the existing PDF activity already does.
- Document bytes/text, IDs, anchors, note content and voice preferences are not translated or rewritten. Speech defaults resolve independently of the interface locale, using the engine's default voice or the system resources locale. This avoids treating an Arabic/French menu preference as a new narration language.

## Resource layout

- `app/src/main/res/values/strings.xml`: pre-existing Android messages/widgets.
- `values/interface_strings.xml`: English interface strings extracted from Compose text and icon descriptions, plus the language picker and Home formatting strings.
- `values-fr`, `values-es`, `values-hi`, `values-b+zh+Hans`, `values-ar`: preview translations. The same locale folders contain `reading_plurals.xml`.
- `xml/app_locales.xml`: advertised interface languages; `AppLanguageCatalog` supplies the native names and normalizes region variants. Traditional Chinese is not labelled as Simplified Chinese.
- `AppLanguageDialog.kt`: selectable radio rows, whole-row touch targets, scrollable contents and native-name text direction. This follows Vern's existing settings typography and Material controls.

Resource IDs are stable. Translate whole messages with positional format arguments; do not translate document-format identifiers, internal enum identities, provider IDs, paths, URLs or preference values. Literal percent signs in formatted messages use `%%`. Preserve Android escaping and meaningful spaces. The default English resource is required for every translated key.

The initial extraction moved 1,553 static UI occurrences into 1,153 distinct English resources across 111 files. It deliberately leaves dynamic Kotlin templates, remembered models, non-Compose background messages and stored labels for subsequent explicit migrations. Navigation enum identities remain stable; navigation and Added/Classics display labels use resource IDs. Home greetings, resume progress, goal selection and changing reading statistics have explicit localized messages and plurals. Reading minute/day plurals include Arabic zero/one/two/few/many/other forms. Debug builds enable Android pseudolocales.

Each pack initially contained 331 translated strings. After the connected-device review and the user's report of English subsettings, each now contains 768 translated strings and five plural groups. This includes the audited settings-page resources, custom settings controls, fixed legacy option labels, voice presets, PDF import modes, accessibility, Notes preferences, storage totals, backup controls and formatted settings messages. Fixed catalogue labels are translated at display time; stored settings, engine/provider names, font/theme brand names and user-entered content retain their identities.

The continuation expanded each pack to 2,022 translated strings: every declared translatable string now has all five translations. The remaining default key is the deliberately untranslated brand fragment. It also migrates 219 dynamic interface messages, fixed catalogue labels and 74 Glance widget occurrences. Display numbers use the selected locale; document text and stored option identities remain intact. A coverage test prevents newly declared messages from silently falling back in these five packs.

This remains a preview, rather than complete app translation: long-form help articles, backend/provider error strings, playback status messages and some notification text still originate outside resources and can appear in English. Widget resource access is now localized, but launcher rendering and updates across Android versions need native acceptance. English fallback is stated in the picker. Do not advertise fully reviewed language coverage from this preview.

## Reading across scripts

The selectable text-flow TextView explicitly uses first-strong text direction with an LTR neutral fallback and text-start alignment. Thus document paragraphs determine their direction independently of Arabic app chrome. Imported text and existing UTF-16 position anchors are preserved. Android font fallback and shaping are used; no new font package or reindexing/migration was introduced.

This is not acceptance evidence for every script. Physical-device checks are still needed for Arabic mixed-direction selections, Hindi shaping, Chinese line wrapping, font coverage, search, pagination, original-document views and narration highlights. Interface translation does not imply voice-model language support; the shipped Studio/Lite catalogue remains a separate feature.

## Verification and continuation

JVM checks live in `AppLanguageCatalogTest.kt` and `AppLocalizationResourcesTest.kt`. They verify region normalization, unsupported/Traditional Chinese handling, English fallback keys, duplicate/empty translations, positional format arguments, locale registration and required plural forms. `AppLanguageResourcesInstrumentedTest.kt` checks isolated native resource configurations without changing the phone's language or saved app preference; it needs an Android device/emulator to execute.

Run the repository's normal Android build, JVM tests and lint. On this host the wrapper initially attempted a blocked Gradle download, so verification used the existing Gradle 9.4.1 distribution directly with Android Studio JBR. Exact commands and observed results are recorded in `progress.md`. No connected Android device or configured emulator was available at the initial device inventory; no browser result establishes native behavior.

Final host verification passed debug APK assembly, 668 JVM tests (102 suites, no failures/errors) and compilation of the Android instrumentation tests. An English-literal round-trip check passed for 104 migration-only source files; seven intentionally changed files were reviewed separately. Both broad lint and a narrowed resource-lint retry were stopped after prolonged Kotlin/UAST analysis, so there is no completed lint result. APK configuration inspection confirms the five new locale configurations and debug pseudolocales; it does not verify rendered screens or native language switching.

The user subsequently connected a Samsung SM-A175F running Android 16/API 36. The separate preview app and its test APK were installed without clearing data or replacing the primary app. The native resource test passed, and actual picker selections were verified for French, Spanish, Hindi, Simplified Chinese and Arabic against Android's saved app locales. French and Arabic survived process stop and cold launch. Cancel preserved Arabic, and resetting to device language cleared the override. Screenshots at the phone's existing 0.8 font scale showed the picker in all five languages and Arabic right-to-left chrome. That review found a reversed Latin wordmark and book-metadata direction issues, which were corrected at their display boundaries. Final expanded-settings build/device results are recorded in `progress.md`.

`settingsSubpageResourcesHaveAllFiveTranslations` now prevents the audited settings sources and their fixed-option display helper from referring to untranslated string resources. `settings_strings.xml`, `settings_options.xml`, `settings_templates.xml` and `LocalizedSettingsOptions.kt` supply the new custom-control messages, fixed catalogue labels and complete formatted messages. This contract does not detect every possible English string in arbitrary runtime models or external provider messages.

After the subsettings expansion, debug assembly, all 669 JVM tests and instrumentation APK assembly passed. The updated native resource test passed again on the phone (one test covering all six locale contexts). The actual Accessibility subpage was checked in French, Spanish, Hindi, Simplified Chinese and Arabic using Android app-locale changes, hierarchy assertions and screenshots; the translated group labels appeared in all five. Spanish voice presets and Hindi display/theme titles and descriptions were also checked. The preview was returned to device language, with its picker left open. These checks used the phone's existing font scale, not a large-font accessibility sweep. Full Android lint and broader native reading/narration acceptance remain open.

Next native acceptance should change a new and an existing profile through every language, relaunch the app, reset to device language, change the preference in Android Settings, and repeat on Android 12 and Android 13+. Check narrow screens and large fonts, long French/Spanish labels, Arabic navigation/focus order, saved reading position, note drafts, system/native speech, background playback and notifications/widgets. Run English XA and Arabic XB pseudolocales to find remaining literals and layout clipping. Human review remains deferred at the user's request.

Extend the preview by migrating dynamic labels at their rendering boundaries and adding corresponding translations. Never translate comparison keys or persisted values merely because they are English. Keep format and locale-contract tests passing; record tested devices separately from compilation and JVM checks.

Official references: [per-app languages](https://developer.android.com/guide/topics/resources/app-languages), [Compose resources](https://developer.android.com/develop/ui/compose/resources), [pseudolocales](https://developer.android.com/guide/topics/resources/pseudolocales).
