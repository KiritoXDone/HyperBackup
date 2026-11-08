#!/usr/bin/env python3
"""Validate a release before signing/publication; keep APK identity tied to its tag."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def version():
    build = (ROOT / 'app/build.gradle.kts').read_text()
    name = re.search(r'versionName\s*=\s*"([^"]+)"', build).group(1)
    code = int(re.search(r'versionCode\s*=\s*(\d+)', build).group(1))
    return name, code

def notes(changelog, name):
    blocks = re.split(r'^##\s+', changelog, flags=re.M)[1:]
    matches = [block.split('\n', 1)[1].strip() for block in blocks if block.splitlines()[0].strip() == name]
    if len(matches) != 1 or not matches[0]:
        raise ValueError('A unique, non-empty changelog section is required')
    return matches[0] + '\n'

def prepare(tag):
    name, code = version()
    if tag != 'v' + name or not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[a-z0-9.]+)?', tag):
        raise ValueError('Tag must match the APK versionName')
    if '-' not in name:
        subprocess.run(['python3', 'ci/check_coverage.py', '--release'], cwd=ROOT, check=True)
    output = ROOT / 'work/release'
    output.mkdir(parents=True, exist_ok=True)
    output.joinpath('notes.md').write_text(notes(ROOT.joinpath('changelog.md').read_text(), name))
    return name, code, output

def package(tag, apk, sdk):
    name, code, output = prepare(tag)
    tools = Path(sdk) / 'build-tools/37.0.0'
    badging = subprocess.check_output([str(tools / 'aapt2'), 'dump', 'badging', apk], text=True)
    expected = f"package: name='io.github.kiritoxdone.hyperbackup' versionCode='{code}' versionName='{name}'"
    if not badging.startswith(expected):
        raise ValueError('APK package/version differs from release source')
    if "native-code: 'arm64-v8a'" not in badging or "'x86_64'" in badging:
        raise ValueError('Release must contain only the supported arm64 ABI')
    cert = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--verbose', '--print-certs', apk], text=True)
    digests = re.findall(r'certificate SHA-256 digest: ([a-f0-9]{64})', cert)
    if set(digests) != {ROOT.joinpath('ci/signing-cert.sha256').read_text().strip()}:
        raise ValueError('APK signature differs from the pinned release certificate')
    subprocess.run(['python3', 'ci/verify_native.py'], cwd=ROOT, check=True)
    target = output / f'HyperBackup-{name}-arm64-v8a.apk'
    shutil.copyfile(apk, target)
    output.joinpath('SHA256SUMS').write_text(f'{hashlib.sha256(target.read_bytes()).hexdigest()}  {target.name}\n')
    provenance = json.loads(ROOT.joinpath('engine/generated/provenance.json').read_text())
    provenance.update(commit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(), tag=tag,
                      apkSha256=hashlib.sha256(target.read_bytes()).hexdigest())
    output.joinpath('provenance.json').write_text(json.dumps(provenance, indent=2) + '\n')
    return target

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['prepare', 'package'])
    parser.add_argument('--tag', default='v' + version()[0])
    parser.add_argument('--apk', default='app/build/outputs/apk/release/app-release.apk')
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME'))
    args = parser.parse_args()
    if args.mode == 'prepare':
        prepare(args.tag)
    else:
        if not args.sdk:
            parser.error('--sdk or ANDROID_HOME is required')
        print(package(args.tag, args.apk, args.sdk))
