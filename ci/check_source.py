#!/usr/bin/env python3
"""Small, deterministic source/packaging guardrails; no user data or network."""
from pathlib import Path
import hashlib
import subprocess

root = Path(__file__).resolve().parents[1]
upstream = root / 'engine/upstream/backup_script'
expected = '4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5'
assert subprocess.check_output(['git', '-C', str(upstream), 'rev-parse', 'HEAD'], text=True).strip() == expected
for line in (root / 'engine/upstream/SHA256SUMS').read_text().splitlines():
    digest, name = line.split('  ')
    assert hashlib.sha256((upstream / 'tools' / name).read_bytes()).hexdigest() == digest, name
for module in ['domain', 'ui']:
    for source in (root / 'core' / module / 'src/main').rglob('*.kt'):
        text = source.read_text()
        assert 'hyperbackup.engine.' not in text and 'hyperbackup.data.' not in text, source
        if module == 'domain':
            assert 'import android.' not in text and 'import androidx.' not in text, source
for source in list((root / 'app/src/main').rglob('*.kt')) + list((root / 'core').glob('*/src/main/**/*.kt')):
    assert len(source.read_text().splitlines()) <= 400, f'Split responsibilities: {source}'
for retired in ['ApkInstaller.kt', 'BackupCapture.kt', 'DataRestore.kt', 'RestoreOperation.kt', 'RootJobExecutor.kt']:
    assert not (root / 'core/engine/src/main/kotlin/io/github/kiritoxdone/hyperbackup/engine/root' / retired).exists(), retired
assert 'ScriptJobExecutor' in (root / 'core/engine/src/main/kotlin/io/github/kiritoxdone/hyperbackup/engine/root/HyperBackupRootService.kt').read_text()
print('Pinned payload, dependency boundaries, full-engine dispatch and file-size gates passed.')
