// SPDX-License-Identifier: GPL-3.0-only
// A private hand-off for the unchanged upstream editor API. No backup or restore policy lives here.
use std::{fs::{self, File, OpenOptions}, io::{self, Write}, os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt}, path::PathBuf};

pub(super) struct HostSession { path: PathBuf }
impl Drop for HostSession {
    fn drop(&mut self) { let _ = fs::remove_file(self.path.join("endpoint.json")); }
}

pub(super) fn register(origin: &str, token: &str, args: &[String], selection: Option<&str>) -> io::Result<Option<HostSession>> {
    if std::env::var("HYPERBACKUP_BRIDGE").as_deref() != Ok("1") { return Ok(None); }
    let root = PathBuf::from(std::env::var_os("HYPERBACKUP_JOB").ok_or_else(|| io::Error::other("host job missing"))?);
    let metadata = fs::symlink_metadata(&root)?;
    if !root.is_absolute() || root.canonicalize()? != root || !metadata.is_dir() || metadata.uid() != 0 || metadata.mode() & 0o077 != 0 {
        return Err(io::Error::other("host job identity"));
    }
    let directory = root.join("editors").join(format!("editor-{}-{}", std::process::id(), &token[..12]));
    fs::create_dir(&directory)?;
    fs::set_permissions(&directory, fs::Permissions::from_mode(0o700))?;
    let payload = serde_json::json!({"protocol": 1, "origin": origin, "token": token, "args": args, "selection": selection});
    let mut file = OpenOptions::new().write(true).create_new(true).mode(0o600).open(directory.join("endpoint.pending"))?;
    file.write_all(payload.to_string().as_bytes())?; file.sync_all()?;
    fs::rename(directory.join("endpoint.pending"), directory.join("endpoint.json"))?;
    File::open(&directory)?.sync_all()?;
    Ok(Some(HostSession { path: directory }))
}
