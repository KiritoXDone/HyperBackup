from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parents[1]
metadata = json.loads((root / 'engine/generated/provenance.json').read_text())
assert metadata['upstream'] == '4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5'
assert metadata['rust'] == '1.99.1' and metadata['ndk'] == '30.0.16248370' and metadata['api'] == 28
assert metadata['sha256'] == hashlib.sha256((root / 'engine/generated/speednative').read_bytes()).hexdigest()
assert metadata['patchFingerprint'] == hashlib.sha256(b''.join((root / 'engine/patches' / name).read_bytes() for name in ['build_native.py', 'hyperbackup_host.rs'])).hexdigest()
print('Native host adapter provenance matches source.')
