use crate::storage::{error, hex_digest, Result};
use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    fs::{self, File},
    io::{BufRead, BufReader, Read},
    path::{Path, PathBuf},
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    time::Duration,
};
use unicode_normalization::UnicodeNormalization;
use zip::ZipArchive;

pub const PACK_ID: &str = "oewn-2025";
pub const PACK_TITLE: &str = "English · Open English WordNet 2025";
pub const PACK_URL: &str = "https://en-word.net/static/english-wordnet-2025.zip";
pub const PACK_SHA256: &str = "38b16326159f51853626b7d24a44c453fa88ab33f06fce5ec8fc5996d1c2be93";
pub const DOWNLOAD_BYTES: u64 = 9_616_299;
pub const SOURCE_URL: &str = "https://en-word.net/";
pub const LICENSE_URL: &str = "https://creativecommons.org/licenses/by/4.0/";
pub const CREDIT: &str =
    "Open English WordNet Community, derived from Princeton WordNet. CC BY 4.0. Data converted and indexed for Vern.";

const MAX_RECORD_LENGTH: usize = 65_536;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictionarySense {
    pub part_of_speech: String,
    pub definition: String,
    pub examples: Vec<String>,
    pub synonyms: Vec<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictionaryEntry {
    pub query: String,
    pub headword: String,
    pub senses: Vec<DictionarySense>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictionaryPackInfo {
    pub id: String,
    pub title: String,
    pub words: u32,
    pub bytes: u64,
    pub source: String,
    pub license: String,
    pub credit: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictionaryWordPage {
    pub words: Vec<String>,
    pub has_more: bool,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DictionaryDownloadState {
    pub is_downloading: bool,
    pub progress: u32,
    pub error: Option<String>,
}

pub struct Synset {
    pub offset: i64,
    pub pos: String,
    pub words: Vec<String>,
    pub gloss: String,
}

pub struct WordNetIndex {
    pub lemma: String,
    pub pos: String,
    pub offsets: Vec<i64>,
}

pub fn normalize_query(value: &str) -> Option<String> {
    let replaced: String = value
        .chars()
        .map(|c| {
            if c == '’' {
                '\''
            } else if c == '_' {
                ' '
            } else {
                c
            }
        })
        .collect();
    let nfc: String = replaced.nfc().collect();
    let trimmed = nfc.trim();
    if trimmed.is_empty() {
        return None;
    }
    let mut clean = String::with_capacity(trimmed.len());
    let mut last_was_space = false;
    for c in trimmed.chars() {
        if c.is_control() {
            return None;
        }
        if c.is_whitespace() {
            if !last_was_space {
                clean.push(' ');
                last_was_space = true;
            }
        } else {
            for lc in c.to_lowercase() {
                clean.push(lc);
            }
            last_was_space = false;
        }
    }
    if (1..=80).contains(&clean.len()) {
        Some(clean)
    } else {
        None
    }
}

pub fn parse_synset(line: &str, expected_pos: &str) -> Option<Synset> {
    if line.is_empty() || line.starts_with(|c: char| c.is_whitespace()) {
        return None;
    }
    if line.len() > MAX_RECORD_LENGTH {
        return None;
    }
    let separator = line.find('|')?;
    if separator == 0 {
        return None;
    }
    let header = line[..separator].trim();
    let fields: Vec<&str> = header.split_whitespace().collect();
    if fields.len() < 6 {
        return None;
    }
    let pos = if fields[2] == "s" { "a" } else { fields[2] };
    if pos != expected_pos {
        return None;
    }
    let offset: i64 = fields[0].parse().ok()?;
    if offset < 0 {
        return None;
    }
    let count = usize::from_str_radix(fields[3], 16).ok()?;
    if !(1..=256).contains(&count) || fields.len() < 4 + count * 2 {
        return None;
    }
    let mut words = Vec::with_capacity(count);
    for i in 0..count {
        let raw = fields[4 + i * 2];
        let stripped = raw
            .trim_end_matches("(a)")
            .trim_end_matches("(p)")
            .trim_end_matches("(ip)");
        let clean = stripped.replace('_', " ");
        if clean.is_empty() || clean.len() > 256 {
            return None;
        }
        words.push(clean);
    }
    let gloss = line[separator + 1..].trim();
    if gloss.is_empty() {
        return None;
    }
    Some(Synset {
        offset,
        pos: pos.to_string(),
        words,
        gloss: gloss.to_string(),
    })
}

pub fn parse_index(line: &str, expected_pos: &str) -> Option<WordNetIndex> {
    if line.is_empty() || line.starts_with(|c: char| c.is_whitespace()) {
        return None;
    }
    if line.len() > MAX_RECORD_LENGTH {
        return None;
    }
    let fields: Vec<&str> = line.split_whitespace().collect();
    if fields.len() < 7 || fields[1] != expected_pos {
        return None;
    }
    let synset_count: usize = fields[2].parse().ok()?;
    let pointer_count: usize = fields[3].parse().ok()?;
    if !(1..=256).contains(&synset_count)
        || pointer_count > 64
        || fields.len() != 6 + pointer_count + synset_count
    {
        return None;
    }
    let lemma = normalize_query(fields[0])?;
    let mut offsets = Vec::with_capacity(synset_count);
    for raw in &fields[6 + pointer_count..] {
        let offset: i64 = raw.parse().ok()?;
        if offset < 0 {
            return None;
        }
        offsets.push(offset);
    }
    Some(WordNetIndex {
        lemma,
        pos: expected_pos.to_string(),
        offsets,
    })
}

pub fn build_sense(pos: &str, gloss: &str, words: &[String], headword: &str) -> DictionarySense {
    let part_of_speech = match pos {
        "n" => "noun",
        "v" => "verb",
        "a" => "adjective",
        "r" => "adverb",
        other => other,
    }
    .to_string();

    let mut examples = Vec::new();
    let mut clean_gloss = gloss.to_string();

    // Match ; "example" or ; “example”
    let bytes = gloss.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b';' {
            let mut j = i + 1;
            while j < bytes.len() && bytes[j].is_ascii_whitespace() {
                j += 1;
            }
            if j < bytes.len() {
                let _quote_start = j;
                let is_ascii_quote = bytes[j] == b'"';
                let is_curly_quote = j + 2 < bytes.len() && &bytes[j..j + 3] == "“".as_bytes();
                if is_ascii_quote || is_curly_quote {
                    let content_start = if is_ascii_quote { j + 1 } else { j + 3 };
                    let mut k = content_start;
                    let mut found_end = None;
                    while k < bytes.len() {
                        if bytes[k] == b'"' {
                            found_end = Some((k, k + 1));
                            break;
                        } else if k + 2 < bytes.len() && &bytes[k..k + 3] == "”".as_bytes() {
                            found_end = Some((k, k + 3));
                            break;
                        }
                        k += 1;
                    }
                    if let Some((content_end, quote_end)) = found_end {
                        if let Ok(ex) = std::str::from_utf8(&bytes[content_start..content_end]) {
                            if !ex.trim().is_empty() && examples.len() < 4 {
                                examples.push(ex.trim().to_string());
                            }
                        }
                        if let Ok(prefix) = std::str::from_utf8(&bytes[..i]) {
                            if let Ok(suffix) = std::str::from_utf8(&bytes[quote_end..]) {
                                clean_gloss = format!("{}{}", prefix, suffix);
                            }
                        }
                    }
                }
            }
        }
        i += 1;
    }

    let definition = clean_gloss.trim().trim_end_matches(';').trim().to_string();

    let mut synonyms = Vec::new();
    for word in words {
        if normalize_query(word).as_deref() != Some(headword) && !synonyms.contains(word) {
            synonyms.push(word.clone());
            if synonyms.len() >= 12 {
                break;
            }
        }
    }

    DictionarySense {
        part_of_speech,
        definition,
        examples,
        synonyms,
    }
}

pub fn candidate_forms(word: &str) -> Vec<String> {
    const RULES: &[(&str, &str)] = &[
        ("s", ""),
        ("ses", "s"),
        ("xes", "x"),
        ("zes", "z"),
        ("ches", "ch"),
        ("shes", "sh"),
        ("men", "man"),
        ("ies", "y"),
        ("es", "e"),
        ("es", ""),
        ("ed", "e"),
        ("ed", ""),
        ("ing", "e"),
        ("ing", ""),
        ("er", ""),
        ("er", "e"),
        ("est", ""),
        ("est", "e"),
    ];
    let mut result = Vec::new();
    for (ending, replacement) in RULES {
        if word.len() > ending.len() + 1 && word.ends_with(ending) {
            let base = &word[..word.len() - ending.len()];
            let candidate = format!("{}{}", base, replacement);
            if !result.contains(&candidate) {
                result.push(candidate);
                if result.len() >= 20 {
                    break;
                }
            }
        }
    }
    result
}

pub fn dict_dir(app_data_dir: &Path) -> PathBuf {
    app_data_dir.join("offline-dictionary")
}

pub fn db_path(app_data_dir: &Path) -> PathBuf {
    dict_dir(app_data_dir).join(format!("{}.sqlite", PACK_ID))
}

pub fn is_installed(app_data_dir: &Path) -> bool {
    db_path(app_data_dir).is_file()
}

pub fn info(app_data_dir: &Path) -> Result<Option<DictionaryPackInfo>> {
    let path = db_path(app_data_dir);
    if !path.is_file() {
        return Ok(None);
    }
    let conn = Connection::open_with_flags(
        &path,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .map_err(error)?;

    let user_version: i32 = conn
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .map_err(error)?;
    if user_version != 1 {
        return Ok(None);
    }

    let sha256: String =
        match conn.query_row("SELECT value FROM metadata WHERE key='sha256'", [], |row| {
            row.get(0)
        }) {
            Ok(v) => v,
            Err(_) => return Ok(None),
        };
    if sha256 != PACK_SHA256 {
        return Ok(None);
    }

    let words: u32 = match conn
        .query_row("SELECT value FROM metadata WHERE key='words'", [], |row| {
            row.get::<_, String>(0)
        })
        .map_err(error)?
        .parse()
    {
        Ok(w) => w,
        Err(_) => return Ok(None),
    };

    let bytes = fs::metadata(&path).map(|m| m.len()).unwrap_or(0);

    Ok(Some(DictionaryPackInfo {
        id: PACK_ID.to_string(),
        title: PACK_TITLE.to_string(),
        words,
        bytes,
        source: SOURCE_URL.to_string(),
        license: LICENSE_URL.to_string(),
        credit: CREDIT.to_string(),
    }))
}

pub fn lookup(app_data_dir: &Path, input: &str) -> Result<Option<DictionaryEntry>> {
    let word = match normalize_query(input) {
        Some(w) => w,
        None => return Ok(None),
    };
    let path = db_path(app_data_dir);
    if !path.is_file() {
        return Ok(None);
    }
    let conn = Connection::open_with_flags(
        &path,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .map_err(error)?;

    let find_lemma = |lemma: &str| -> Result<Option<DictionaryEntry>> {
        let mut stmt = conn
            .prepare(
                "SELECT s.pos, s.gloss, s.words FROM entries e \
                 JOIN synsets s ON e.pos = s.pos AND e.offset = s.offset \
                 WHERE e.lemma = ?1 \
                 ORDER BY CASE e.pos WHEN 'n' THEN 0 WHEN 'v' THEN 1 WHEN 'a' THEN 2 ELSE 3 END, e.rank \
                 LIMIT 16",
            )
            .map_err(error)?;

        let rows = stmt
            .query_map(params![lemma], |row| {
                let pos: String = row.get(0)?;
                let gloss: String = row.get(1)?;
                let words_str: String = row.get(2)?;
                let words: Vec<String> = words_str.lines().map(|s| s.to_string()).collect();
                Ok((pos, gloss, words))
            })
            .map_err(error)?;

        let mut senses = Vec::new();
        for row in rows {
            let (pos, gloss, words) = row.map_err(error)?;
            senses.push(build_sense(&pos, &gloss, &words, lemma));
        }

        if senses.is_empty() {
            Ok(None)
        } else {
            Ok(Some(DictionaryEntry {
                query: word.clone(),
                headword: lemma.to_string(),
                senses,
            }))
        }
    };

    let mut forms_to_try = vec![word.clone()];
    let alphanumeric_trim: String = word
        .trim_matches(|c: char| !c.is_alphanumeric())
        .to_string();
    if !alphanumeric_trim.is_empty() && alphanumeric_trim != word {
        forms_to_try.push(alphanumeric_trim);
    }

    for form in forms_to_try {
        if let Some(entry) = find_lemma(&form)? {
            return Ok(Some(entry));
        }

        let mut exceptions = Vec::new();
        let mut exc_stmt = conn
            .prepare("SELECT lemma FROM exceptions WHERE form = ?1 ORDER BY rowid LIMIT 16")
            .map_err(error)?;
        let exc_rows = exc_stmt
            .query_map(params![form], |row| row.get::<_, String>(0))
            .map_err(error)?;
        for exc in exc_rows {
            exceptions.push(exc.map_err(error)?);
        }

        let mut candidates = exceptions;
        for c in candidate_forms(&form) {
            if !candidates.contains(&c) {
                candidates.push(c);
            }
        }

        for candidate in candidates {
            if let Some(entry) = find_lemma(&candidate)? {
                return Ok(Some(entry));
            }
        }
    }

    Ok(None)
}

pub fn suggestions(app_data_dir: &Path, input: &str) -> Result<Vec<String>> {
    let word = match normalize_query(input) {
        Some(w) => w,
        None => return Ok(Vec::new()),
    };
    let path = db_path(app_data_dir);
    if !path.is_file() {
        return Ok(Vec::new());
    }
    let conn = Connection::open_with_flags(
        &path,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .map_err(error)?;

    let escaped = word
        .replace('\\', "\\\\")
        .replace('%', "\\%")
        .replace('_', "\\_");
    let pattern = format!("{}%", escaped);

    let mut stmt = conn
        .prepare(
            "SELECT DISTINCT lemma FROM entries \
             WHERE lemma LIKE ?1 ESCAPE '\\' \
             ORDER BY lemma LIMIT 8",
        )
        .map_err(error)?;

    let rows = stmt
        .query_map(params![pattern], |row| row.get::<_, String>(0))
        .map_err(error)?;

    let mut results = Vec::new();
    for row in rows {
        results.push(row.map_err(error)?);
    }
    Ok(results)
}

pub fn browse(
    app_data_dir: &Path,
    prefix: &str,
    after: Option<&str>,
    page_size: usize,
) -> Result<DictionaryWordPage> {
    let clamped_size = page_size.clamp(1, 100);
    let normalized_prefix = if prefix.trim().is_empty() {
        String::new()
    } else {
        match normalize_query(prefix) {
            Some(p) => p,
            None => {
                return Ok(DictionaryWordPage {
                    words: Vec::new(),
                    has_more: false,
                })
            }
        }
    };

    let normalized_after = match after {
        Some(a) if !a.trim().is_empty() => match normalize_query(a) {
            Some(v) => Some(v),
            None => {
                return Ok(DictionaryWordPage {
                    words: Vec::new(),
                    has_more: false,
                })
            }
        },
        _ => None,
    };

    let path = db_path(app_data_dir);
    if !path.is_file() {
        return Ok(DictionaryWordPage {
            words: Vec::new(),
            has_more: false,
        });
    }

    let conn = Connection::open_with_flags(
        &path,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .map_err(error)?;

    let limit = (clamped_size + 1) as i64;
    let mut words = Vec::new();

    if normalized_prefix.is_empty() {
        if let Some(cursor) = normalized_after {
            let mut stmt = conn
                .prepare(
                    "SELECT DISTINCT lemma FROM entries \
                     WHERE lemma > ?1 \
                     ORDER BY lemma LIMIT ?2",
                )
                .map_err(error)?;
            let rows = stmt
                .query_map(params![cursor, limit], |row| row.get::<_, String>(0))
                .map_err(error)?;
            for row in rows {
                words.push(row.map_err(error)?);
            }
        } else {
            let mut stmt = conn
                .prepare(
                    "SELECT DISTINCT lemma FROM entries \
                     ORDER BY lemma LIMIT ?1",
                )
                .map_err(error)?;
            let rows = stmt
                .query_map(params![limit], |row| row.get::<_, String>(0))
                .map_err(error)?;
            for row in rows {
                words.push(row.map_err(error)?);
            }
        }
    } else {
        let escaped = normalized_prefix
            .replace('\\', "\\\\")
            .replace('%', "\\%")
            .replace('_', "\\_");
        let pattern = format!("{}%", escaped);
        if let Some(cursor) = normalized_after {
            let mut stmt = conn
                .prepare(
                    "SELECT DISTINCT lemma FROM entries \
                     WHERE lemma LIKE ?1 ESCAPE '\\' AND lemma > ?2 \
                     ORDER BY lemma LIMIT ?3",
                )
                .map_err(error)?;
            let rows = stmt
                .query_map(params![pattern, cursor, limit], |row| {
                    row.get::<_, String>(0)
                })
                .map_err(error)?;
            for row in rows {
                words.push(row.map_err(error)?);
            }
        } else {
            let mut stmt = conn
                .prepare(
                    "SELECT DISTINCT lemma FROM entries \
                     WHERE lemma LIKE ?1 ESCAPE '\\' \
                     ORDER BY lemma LIMIT ?2",
                )
                .map_err(error)?;
            let rows = stmt
                .query_map(params![pattern, limit], |row| row.get::<_, String>(0))
                .map_err(error)?;
            for row in rows {
                words.push(row.map_err(error)?);
            }
        }
    }

    let has_more = words.len() > clamped_size;
    if has_more {
        words.truncate(clamped_size);
    }

    Ok(DictionaryWordPage { words, has_more })
}

pub fn remove(app_data_dir: &Path) -> Result<()> {
    let path = db_path(app_data_dir);
    if path.exists() {
        fs::remove_file(&path).map_err(error)?;
    }
    clear_interrupted(app_data_dir)?;
    Ok(())
}

pub fn clear_interrupted(app_data_dir: &Path) -> Result<()> {
    let root = dict_dir(app_data_dir);
    if root.is_dir() {
        if let Ok(entries) = fs::read_dir(&root) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_string();
                if name.starts_with("staging-") || name.starts_with("download-") {
                    let _ = if entry.path().is_dir() {
                        fs::remove_dir_all(entry.path())
                    } else {
                        fs::remove_file(entry.path())
                    };
                }
            }
        }
    }
    Ok(())
}

pub fn install_from_zip(
    app_data_dir: &Path,
    archive_file: &Path,
    check_cancelled: impl Fn() -> Result<()>,
    on_progress: impl Fn(u32),
) -> Result<DictionaryPackInfo> {
    let mut file = File::open(archive_file).map_err(error)?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 65536];
    let mut read_bytes: u64 = 0;
    loop {
        check_cancelled()?;
        let count = file.read(&mut buffer).map_err(error)?;
        if count == 0 {
            break;
        }
        hasher.update(&buffer[..count]);
        read_bytes += count as u64;
    }
    if read_bytes != DOWNLOAD_BYTES {
        return Err("Dictionary download size is invalid.".into());
    }
    let calculated_sha = hex_digest(hasher.finalize());
    if calculated_sha != PACK_SHA256 {
        return Err("Dictionary download verification failed (checksum mismatch).".into());
    }

    let root = dict_dir(app_data_dir);
    fs::create_dir_all(&root).map_err(error)?;
    clear_interrupted(app_data_dir)?;

    let staging_dir = root.join(format!("staging-{}", uuid::Uuid::new_v4()));
    fs::create_dir_all(&staging_dir).map_err(error)?;
    let built_db_path = staging_dir.join("dictionary.sqlite");

    let install_res = (|| -> Result<DictionaryPackInfo> {
        let mut conn = Connection::open(&built_db_path).map_err(error)?;
        conn.execute_batch(
            "PRAGMA synchronous = OFF; \
             PRAGMA journal_mode = MEMORY; \
             CREATE TABLE synsets(pos TEXT NOT NULL, offset INTEGER NOT NULL, gloss TEXT NOT NULL, words TEXT NOT NULL, PRIMARY KEY(pos, offset)); \
             CREATE TABLE entries(lemma TEXT COLLATE NOCASE NOT NULL, pos TEXT NOT NULL, rank INTEGER NOT NULL, offset INTEGER NOT NULL); \
             CREATE TABLE exceptions(form TEXT NOT NULL, lemma TEXT NOT NULL); \
             CREATE TABLE metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);",
        )
        .map_err(error)?;

        let zip_file = File::open(archive_file).map_err(error)?;
        let mut zip = ZipArchive::new(zip_file).map_err(error)?;

        let mut notices = Vec::new();
        let parts = [("noun", "n"), ("verb", "v"), ("adj", "a"), ("adv", "r")];

        let tx = conn.transaction().map_err(error)?;
        {
            let mut insert_synset = tx
                .prepare("INSERT INTO synsets VALUES(?1, ?2, ?3, ?4)")
                .map_err(error)?;

            for (part_idx, (part, pos)) in parts.iter().enumerate() {
                check_cancelled()?;
                let entry_name = format!("oewn2025/data.{}", part);
                let entry = zip.by_name(&entry_name).map_err(|_| {
                    format!("Dictionary package missing required file {}", entry_name)
                })?;
                let reader = BufReader::new(entry);
                for line_res in reader.lines() {
                    let line = line_res.map_err(error)?;
                    if line.starts_with(' ') {
                        if notices.len() < 160 {
                            notices.push(line.trim().to_string());
                        }
                    } else if let Some(record) = parse_synset(&line, pos) {
                        insert_synset
                            .execute(params![
                                record.pos,
                                record.offset,
                                record.gloss,
                                record.words.join("\n")
                            ])
                            .map_err(error)?;
                    }
                }
                on_progress(10 + (part_idx as u32) * 10);
            }
        }

        {
            let mut insert_entry = tx
                .prepare("INSERT INTO entries VALUES(?1, ?2, ?3, ?4)")
                .map_err(error)?;

            for (part_idx, (part, pos)) in parts.iter().enumerate() {
                check_cancelled()?;
                let entry_name = format!("oewn2025/index.{}", part);
                let entry = zip.by_name(&entry_name).map_err(|_| {
                    format!("Dictionary package missing required file {}", entry_name)
                })?;
                let reader = BufReader::new(entry);
                for line_res in reader.lines() {
                    let line = line_res.map_err(error)?;
                    if !line.starts_with(' ') {
                        if let Some(record) = parse_index(&line, pos) {
                            for (rank, offset) in record.offsets.iter().enumerate() {
                                insert_entry
                                    .execute(params![record.lemma, pos, rank as i64, offset])
                                    .map_err(error)?;
                            }
                        }
                    }
                }
                on_progress(50 + (part_idx as u32) * 10);
            }
        }

        {
            let mut insert_exc = tx
                .prepare("INSERT INTO exceptions VALUES(?1, ?2)")
                .map_err(error)?;

            for (part, _) in parts.iter() {
                check_cancelled()?;
                let entry_name = format!("oewn2025/{}.exc", part);
                let entry = zip.by_name(&entry_name).map_err(|_| {
                    format!("Dictionary package missing required file {}", entry_name)
                })?;
                let reader = BufReader::new(entry);
                for line_res in reader.lines() {
                    let line = line_res.map_err(error)?;
                    let fields: Vec<&str> = line.split_whitespace().collect();
                    if fields.len() >= 2 {
                        let form = match normalize_query(fields[0]) {
                            Some(f) => f,
                            None => continue,
                        };
                        for lemma_raw in &fields[1..] {
                            if let Some(lemma) = normalize_query(lemma_raw) {
                                insert_exc.execute(params![form, lemma]).map_err(error)?;
                            }
                        }
                    }
                }
            }
        }

        on_progress(90);
        check_cancelled()?;

        tx.execute_batch(
            "CREATE INDEX entry_lemma ON entries(lemma); \
             CREATE INDEX exception_form ON exceptions(form);",
        )
        .map_err(error)?;

        let words_count: u32 = tx
            .query_row("SELECT COUNT(DISTINCT lemma) FROM entries", [], |row| {
                row.get(0)
            })
            .map_err(error)?;

        if !(100_000..=250_000).contains(&words_count) {
            return Err("Unexpected dictionary lemma count.".into());
        }

        tx.execute(
            "INSERT INTO metadata VALUES(?1, ?2)",
            params!["sha256", PACK_SHA256],
        )
        .map_err(error)?;
        tx.execute(
            "INSERT INTO metadata VALUES(?1, ?2)",
            params!["words", words_count.to_string()],
        )
        .map_err(error)?;
        tx.execute(
            "INSERT INTO metadata VALUES(?1, ?2)",
            params!["source", SOURCE_URL],
        )
        .map_err(error)?;
        tx.execute(
            "INSERT INTO metadata VALUES(?1, ?2)",
            params!["credit", CREDIT],
        )
        .map_err(error)?;
        tx.execute(
            "INSERT INTO metadata VALUES(?1, ?2)",
            params!["license", LICENSE_URL],
        )
        .map_err(error)?;
        tx.execute(
            "INSERT INTO metadata VALUES(?1, ?2)",
            params!["notices", notices.join("\n")],
        )
        .map_err(error)?;
        tx.pragma_update(None, "user_version", 1).map_err(error)?;

        tx.commit().map_err(error)?;

        on_progress(98);
        check_cancelled()?;

        let final_path = db_path(app_data_dir);
        if final_path.exists() {
            let _ = fs::remove_file(&final_path);
        }
        fs::rename(&built_db_path, &final_path).map_err(error)?;
        on_progress(100);

        info(app_data_dir)?.ok_or_else(|| "Dictionary verification failed after install.".into())
    })();

    let _ = fs::remove_dir_all(&staging_dir);
    install_res
}

pub struct DictionaryManager {
    app_data_dir: PathBuf,
    active_download: Mutex<Option<Arc<AtomicBool>>>,
    download_state: Mutex<DictionaryDownloadState>,
}

impl DictionaryManager {
    pub fn new(app_data_dir: PathBuf) -> Self {
        Self {
            app_data_dir,
            active_download: Mutex::new(None),
            download_state: Mutex::new(DictionaryDownloadState {
                is_downloading: false,
                progress: 0,
                error: None,
            }),
        }
    }

    pub fn status(&self) -> Result<(Option<DictionaryPackInfo>, DictionaryDownloadState)> {
        let pack_info = info(&self.app_data_dir)?;
        let state = self.download_state.lock().map_err(error)?.clone();
        Ok((pack_info, state))
    }

    pub fn lookup(&self, input: &str) -> Result<Option<DictionaryEntry>> {
        lookup(&self.app_data_dir, input)
    }

    pub fn suggestions(&self, input: &str) -> Result<Vec<String>> {
        suggestions(&self.app_data_dir, input)
    }

    pub fn browse(
        &self,
        prefix: &str,
        after: Option<&str>,
        page_size: usize,
    ) -> Result<DictionaryWordPage> {
        browse(&self.app_data_dir, prefix, after, page_size)
    }

    pub fn remove(&self) -> Result<()> {
        self.cancel_download()?;
        remove(&self.app_data_dir)
    }

    pub fn cancel_download(&self) -> Result<()> {
        let mut active = self.active_download.lock().map_err(error)?;
        if let Some(flag) = active.take() {
            flag.store(true, Ordering::SeqCst);
        }
        let mut state = self.download_state.lock().map_err(error)?;
        state.is_downloading = false;
        state.progress = 0;
        state.error = Some("Download cancelled.".to_string());
        clear_interrupted(&self.app_data_dir)?;
        Ok(())
    }

    pub fn start_download(self: &Arc<Self>) -> Result<()> {
        let mut active = self.active_download.lock().map_err(error)?;
        if active.is_some() {
            return Err("A dictionary download is already in progress.".into());
        }
        let cancelled = Arc::new(AtomicBool::new(false));
        *active = Some(cancelled.clone());

        {
            let mut state = self.download_state.lock().map_err(error)?;
            state.is_downloading = true;
            state.progress = 0;
            state.error = None;
        }

        let mgr = self.clone();
        std::thread::spawn(move || {
            let res = mgr.execute_download(cancelled);
            let mut active = match mgr.active_download.lock() {
                Ok(a) => a,
                Err(_) => return,
            };
            *active = None;
            if let Ok(mut state) = mgr.download_state.lock() {
                state.is_downloading = false;
                match res {
                    Ok(_) => {
                        state.progress = 100;
                        state.error = None;
                    }
                    Err(e) => {
                        state.error = Some(e.to_string());
                    }
                }
            }
        });

        Ok(())
    }

    fn execute_download(&self, cancelled: Arc<AtomicBool>) -> Result<DictionaryPackInfo> {
        let root = dict_dir(&self.app_data_dir);
        fs::create_dir_all(&root).map_err(error)?;
        clear_interrupted(&self.app_data_dir)?;

        let temp_download = root.join(format!("download-{}.zip", uuid::Uuid::new_v4()));

        let download_res = (|| -> Result<DictionaryPackInfo> {
            let _ = rustls::crypto::ring::default_provider().install_default();
            let client = reqwest::blocking::Client::builder()
                .connect_timeout(Duration::from_secs(15))
                .timeout(Duration::from_secs(180))
                .user_agent("Vern-Desktop/0.1 (WordNet Dictionary Pack)")
                .build()
                .map_err(error)?;

            let mut response = client
                .get(PACK_URL)
                .send()
                .and_then(|r| r.error_for_status())
                .map_err(error)?;

            let mut out = File::create(&temp_download).map_err(error)?;
            let mut downloaded: u64 = 0;
            let mut buffer = [0u8; 32768];

            loop {
                if cancelled.load(Ordering::SeqCst) {
                    return Err("Dictionary download was cancelled.".into());
                }
                let count = response.read(&mut buffer).map_err(error)?;
                if count == 0 {
                    break;
                }
                std::io::Write::write_all(&mut out, &buffer[..count]).map_err(error)?;
                downloaded += count as u64;

                let pct = if DOWNLOAD_BYTES > 0 {
                    ((downloaded as f64 / DOWNLOAD_BYTES as f64) * 40.0) as u32
                } else {
                    10
                };
                if let Ok(mut state) = self.download_state.lock() {
                    state.progress = pct.min(40);
                }
            }

            if downloaded != DOWNLOAD_BYTES {
                return Err("Downloaded archive size does not match expected size.".into());
            }

            let check_cancelled = || {
                if cancelled.load(Ordering::SeqCst) {
                    Err("Dictionary install cancelled.".into())
                } else {
                    Ok(())
                }
            };

            let mgr = self;
            let on_progress = |step_pct: u32| {
                // Mapping 40..100
                let total_pct = 40 + (step_pct * 60 / 100);
                if let Ok(mut state) = mgr.download_state.lock() {
                    state.progress = total_pct.min(100);
                }
            };

            install_from_zip(
                &self.app_data_dir,
                &temp_download,
                check_cancelled,
                on_progress,
            )
        })();

        let _ = fs::remove_file(&temp_download);
        download_res
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn query_normalization_handles_punctuation_and_whitespace() {
        assert_eq!(normalize_query("  Word  "), Some("word".to_string()));
        assert_eq!(normalize_query("it’s"), Some("it's".to_string()));
        assert_eq!(normalize_query("ice_cream"), Some("ice cream".to_string()));
        assert_eq!(normalize_query(""), None);
        assert_eq!(normalize_query("   "), None);
    }

    #[test]
    fn synset_and_index_parsing_works() {
        let synset_line = "00001740 03 n 01 entity 0 003 | that which is perceived or known or inferred to have its own distinct existence (living or nonliving)";
        let parsed = parse_synset(synset_line, "n").expect("parse synset");
        assert_eq!(parsed.offset, 1740);
        assert_eq!(parsed.pos, "n");
        assert_eq!(parsed.words, vec!["entity"]);
        assert!(parsed.gloss.starts_with("that which is perceived"));

        let index_line = "entity n 1 3 @ ~ + 1 0 00001740";
        let idx = parse_index(index_line, "n").expect("parse index");
        assert_eq!(idx.lemma, "entity");
        assert_eq!(idx.pos, "n");
        assert_eq!(idx.offsets, vec![1740]);
    }

    #[test]
    fn candidate_forms_generates_standard_inflections() {
        let forms = candidate_forms("boxes");
        assert!(forms.contains(&"box".to_string()));

        let forms = candidate_forms("running");
        assert!(forms.contains(&"runn".to_string()) || forms.contains(&"runne".to_string()));

        let forms = candidate_forms("cities");
        assert!(forms.contains(&"city".to_string()));
    }
}
