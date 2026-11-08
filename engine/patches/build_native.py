#!/usr/bin/env python3
"""Rebuild one pinned native tool with a narrow host hand-off; vendor dependencies stay offline."""
from pathlib import Path
import os
import platform
import shutil
import subprocess
import sys
import hashlib
import json

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).parent))
from generate import replace_once

def patch(source):
    source = '#[path = "hyperbackup_host.rs"]\nmod hyperbackup_host;\n' + source
    # Keep the inner module doc comment before the inserted item.
    source = source.replace('//! One-session loopback list editor. No shell execution from HTTP input.', '// One-session loopback list editor. No shell execution from HTTP input.')
    source = replace_once(source,
        '    let mut _console_page=web_console::Page::register(&url,!std::env::var("SPEEDBACKUP_AUTH_SETUP").is_ok_and(|v|v=="1"))?;',
        '    let hyperbackup = hyperbackup_host::register(&origin, &token, args, selection)?;\n    let mut _console_page=if hyperbackup.is_some() { None } else { web_console::Page::register(&url,!std::env::var("SPEEDBACKUP_AUTH_SETUP").is_ok_and(|v|v=="1"))? };')
    source = replace_once(source, '    let mut return_task = if args.len() >= 2 && _console_page.is_none() {',
        '    let mut return_task = if hyperbackup.is_none() && args.len() >= 2 && _console_page.is_none() {')
    source = replace_once(source, '    if args.get(1).is_some_and(|s| s == "--open") && return_task.is_none() && _console_page.is_none() {',
        '    if hyperbackup.is_none() && args.get(1).is_some_and(|s| s == "--open") && return_task.is_none() && _console_page.is_none() {')
    return source

def main():
    ndk = Path(os.environ['ANDROID_NDK_HOME'])
    cargo = os.environ.get('CARGO', 'cargo')
    output = ROOT / 'work/native-source'
    output.mkdir(parents=True, exist_ok=True)
    upstream = ROOT / 'engine/upstream/backup_script'
    for folder in ['rust', 'webview/embedded', 'tools']:
        shutil.copytree(upstream / folder, output / folder, dirs_exist_ok=True, ignore=shutil.ignore_patterns('target'))
    shutil.copy2(upstream / 'versions.properties', output / 'versions.properties')
    source = output / 'rust/src/applist_web.rs'
    source.write_text(patch(source.read_text()))
    shutil.copy2(ROOT / 'engine/patches/hyperbackup_host.rs', output / 'rust/src/hyperbackup_host.rs')
    main_source = output / 'rust/src/main.rs'
    main_source.write_text(replace_once(main_source.read_text(), 'speednative.multicall.v1 speednative.argv0_dispatch.v1',
        'speednative.multicall.v1 speednative.argv0_dispatch.v1 speednative.hyperbackup_host.v1'))
    host = 'darwin-x86_64' if platform.system() == 'Darwin' else 'linux-x86_64'
    binpath = ndk / 'toolchains/llvm/prebuilt' / host / 'bin'
    env = os.environ.copy()
    env['CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER'] = str(binpath / 'aarch64-linux-android28-clang')
    env['CARGO_TARGET_AARCH64_LINUX_ANDROID_AR'] = str(binpath / 'llvm-ar')
    env['RUSTFLAGS'] = '-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
    subprocess.run([cargo, 'build', '--offline', '--locked', '--release', '--target', 'aarch64-linux-android'], cwd=output / 'rust', env=env, check=True)
    destination = ROOT / 'engine/generated'
    destination.mkdir(exist_ok=True)
    shutil.copy2(output / 'rust/target/aarch64-linux-android/release/speednative', destination / 'speednative')
    fingerprint = hashlib.sha256(b''.join((ROOT / 'engine/patches' / name).read_bytes() for name in ['build_native.py', 'hyperbackup_host.rs'])).hexdigest()
    (destination / 'provenance.json').write_text(json.dumps({'upstream': '4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5',
        'patchFingerprint': fingerprint, 'sha256': hashlib.sha256((destination / 'speednative').read_bytes()).hexdigest(),
        'rust': '1.99.1', 'ndk': '30.0.16248370', 'api': 28}, indent=2) + '\n')

if __name__ == '__main__': main()
