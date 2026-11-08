#!/usr/bin/env python3
"""Keep the public parity inventory and release gate honest."""
import argparse
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]

def validate(manifest, required, commands, release=False):
    rows = manifest['capabilities']
    ids = [row['id'] for row in rows]
    if len(ids) != len(set(ids)) or set(ids) != required:
        raise ValueError('Coverage IDs must match every documented capability exactly')
    mapped = {command for row in rows for command in row['operations']}
    if mapped != commands:
        raise ValueError(f'Unmapped or obsolete commands: {mapped ^ commands}')
    for row in rows:
        if row['status'] not in {'pending', 'in_progress', 'verified', 'failed'}:
            raise ValueError(f"Invalid status: {row['id']}")
        if row['status'] == 'verified':
            if not row['evidence']:
                raise ValueError(f"Missing evidence: {row['id']}")
            for evidence in row['evidence']:
                if evidence['upstream'] != manifest['upstream'] or not re.fullmatch(r'[a-f0-9]{40}', evidence['commit']):
                    raise ValueError(f"Unpinned evidence: {row['id']}")
                path = (ROOT / evidence['path']).resolve()
                if not path.is_relative_to(ROOT / 'validation') or not path.is_file():
                    raise ValueError(f"Missing evidence file: {row['id']}")
    if release and any(row['status'] != 'verified' for row in rows):
        raise ValueError('Formal release blocked: incomplete upstream capability verification')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--release', action='store_true')
    args = parser.parse_args()
    manifest = json.loads(ROOT.joinpath('ci/coverage.json').read_text())
    if manifest['upstream'] != '4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5':
        raise ValueError('Coverage baseline differs from the bundled upstream')
    source = ROOT.joinpath('core/domain/src/main/kotlin/io/github/kiritoxdone/hyperbackup/domain/ScriptContracts.kt').read_text()
    commands = set(re.findall(r'[A-Z_]+\("([a-z_]+)",', source))
    required = set(re.findall(r'^\| (CAP-\d+) \|', ROOT.joinpath('docs/UPSTREAM-PARITY.md').read_text(), re.M))
    runtime = ROOT.joinpath('engine/upstream/backup_script/rust/src/runtime_conf.rs').read_text()
    keys = set(re.search(r'const KNOWN:&str="([^"]+)"', runtime).group(1).split('|'))
    if set(manifest.get('configuration', {})) != keys:
        raise ValueError('Every upstream configuration key must have an explicit application owner')
    if not set(manifest['configuration'].values()) <= {'app_runtime', 'location_or_task', 'persistent_setting'}:
        raise ValueError('Unknown configuration owner')
    validate(manifest, required, commands, args.release)
    print(f'{len(required)} capabilities mapped; {sum(r["status"] == "verified" for r in manifest["capabilities"])} verified.')
