# Vern 2.7.1

Android version name **2.7.1**, version code **46**.

- Repaired minified release startup and the shared WorkManager input-merger construction failure that caused imports to report an unknown error before extraction began. Samsung API 36 checks confirm TXT, PDF and image OCR imports, retained imported files after APK replacement, and Reading Tracker widget rendering. Local verification does not publish an update.

- Completed books stay below unfinished books in Library, with the selected sort preserved within each group. This also applies to filtered, searched and queued Library results. Playback queue order itself is unchanged. Manual dragging stays within the finished or unfinished group.
- Display & theme now offers a visual accent picker with a colour field, rainbow hue strip, quick swatches and a small live circle preview. Apply, Cancel and a reset to theme colours keep you in control. The choice persists with reader settings and their backups.
- Custom accents supply buttons, highlights and matching container tints across Vern Media, Material You, Liquid Glass and One UI. Accent fills and vibrant reading cards keep the exact chosen colour, with contrasting text. The selected colour theme continues to control light, dark and system following; AMOLED keeps black backgrounds.
- App chrome, native PDF reader chrome and appearance previews use the shared accent scheme. High-contrast presets retain their accessibility colours while the custom choice remains saved for later use.
- The pronunciation rules page has a roomier layout, grouped Text/Pattern/Regex and Skip/Say instead controls, balanced example buttons, expandable help and a labelled spoken-result preview with a Listen action.

Verification commands, results and tested platforms are recorded in [progress.md](progress.md). This source change does not publish a release.
