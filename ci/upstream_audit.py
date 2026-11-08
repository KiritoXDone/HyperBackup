#!/usr/bin/env python3
"""Read-only upstream drift report. A candidate is never checked out or activated."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import subprocess

root = Path(__file__).resolve().parents[1]
upstream = root / 'engine/upstream/backup_script'
baseline = '4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5'
parser = argparse.ArgumentParser()
parser.add_argument('--candidate', default=baseline)
args = parser.parse_args()
assert re.fullmatch('[0-9a-f]{40}', args.candidate)
def git(*arguments):
    return subprocess.check_output(['git', '-C', str(upstream), *arguments], text=True)
if args.candidate != baseline:
    subprocess.run(['git', '-C', str(upstream), 'fetch', '--depth', '1', 'origin', args.candidate], check=True)
paths = ['README.md', 'tools/tools.sh', 'versions.properties', 'ci/toolchain.json',
    'rust/src/runtime_conf.rs', 'rust/src/pm_session.rs', 'rust/src/applist_web.rs', 'rust/src/json_ops.rs',
    'dex/app/src/main/java/com/xayah/dex/AppStateEngine.java', 'dex/app/src/main/java/com/xayah/dex/AppListReturn.java',
    'webview/app/src/main/assets/settings-labels.json', 'webview/app/src/main/assets/settings-options.json']
def snapshot(sha):
    files = {path: git('show', f'{sha}:{path}') for path in paths}
    shell = files['tools/tools.sh']
    menus = {kind: re.search(r"<<'SPEEDBACKUP_COMMANDS_" + kind + r"'\n(.*?)\nSPEEDBACKUP_COMMANDS_" + kind, shell, re.S).group(1).splitlines() for kind in ['BACKUP', 'RESTORE']}
    keys = re.search(r'const KNOWN:&str="([^"]+)"', files['rust/src/runtime_conf.rs']).group(1).split('|')
    defaults = re.findall(r'_conf_write ([A-Za-z_][A-Za-z0-9_]*) (.*)', shell)
    return {'sha': sha, 'menus': menus, 'configKeys': keys, 'defaultExpressions': defaults,
        'versions': files['versions.properties'], 'toolchain': json.loads(files['ci/toolchain.json']),
        'fileHashes': {path: hashlib.sha256(text.encode()).hexdigest() for path, text in files.items()}}
old, new = snapshot(baseline), snapshot(args.candidate)
changed = [path for path in paths if old['fileHashes'][path] != new['fileHashes'][path]]
report = {'baseline': old, 'candidate': new, 'changedEvidenceFiles': changed,
    'status': 'BASELINE_AUDITED_NOT_A_RELEASE_APPROVAL' if not changed else 'UNCLASSIFIED_CHANGES_BLOCK_RELEASE',
    'note': '菜单、字段、默认表达式和版本变化仅为审查输入；不代表新版本已受支持。'}
output = root / 'work/upstream-audit.json'
output.parent.mkdir(exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(f"Audited {len(paths)} evidence files, {len(new['configKeys'])} public configuration keys; {len(changed)} changed evidence files.")
