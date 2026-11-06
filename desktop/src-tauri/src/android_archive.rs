//! Lossless Android import provenance. Names are archive keys, never filesystem paths.
use crate::{
    backup_archive::StagedFile,
    storage::{error, hex_digest, Result, Storage},
    transfer::LIMIT,
};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{
    collections::{HashMap, HashSet},
    io::Write,
    path::Path,
    sync::Arc,
};

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AndroidAsset {
    pub name: String,
    pub size: usize,
    pub sha256: String,
    #[serde(skip)]
    pub staged: Option<Arc<StagedFile>>,
}
#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct AndroidImport {
    pub id: String,
    pub metadata: Value,
    pub assets: Vec<AndroidAsset>,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportSummary {
    pub id: String,
    pub created_at: u64,
    pub readings: usize,
    pub assets: usize,
}
fn safe_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 2000
        && !name.starts_with('/')
        && !name
            .chars()
            .any(|c| c.is_control() || matches!(c, '\\' | ':'))
        && name.split('/').all(|part| !["", ".", ".."].contains(&part))
        && name != "backup.json"
}
impl AndroidAsset {
    pub fn archive_name(&self, import_id: &str) -> Result<String> {
        if import_id.len() != 64
            || !import_id.bytes().all(|c| c.is_ascii_hexdigit())
            || !safe_name(&self.name)
        {
            return Err("Invalid retained Android attachment identity.".into());
        }
        Ok(format!(
            "android_imports/{import_id}/{}.bin",
            hex_digest(Sha256::digest(self.name.as_bytes()))
        ))
    }
    pub fn bytes(&self) -> Result<Vec<u8>> {
        let bytes = self
            .staged
            .as_ref()
            .ok_or("Choose the full backup to include retained Android attachments.")?
            .read(32 * 1024 * 1024)?;
        self.validate_bytes(&bytes)?;
        Ok(bytes)
    }
    fn validate_bytes(&self, bytes: &[u8]) -> Result<()> {
        if !safe_name(&self.name)
            || self.size > 32 * 1024 * 1024
            || self.size != bytes.len()
            || self.sha256 != hex_digest(Sha256::digest(bytes))
        {
            return Err("A retained Android attachment failed its integrity check.".into());
        }
        Ok(())
    }
}
impl AndroidImport {
    pub fn from_parts(metadata: Value, assets: &HashMap<String, Arc<StagedFile>>) -> Result<Self> {
        let mut records = Vec::new();
        for (name, staged) in assets {
            let bytes = staged.read(32 * 1024 * 1024)?;
            records.push(AndroidAsset {
                name: name.clone(),
                size: bytes.len(),
                sha256: hex_digest(Sha256::digest(&bytes)),
                staged: Some(staged.clone()),
            });
        }
        records.sort_by(|a, b| a.name.cmp(&b.name));
        let mut item = Self {
            id: String::new(),
            metadata,
            assets: records,
        };
        item.id = item.digest()?;
        item.validate()?;
        Ok(item)
    }
    fn digest(&self) -> Result<String> {
        // Include the manifest, so different originals with identical JSON stay distinct.
        let bytes = serde_json::to_vec(&(&self.metadata, &self.assets)).map_err(error)?;
        if bytes.len() > LIMIT {
            return Err("Retained Android metadata exceeds 64 MB.".into());
        }
        Ok(hex_digest(Sha256::digest(bytes)))
    }
    pub fn validate_manifest(&self) -> Result<()> {
        if !self.metadata.is_object()
            || self
                .metadata
                .get("documents")
                .and_then(Value::as_array)
                .is_none()
            || self.assets.len() > 20_000
            || self.digest()? != self.id
        {
            return Err("Invalid retained Android backup metadata.".into());
        }
        let mut names = HashSet::new();
        let mut total = 0_u64;
        for asset in &self.assets {
            asset.archive_name(&self.id)?;
            if !names.insert(&asset.name) {
                return Err("Duplicate retained Android attachment.".into());
            }
            if asset.size > 32 * 1024 * 1024
                || asset.sha256.len() != 64
                || !asset.sha256.bytes().all(|c| c.is_ascii_hexdigit())
            {
                return Err("Invalid retained Android attachment manifest.".into());
            }
            total = total
                .checked_add(asset.size as u64)
                .ok_or("Android attachment size overflow.")?;
            if total > 2 * 1024 * 1024 * 1024 {
                return Err("Retained Android attachments exceed 2 GB.".into());
            }
        }
        Ok(())
    }
    pub fn validate(&self) -> Result<()> {
        self.validate_manifest()?;
        for asset in &self.assets {
            asset.bytes()?;
        }
        Ok(())
    }
}
impl Storage {
    pub(crate) fn android_imports(&self) -> Result<Vec<AndroidImport>> {
        let mut query = self
            .connection
            .prepare("SELECT id,metadata FROM android_imports ORDER BY id")
            .map_err(error)?;
        let rows = query
            .query_map([], |r| Ok((r.get::<_, String>(0)?, r.get::<_, String>(1)?)))
            .map_err(error)?;
        let mut output = Vec::new();
        for row in rows {
            let (id, metadata) = row.map_err(error)?;
            let mut asset_query = self
                .connection
                .prepare(
                    "SELECT name,size,sha256 FROM android_assets WHERE import_id=?1 ORDER BY name",
                )
                .map_err(error)?;
            let assets = asset_query
                .query_map([&id], |r| {
                    Ok(AndroidAsset {
                        name: r.get(0)?,
                        size: crate::storage::read_unsigned(r, 1)?,
                        sha256: r.get(2)?,
                        staged: None,
                    })
                })
                .map_err(error)?
                .map(|r| r.map_err(error))
                .collect::<Result<Vec<_>>>()?;
            output.push(AndroidImport {
                id,
                metadata: serde_json::from_str(&metadata).map_err(error)?,
                assets,
            });
        }
        Ok(output)
    }
    pub fn android_import_summaries(&self) -> Result<Vec<ImportSummary>> {
        Ok(self
            .android_imports()?
            .into_iter()
            .map(|item| ImportSummary {
                id: item.id,
                created_at: item.metadata["createdAt"].as_u64().unwrap_or(0),
                readings: item.metadata["documents"].as_array().map_or(0, Vec::len),
                assets: item.assets.len(),
            })
            .collect())
    }
    pub fn delete_android_import(&self, id: &str) -> Result<()> {
        if id.len() != 64 || !id.bytes().all(|c| c.is_ascii_hexdigit()) {
            return Err("Choose a retained Android backup.".into());
        }
        self.connection
            .execute("DELETE FROM android_imports WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
    pub(crate) fn android_asset_bytes(&self, id: &str, asset: &AndroidAsset) -> Result<Vec<u8>> {
        let bytes: Vec<u8> = self
            .connection
            .query_row(
                "SELECT bytes FROM android_assets WHERE import_id=?1 AND name=?2",
                params![id, asset.name],
                |r| r.get(0),
            )
            .map_err(error)?;
        asset.validate_bytes(&bytes)?;
        Ok(bytes)
    }
    pub(crate) fn restore_android_import(&self, item: &AndroidImport) -> Result<()> {
        item.validate()?;
        let exists: Option<String> = self
            .connection
            .query_row(
                "SELECT id FROM android_imports WHERE id=?1",
                [&item.id],
                |r| r.get(0),
            )
            .optional()
            .map_err(error)?;
        if exists.is_some() {
            return Ok(());
        }
        let count: usize = self
            .connection
            .query_row("SELECT count(*) FROM android_imports", [], |r| {
                crate::storage::read_unsigned(r, 0)
            })
            .map_err(error)?;
        if count >= 100 {
            return Err("Export and remove an older retained Android backup before importing more (limit 100).".into());
        }
        self.connection
            .execute(
                "INSERT INTO android_imports(id,metadata) VALUES(?1,?2)",
                params![
                    item.id,
                    serde_json::to_string(&item.metadata).map_err(error)?
                ],
            )
            .map_err(error)?;
        for asset in &item.assets {
            self.connection.execute("INSERT INTO android_assets(import_id,name,size,sha256,bytes) VALUES(?1,?2,?3,?4,?5)", params![item.id,asset.name,i64::try_from(asset.size).map_err(error)?,asset.sha256,asset.bytes()?]).map_err(error)?;
        }
        Ok(())
    }
    pub fn export_android_import(&self, id: &str, path: &Path) -> Result<()> {
        let item = self
            .android_imports()?
            .into_iter()
            .find(|item| item.id == id)
            .ok_or("Choose a retained Android backup.")?;
        item.validate_manifest()?;
        // Write alongside the chosen destination for an atomic same-volume replacement.
        let target = path
            .parent()
            .ok_or("Choose a backup folder.")?
            .join(format!(".vern-android-{}.tmp", uuid::Uuid::new_v4()));
        let result = (|| {
            let file = std::fs::OpenOptions::new()
                .write(true)
                .create_new(true)
                .open(&target)
                .map_err(error)?;
            let mut zip = zip::ZipWriter::new(crate::backup_archive::BoundedFile {
                inner: file,
                written: 0,
            });
            let options = zip::write::SimpleFileOptions::default()
                .compression_method(zip::CompressionMethod::Deflated);
            zip.start_file("backup.json", options).map_err(error)?;
            zip.write_all(&serde_json::to_vec(&item.metadata).map_err(error)?)
                .map_err(error)?;
            for asset in &item.assets {
                if !safe_name(&asset.name) {
                    return Err("Unsafe Android archive name.".into());
                }
                zip.start_file(
                    &asset.name,
                    options.compression_method(zip::CompressionMethod::Stored),
                )
                .map_err(error)?;
                zip.write_all(&self.android_asset_bytes(id, asset)?)
                    .map_err(error)?;
            }
            let file = zip.finish().map_err(error)?;
            file.inner.sync_all().map_err(error)?;
            drop(file);
            std::fs::rename(&target, path).map_err(error)
        })();
        if result.is_err() {
            let _ = std::fs::remove_file(&target);
        }
        result
    }
}
