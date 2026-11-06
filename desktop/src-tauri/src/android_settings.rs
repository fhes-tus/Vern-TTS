//! Convert only preferences with a desktop equivalent; retain every source value in the import.
use crate::storage::{Result, Settings};
use serde_json::Value;

fn string<'a>(row: &'a Value, key: &str) -> Result<Option<&'a str>> {
    row.get(key)
        .map(|v| v.as_str().ok_or_else(|| format!("Invalid Android {key}.")))
        .transpose()
}
fn number(row: &Value, key: &str) -> Result<Option<f64>> {
    row.get(key)
        .map(|v| {
            v.as_f64()
                .filter(|n| n.is_finite())
                .ok_or_else(|| format!("Invalid Android {key}."))
        })
        .transpose()
}
fn copy_bools(row: &Value, fields: &mut [(&str, &mut bool)]) -> Result<()> {
    for (key, target) in fields {
        if let Some(v) = row.get(*key) {
            **target = v
                .as_bool()
                .ok_or_else(|| format!("Invalid Android {key}."))?;
        }
    }
    Ok(())
}
fn choice(
    row: &Value,
    key: &str,
    values: &[(&str, &str)],
    target: &mut String,
    warnings: &mut Vec<String>,
) -> Result<()> {
    if let Some(value) = string(row, key)? {
        if let Some((_, mapped)) = values.iter().find(|(source, _)| *source == value) {
            *target = (*mapped).into();
        } else {
            warnings.push(format!("Android {key} has no desktop equivalent. The current desktop preference is kept and the original remains in the retained backup."));
        }
    }
    Ok(())
}
fn theme(id: &str) -> Option<(&'static str, &'static str)> {
    Some(match id {
        "system" => ("sage", "system"),
        "light" => ("sage", "light"),
        "dark" | "default_dark_2026" | "blue_high_contrast" => ("sage", "dark"),
        "midnight_system" => ("midnight", "system"),
        "midnight_light" => ("midnight", "light"),
        "midnight_dark" => ("midnight", "dark"),
        "dracula_system" => ("dracula", "system"),
        "dracula_light" => ("dracula", "light"),
        "dracula" => ("dracula", "dark"),
        "github_system" => ("github", "system"),
        "github_light" | "one_light" => ("github", "light"),
        "github_dark" | "one_dark_pro" => ("github", "dark"),
        "bw_gradient_system" => ("bw_gradient", "system"),
        "bw_gradient_light" => ("bw_gradient", "light"),
        "bw_gradient_dark" => ("bw_gradient", "dark"),
        "neon" => ("neon", "dark"),
        "amoled" => ("amoled", "dark"),
        "dark_high_contrast" => ("dark_high_contrast", "dark"),
        "white_high_contrast" => ("white_high_contrast", "light"),
        _ => return None,
    })
}
pub(crate) fn apply(
    root: &Value,
    settings: &mut Settings,
    warnings: &mut Vec<String>,
) -> Result<()> {
    let reader = &root["readerSettings"];
    if let Some(size) = number(reader, "fontSizeSp")? {
        if size.fract() != 0.0 {
            return Err("Invalid Android fontSizeSp.".into());
        }
        if (14.0..=32.0).contains(&size) {
            settings.font_size = size as u32;
        } else {
            warnings.push("The Android text size is outside the desktop range. Its original value is retained; the current desktop size is kept.".into());
        }
    }
    if let Some(goal) = number(reader, "dailyGoalMinutes")? {
        if goal.fract() != 0.0 || !(0.0..=180.0).contains(&goal) {
            return Err("Invalid Android dailyGoalMinutes.".into());
        }
        settings.daily_goal_minutes = goal as u32;
    }
    copy_bools(
        reader,
        &mut [
            ("reduceMotion", &mut settings.reduce_motion),
            ("reduceTransparency", &mut settings.reduce_transparency),
            ("bionicReading", &mut settings.bionic_reading),
            ("showSectionNumbers", &mut settings.show_section_numbers),
        ],
    )?;
    if let Some(accent) = string(reader, "customAccentColor")? {
        let value = accent.trim();
        let hex = value.strip_prefix('#').unwrap_or(value);
        if accent.is_empty() {
            settings.accent_color.clear();
        } else if hex.len() == 6 && hex.bytes().all(|b| b.is_ascii_hexdigit()) {
            settings.accent_color = format!("#{}", hex.to_ascii_uppercase());
        } else {
            warnings.push("The Android accent is not an opaque RGB colour. The current desktop accent is kept; the original remains in the retained backup.".into());
        }
    }
    if let Some(id) = string(reader, "themeId")? {
        if let Some((family, mode)) = theme(id) {
            settings.color_theme = family.into();
            settings.theme = mode.into();
        } else {
            warnings.push("The Android theme has no desktop equivalent. The current desktop theme is kept; the original remains in the retained backup.".into());
        }
    }
    choice(
        reader,
        "paperToneMode",
        &[
            ("active_theme", "theme"),
            ("natural_white", "white"),
            ("warm_sepia", "sepia"),
            ("dark", "dark"),
        ],
        &mut settings.paper_tone,
        warnings,
    )?;
    choice(
        reader,
        "readingLayout",
        &[("pages", "single"), ("scroll", "scroll")],
        &mut settings.reader_layout,
        warnings,
    )?;
    choice(
        reader,
        "uiFontId",
        &[
            ("gazette", "Bitter"),
            ("bitter", "Bitter"),
            ("literata", "Bitter"),
            ("lora", "Bitter"),
            ("crisp", "Outfit"),
            ("outfit", "Outfit"),
            ("atkinson", "Atkinson"),
            ("typewriter", "Times New Roman"),
            ("jetbrains_mono", "Times New Roman"),
            ("times", "Times New Roman"),
            ("serif", "Times New Roman"),
            ("system", "Times New Roman"),
        ],
        &mut settings.reader_font,
        warnings,
    )?;
    if reader.get("uiFontId").is_some() {
        warnings.push("Desktop typeface choices affect reading text. Navigation keeps its application font; system serif fonts may differ between devices.".into());
    }
    let notes = &root["notesSettings"];
    choice(
        notes,
        "paperTemplate",
        &[
            ("BLANK", "blank"),
            ("RULED", "ruled"),
            ("GRID", "grid"),
            ("DOTS", "dots"),
        ],
        &mut settings.notes.paper,
        warnings,
    )?;
    choice(
        notes,
        "defaultSortOrder",
        &[
            ("date", "updated"),
            ("created", "created"),
            ("title", "title"),
        ],
        &mut settings.notes.sort,
        warnings,
    )?;
    if let Some(scale) = number(notes, "editorFontScale")? {
        if !(0.85..=1.25).contains(&scale) {
            return Err("Invalid Android editorFontScale.".into());
        }
        settings.notes.font_scale = scale as f32;
    }
    if let Some(lines) = number(notes, "previewLines")? {
        if lines.fract() != 0.0 || !(0.0..=12.0).contains(&lines) {
            return Err("Invalid Android previewLines.".into());
        }
        settings.notes.preview_lines = lines as u32;
    }
    copy_bools(
        notes,
        &mut [
            ("newNoteIsChecklist", &mut settings.notes.new_checklist),
            ("moveCheckedToBottom", &mut settings.notes.checked_to_bottom),
            ("isGridView", &mut settings.notes.grid),
        ],
    )?;
    // Android editorLineSpacing multiplies a platform text-style baseline; desktop stores a
    // CSS line-height ratio. Do not silently equate those different quantities.
    if notes.get("editorLineSpacing").is_some() {
        warnings.push("Android note line spacing uses a different text baseline. The current desktop spacing is kept; the original remains in the retained backup.".into());
    }
    choice(
        &root["askAiSettings"],
        "assistantId",
        &[
            ("chatgpt", "chatgpt"),
            ("gemini", "gemini"),
            ("claude", "claude"),
            ("copilot", "copilot"),
            ("perplexity", "perplexity"),
        ],
        &mut settings.ai_assistant,
        warnings,
    )?;
    if root["askAiSettings"].get("promptTemplate").is_some()
        || root["askAiSettings"].get("packageName").is_some()
    {
        warnings.push("Android AI prompt templates and application package choices remain in the retained backup; desktop conversion is pending.".into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;
    #[test]
    fn supported_preferences_persist_and_unknown_values_do_not_replace_live_choices() {
        let mut settings = Settings::default();
        let mut warnings = Vec::new();
        apply(&json!({"readerSettings":{"fontSizeSp":24,"dailyGoalMinutes":30,"reduceMotion":true,"reduceTransparency":true,"bionicReading":true,"showSectionNumbers":true,"customAccentColor":"34ac56","themeId":"dracula_system","paperToneMode":"warm_sepia","readingLayout":"scroll","uiFontId":"gazette"},"notesSettings":{"paperTemplate":"DOTS","editorFontScale":1.2,"editorLineSpacing":1.5,"previewLines":0,"newNoteIsChecklist":true,"moveCheckedToBottom":false,"isGridView":false,"defaultSortOrder":"created"},"askAiSettings":{"assistantId":"claude"}}), &mut settings, &mut warnings).unwrap();
        let mut db = crate::storage::Storage::open(std::path::Path::new(":memory:")).unwrap();
        db.save_settings(settings.clone()).unwrap();
        let saved = db.settings().unwrap();
        assert_eq!((saved.font_size, saved.daily_goal_minutes), (24, 30));
        assert!(
            saved.reduce_motion
                && saved.reduce_transparency
                && saved.bionic_reading
                && saved.show_section_numbers
        );
        assert_eq!(
            (&*saved.color_theme, &*saved.theme, &*saved.accent_color),
            ("dracula", "system", "#34AC56")
        );
        assert_eq!(
            (
                &*saved.reader_font,
                &*saved.reader_layout,
                &*saved.paper_tone
            ),
            ("Bitter", "scroll", "sepia")
        );
        assert_eq!(
            (
                &*saved.notes.paper,
                saved.notes.preview_lines,
                &*saved.notes.sort
            ),
            ("dots", 0, "created")
        );
        assert!(saved.notes.new_checklist && !saved.notes.checked_to_bottom && !saved.notes.grid);
        assert_eq!(saved.notes.font_scale, 1.2);
        assert_eq!(saved.notes.line_spacing, 1.5);
        assert_eq!(saved.ai_assistant, "claude");
        assert!(warnings
            .iter()
            .any(|v| v.contains("different text baseline")));
        apply(&json!({"readerSettings":{"themeId":"future-theme","uiFontId":"future-font","paperToneMode":"future-paper","readingLayout":"future-layout","customAccentColor":"#12345678"},"notesSettings":{"paperTemplate":"FUTURE"},"askAiSettings":{"assistantId":"chooser"}}), &mut settings,&mut warnings).unwrap();
        assert_eq!(
            serde_json::to_value(&settings).unwrap(),
            serde_json::to_value(saved).unwrap()
        );
        apply(
            &json!({"readerSettings":{},"notesSettings":{}}),
            &mut settings,
            &mut warnings,
        )
        .unwrap();
        assert!(settings.reduce_motion && settings.notes.new_checklist);
    }
    #[test]
    fn theme_aliases_font_aliases_and_malformed_preferences_are_checked() {
        for (source, family, mode) in [
            ("one_light", "github", "light"),
            ("default_dark_2026", "sage", "dark"),
            ("white_high_contrast", "white_high_contrast", "light"),
            ("bw_gradient_system", "bw_gradient", "system"),
            ("neon", "neon", "dark"),
        ] {
            assert_eq!(theme(source), Some((family, mode)));
        }
        for (source, font) in [
            ("system", "Times New Roman"),
            ("typewriter", "Times New Roman"),
            ("crisp", "Outfit"),
            ("atkinson", "Atkinson"),
        ] {
            let mut settings = Settings::default();
            apply(
                &json!({"readerSettings":{"uiFontId":source}}),
                &mut settings,
                &mut vec![],
            )
            .unwrap();
            assert_eq!(settings.reader_font, font);
        }
        for root in [
            json!({"readerSettings":{"reduceMotion":"true"}}),
            json!({"readerSettings":{"dailyGoalMinutes":181}}),
            json!({"notesSettings":{"previewLines":1.5}}),
            json!({"notesSettings":{"editorFontScale":5}}),
            json!({"readerSettings":{"readingLayout":false}}),
        ] {
            assert!(apply(&root, &mut Settings::default(), &mut vec![]).is_err());
        }
    }
}
