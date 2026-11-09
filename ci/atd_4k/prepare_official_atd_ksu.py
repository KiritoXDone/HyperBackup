#!/usr/bin/env python3
"""Produce a KernelSU ramdisk overlay for the pinned official 4 KB ARM64 ATD.

The original SDK image and any attached device are never modified. The output
is an isolated ramdisk, not a complete or boot-verified system image. A later
CI job must verify the full Google archive, boot the AVD, and test app UID Root.
"""
from __future__ import annotations
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tempfile
from zipfile import ZipFile

LOCK_PATH = Path(__file__).with_name('official-aosp-atd-36-arm64-4k.lock.json')


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def require_hash(path: Path, expected: str) -> None:
    if not path.is_file() or sha256(path) != expected:
        raise ValueError(f'Pinned file checksum mismatch: {path.name}')


def kernel_config(path: Path, expected_release: str) -> None:
    image = gzip.decompress(path.read_bytes())
    found = re.search(rb'Linux version ([^\x00\n ]+)', image)
    if not found or found.group(1).decode('ascii') != expected_release:
        raise ValueError('Unexpected ATD kernel release')
    start = image.find(b'IKCFG_ST')
    end = image.find(b'IKCFG_ED', start + 8)
    if start < 0 or end <= start:
        raise ValueError('Embedded kernel configuration is missing')
    config = gzip.decompress(image[start + 8:end]).decode('utf8')
    if 'CONFIG_ARM64_4K_PAGES=y' not in config.splitlines() or 'CONFIG_MODULES=y' not in config.splitlines():
        raise ValueError('Kernel must use 4 KB pages and support modules')


