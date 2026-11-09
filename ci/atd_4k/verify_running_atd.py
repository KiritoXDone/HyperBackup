#!/usr/bin/env python3
"""Check a disposable AVD's official ATD configuration, 4 KB ABI and KernelSU.

This observes the emulator only. It does not grant HyperBackup Root or run any
backup/restore operation; the separate app fixture must prove that path.
"""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
from prepare_official_atd_ksu import LOCK_PATH, require_hash, sha256

MANAGER = 'me.weishu.kernelsu'


def properties(path: Path) -> dict[str, str]:
    result = {}
    for raw in path.read_text(encoding='utf8').splitlines():
        line = raw.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            raise ValueError(f'Invalid property in {path.name}')
        key, value = line.split('=', 1)
        if key.strip() in result:
            raise ValueError(f'Duplicate property in {path.name}: {key}')
        result[key.strip()] = value.strip()
    return result


def verify(serial: str, adb: str, sdk: Path, avd_config: Path, prepared: dict, lock: dict, *,
           runner=subprocess.run) -> dict:
    if not re.fullmatch(r'emulator-[0-9]+', serial):
        raise ValueError('Only an explicit emulator serial is allowed')
    source, ksu = lock['system_image'], lock['kernelsu']
    expected = {'sdk_package': source['sdk_package'], 'kernel_release': source['kernel_release'],
                'kmi': source['kmi'], 'expected_page_size': 4096, 'kernelsu': ksu['version'],
                'module_sha256': ksu['module']['sha256'], 'stock_ramdisk_sha256': source['ramdisk_sha256'],
                'kernel_sha256': source['kernel_sha256'], 'source_properties_sha256': source['source_properties_sha256']}
    if any(prepared.get(key) != value for key, value in expected.items()):
        raise ValueError('Preparation evidence does not match the pinned full official image')
    if prepared.get('full_archive_verified') is not True:
        raise ValueError('Preparation evidence does not match the pinned full official image')
    host = prepared.get('host_tool_platform')
    if host not in ksu['host_tools'] or prepared.get('host_tool_sha256') != ksu['host_tools'][host]['sha256']:
        raise ValueError('Preparation evidence does not match a pinned KernelSU host tool')
    archive_digest = prepared.get('archive_sha256', '')
    if not isinstance(archive_digest, str) or not re.fullmatch(r'[a-f0-9]{64}', archive_digest):
        raise ValueError('Missing verified archive digest')
    patched_digest = prepared.get('patched_ramdisk_sha256', '')
    if not isinstance(patched_digest, str) or not re.fullmatch(r'[a-f0-9]{64}', patched_digest):
        raise ValueError('Missing patched ramdisk digest')

    def call(*args: str) -> str:
        result = runner([adb, '-s', serial, *args], capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise ValueError(f'ADB command failed: {args[0]}')
        return result.stdout.strip()

    def shell(command: str) -> str:
        return call('shell', command)

    if shell('getprop ro.kernel.qemu') != '1':
        raise ValueError('Target did not identify itself as an emulator')
    names = [line.strip() for line in call('emu', 'avd', 'name').splitlines() if line.strip() and line.strip() != 'OK']
    if len(names) != 1 or not re.fullmatch(r'[A-Za-z0-9_.-]+', names[0]):
        raise ValueError('Cannot identify the running AVD')
    config = avd_config.resolve()
    if config.name != 'config.ini' or config.parent.name != names[0] + '.avd':
        raise ValueError('AVD config does not match the running emulator')
    values = properties(config)
    sysdir = values.get('image.sysdir.1', '')
    if not sysdir:
        raise ValueError('AVD system-image directory is missing')
    sdk = sdk.resolve()
    image = Path(sysdir)
    image = image.resolve() if image.is_absolute() else (sdk / image).resolve()
    wanted = (sdk / 'system-images/android-36/aosp_atd/arm64-v8a').resolve()
    if not wanted.is_relative_to((sdk / 'system-images').resolve()) or image != wanted:
        raise ValueError('AVD is not configured for the pinned official ARM64 ATD')
    require_hash(image / 'source.properties', source['source_properties_sha256'])
    require_hash(image / 'kernel-ranchu', source['kernel_sha256'])
    require_hash(image / 'ramdisk.img', patched_digest)
    metadata = properties(image / 'source.properties')
    if metadata.get('SystemImage.TagId') != 'aosp_atd' or metadata.get('SystemImage.Abi') != 'arm64-v8a' or metadata.get('AndroidVersion.ApiLevel') != '36':
        raise ValueError('SDK image metadata is not Android 36 ARM64 AOSP ATD')
    if shell('getprop sys.boot_completed') != '1':
        raise ValueError('Android has not completed boot')
    abi = shell('getprop ro.product.cpu.abi')
    if abi != 'arm64-v8a' or shell('getprop ro.build.version.sdk') != '36':
        raise ValueError('Guest API or ABI differs from the pinned ATD')
    page_size = shell('getconf PAGESIZE')
    if page_size != '4096':
        raise ValueError(f'Expected a 4 KB guest, got page size {page_size}')
    kernel = shell('uname -r')
    if kernel != source['kernel_release']:
        raise ValueError('Running kernel differs from the pinned official ATD kernel')
    if shell('getenforce') != 'Enforcing':
        raise ValueError('SELinux is not Enforcing')
    if not re.search(r'\buid=0(?:\(|\s|$)', shell('su -c id')):
        raise ValueError('KernelSU did not grant the emulator shell Root')
    version = shell("su -c '/data/adb/ksud debug version'")
    if not re.search(rf'\bKernel Version:\s*{ksu["kernel_version_code"]}\b', version):
        raise ValueError('KernelSU kernel version does not match the pinned module')
    package_path = shell(f'pm path {MANAGER}')
    match = re.fullmatch(r'package:(/data/app/[A-Za-z0-9._~+=/-]+/base\.apk)', package_path)
    if not match:
        raise ValueError('The official KernelSU manager is not installed in the data partition')
    checksum = shell(f"su -c 'sha256sum {match.group(1)}'").split()
    if not checksum or checksum[0].lower() != ksu['manager']['sha256']:
        raise ValueError('Installed KernelSU manager differs from the pinned official APK')
    return {'sdk_package': source['sdk_package'], 'serial': serial, 'avd_name': names[0],
            'abi': abi, 'page_size': int(page_size), 'kernel_release': kernel, 'selinux': 'Enforcing',
            'kernel_su': ksu['version'], 'shell_root_verified': True, 'manager_sha256': checksum[0].lower(),
            'source_properties_sha256': sha256(image / 'source.properties'),
            'patched_ramdisk_sha256': patched_digest, 'app_root_verified': False,
            'scope': 'SDK metadata, prepared ramdisk and runtime shell checks; app Root requires the fixture test'}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--avd-config', type=Path, required=True)
    parser.add_argument('--prepare-report', type=Path, required=True)
    args = parser.parse_args()
    try:
        result = verify(args.serial, args.adb, args.sdk, args.avd_config,
                        json.loads(args.prepare_report.read_text(encoding='utf8')),
                        json.loads(LOCK_PATH.read_text(encoding='utf8')))
    except (OSError, ValueError, subprocess.TimeoutExpired) as error:
        print(error, file=sys.stderr)
        return 1
    print(json.dumps(result, indent=2))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
