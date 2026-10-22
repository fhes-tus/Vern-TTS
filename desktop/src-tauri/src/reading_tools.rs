use crate::storage::Result;
use regex::RegexBuilder;
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PronunciationRule {
    pub id: String,
    pub find: String,
    pub replace_with: String,
    pub enabled: bool,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Vocabulary {
    pub id: String,
    pub word: String,
    pub definition: String,
    pub book_id: Option<String>,
    pub position: Option<usize>,
}

pub fn apply_pronunciation(source: &str, rules: &[PronunciationRule]) -> Result<String> {
    let word = regex::Regex::new(r"[\p{L}\p{M}\p{N}_'’]").map_err(|e| e.to_string())?;
    let is_word = |c: char| word.is_match(&c.to_string());
    let mut edits = Vec::new();
    // Android's literal, case-insensitive phrase rules, longest match first at each
    // source position. Boundaries are checked separately because Rust regex has no lookbehind.
    for rule in rules
        .iter()
        .filter(|r| r.enabled && !r.find.trim().is_empty())
    {
        let find = rule.find.trim();
        let body = find
            .split(|c: char| c == '\t' || c.is_whitespace())
            .filter(|s| !s.is_empty())
            .map(regex::escape)
            .collect::<Vec<_>>()
            .join(r"[\p{Zs}\t]+");
        let pattern = RegexBuilder::new(&body)
            .case_insensitive(true)
            .size_limit(1024 * 1024)
            .build()
            .map_err(|e| e.to_string())?;
        let starts_word = find
            .chars()
            .next()
            .is_some_and(|c| c.is_alphanumeric() || c == '_');
        let ends_word = find
            .chars()
            .last()
            .is_some_and(|c| c.is_alphanumeric() || c == '_');
        for matched in pattern.find_iter(source) {
            if starts_word
                && source[..matched.start()]
                    .chars()
                    .next_back()
                    .is_some_and(is_word)
            {
                continue;
            }
            if ends_word && source[matched.end()..].chars().next().is_some_and(is_word) {
                continue;
            }
            if edits.len() >= 20_000 {
                return Err("Too many pronunciation matches in this passage.".into());
            }
            edits.push((matched.start(), matched.end(), rule.replace_with.as_str()));
        }
    }
    edits.sort_by(|a, b| a.0.cmp(&b.0).then_with(|| (b.1 - b.0).cmp(&(a.1 - a.0))));
    let mut cursor = 0;
    let mut output = String::new();
    for (start, end, replacement) in edits {
        if start < cursor {
            continue;
        }
        output.push_str(&source[cursor..start]);
        output.push_str(replacement);
        cursor = end;
        if output.len() > 4096 {
            return Err("Pronunciation replacements make this passage too long. Shorten the replacement text.".into());
        }
    }
    output.push_str(&source[cursor..]);
    if output.len() > 4096 {
        return Err(
            "Pronunciation replacements make this passage too long. Shorten the replacement text."
                .into(),
        );
    }
    Ok(output)
}
