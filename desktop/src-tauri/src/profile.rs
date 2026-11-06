use std::path::PathBuf;

/// Explicit profiles isolate native storage and Tauri's WebView cache together.
/// Never fall back to the user's library when a requested profile is invalid.
pub fn validate(path: PathBuf) -> std::io::Result<PathBuf> {
    if !path.is_absolute()
        || path.file_name().is_none()
        || path.components().any(|part| {
            matches!(
                part,
                std::path::Component::ParentDir | std::path::Component::CurDir
            )
        })
    {
        return Err(std::io::Error::other(
            "VERN_PROFILE_DIR must name an absolute, dedicated profile directory without dot components.",
        ));
    }
    Ok(path)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_ambiguous_or_broad_profile_paths() {
        assert!(validate(PathBuf::new()).is_err());
        assert!(validate(PathBuf::from("relative/profile")).is_err());
        let root = std::env::temp_dir();
        assert!(validate(root.join("vern-profile")).is_ok());
        assert!(validate(root.join("vern-profile/../other")).is_err());
        assert!(validate(root.ancestors().last().unwrap().to_path_buf()).is_err());
    }
}