def verify_archive(archive: Path, image_dir: Path, source: dict) -> str:
    """Check Google's indexed archive and all installed image files against it."""
    if not archive.is_file() or archive.stat().st_size != source['archive_size']:
        raise ValueError('Official ATD archive size mismatch')
    indexed = hashlib.sha1(usedforsecurity=False)
    stronger = hashlib.sha256()
    with archive.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            indexed.update(block)
            stronger.update(block)
    if indexed.hexdigest() != source['archive_sha1_google_index']:
        raise ValueError('Official ATD archive checksum mismatch')
    seen = set()
    with ZipFile(archive) as source_zip:
        for info in source_zip.infolist():
            if info.is_dir():
                continue
            parts = Path(info.filename).parts
            if len(parts) < 2 or parts[0] != 'arm64-v8a' or '..' in parts or '\\' in info.filename:
                raise ValueError('Unexpected official ATD archive member')
            relative = Path(*parts[1:])
            if relative in seen or (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('Duplicate or symbolic-link archive member')
            seen.add(relative)
            installed = (image_dir / relative).resolve()
            if not installed.is_relative_to(image_dir) or not installed.is_file() or installed.stat().st_size != info.file_size:
                raise ValueError(f'Installed ATD image differs from archive: {relative}')
            expected, actual = hashlib.sha256(), hashlib.sha256()
            with source_zip.open(info) as original, installed.open('rb') as current:
                for block in iter(lambda: original.read(1024 * 1024), b''):
                    expected.update(block)
                for block in iter(lambda: current.read(1024 * 1024), b''):
                    actual.update(block)
            if expected.digest() != actual.digest():
                raise ValueError(f'Installed ATD image differs from archive: {relative}')
    if not {'kernel-ranchu', 'ramdisk.img', 'source.properties', 'system.img', 'vendor.img'} <= {str(item) for item in seen}:
        raise ValueError('Official ATD archive is incomplete')
    return stronger.hexdigest()


def cpio_entries(data: bytes) -> dict[str, bytes]:
    """Read newc members without extracting archive paths to the filesystem."""
    entries = {}
    offset = 0
    while offset < len(data):
        if data[offset] == 0:
            offset += 1
            continue
        if data[offset:offset + 6] not in (b'070701', b'070702') or offset + 110 > len(data):
            raise ValueError('Invalid ramdisk cpio header')
        try:
            fields = [int(data[offset + 6 + i * 8:offset + 14 + i * 8], 16) for i in range(13)]
        except ValueError as error:
            raise ValueError('Invalid ramdisk cpio metadata') from error
        size, namesize = fields[6], fields[11]
        name_start = offset + 110
        name_end = name_start + namesize
        content_start = (name_end + 3) & ~3
        content_end = content_start + size
        if namesize < 1 or name_end > len(data) or content_end > len(data) or data[name_end - 1] != 0:
            raise ValueError('Truncated ramdisk cpio entry')
        name = data[name_start:name_end - 1].decode('utf8').removeprefix('./')
        if name in ('ksu_config', 'kernelsu.ko'):
            if name in entries:
                raise ValueError(f'Duplicate ramdisk member: {name}')
            entries[name] = data[content_start:content_end]
        offset = (content_end + 3) & ~3
    return entries


def prepare(image_dir: Path, ksud: Path, module: Path, output: Path, lock: dict, *,
            archive: Path | None = None, components_only: bool = False,
            host: str | None = None, runner=subprocess.run) -> dict:
    image_dir, ksud, module, output = (p.resolve() for p in (image_dir, ksud, module, output))
    if output.is_relative_to(image_dir):
        raise ValueError('Output must not overwrite the original SDK image')
    if output.exists() or not output.parent.is_dir():
        raise ValueError('Output must be new and its parent directory must exist')
    source, ksu = lock['system_image'], lock['kernelsu']
    host = host or f'{platform.system().lower()}-{platform.machine().lower()}'
    if host not in ksu['host_tools']:
        raise ValueError(f'No pinned KernelSU tool for host: {host}')
    for name, key in [('source.properties', 'source_properties_sha256'),
                      ('kernel-ranchu', 'kernel_sha256'), ('ramdisk.img', 'ramdisk_sha256')]:
        require_hash(image_dir / name, source[key])
    require_hash(ksud, ksu['host_tools'][host]['sha256'])
    require_hash(module, ksu['module']['sha256'])
    kernel_config(image_dir / 'kernel-ranchu', source['kernel_release'])
    if archive is None and (not components_only or os.environ.get('CI') == 'true'):
        raise ValueError('The complete official ATD archive is required in CI')
    if archive is not None and components_only:
        raise ValueError('Choose either archive verification or components-only research')
    archive_sha256 = verify_archive(archive.resolve(), image_dir, source) if archive is not None else None
    with tempfile.TemporaryDirectory(prefix='hyperbackup-ksu-', dir=output.parent) as temporary:
        stage = Path(temporary)
        command = [str(ksud), 'boot-patch', '--boot', str(image_dir / 'ramdisk.img'), '--ramdisk',
                   '--arch', 'aarch64', '--module', str(module), '--kmi', source['kmi'], '--allow-shell',
                   '--out', str(stage), '--out-name', 'ramdisk.img']
        try:
            runner(command, check=True, timeout=120, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        except subprocess.CalledProcessError as error:
            output_text = (error.stderr or error.stdout or b'').decode('utf8', errors='replace')[-2000:]
            raise RuntimeError(f'KernelSU ramdisk patch failed: {output_text}') from error
        patched = stage / 'ramdisk.img'
        if patched.is_symlink() or not patched.is_file() or sha256(patched) == source['ramdisk_sha256']:
            raise ValueError('KernelSU did not produce a separate patched ramdisk')
        content = runner(['lz4', '-dc', str(patched)], check=True, timeout=30, capture_output=True).stdout
        entries = cpio_entries(content)
        if b'allow_shell=1' not in entries.get('ksu_config', b'').splitlines() or entries.get('kernelsu.ko') != module.read_bytes():
            raise ValueError('Patched ramdisk lacks the pinned module or shell setting')
        with patched.open('rb') as source_file, output.open('xb') as target:
            shutil.copyfileobj(source_file, target)
    return {
        'sdk_package': source['sdk_package'], 'kernel_release': source['kernel_release'],
        'kmi': source['kmi'], 'expected_page_size': source['expected_page_size'],
        'kernelsu': ksu['version'], 'module_sha256': ksu['module']['sha256'],
        'host_tool_platform': host, 'host_tool_sha256': ksu['host_tools'][host]['sha256'],
        'kernel_sha256': source['kernel_sha256'], 'source_properties_sha256': source['source_properties_sha256'],
        'stock_ramdisk_sha256': source['ramdisk_sha256'], 'patched_ramdisk_sha256': sha256(output),
        'full_archive_verified': archive_sha256 is not None, 'archive_sha256': archive_sha256,
        'verification_scope': 'full archive and installed files' if archive_sha256 else 'source.properties, kernel and ramdisk only',
        'boot_verified': False, 'app_root_verified': False,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--image-dir', type=Path, required=True)
    parser.add_argument('--ksud', type=Path, required=True)
    parser.add_argument('--module', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--archive', type=Path, help='Original Google ATD ZIP, required in CI')
    parser.add_argument('--research-components-only', action='store_true', help='Local offline research; not allowed in CI')
    args = parser.parse_args()
    report = prepare(args.image_dir, args.ksud, args.module, args.output,
                     json.loads(LOCK_PATH.read_text(encoding='utf8')), archive=args.archive,
                     components_only=args.research_components_only)
    print(json.dumps(report, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
