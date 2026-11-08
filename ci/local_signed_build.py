"""Local developer build using the private, ignored key. Never prints credentials."""
from pathlib import Path
import json
import os
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
key = root / 'work/signing/hyperbackup.p12'
credentials = json.loads((root / 'work/signing/credentials.json').read_text())
environment = os.environ.copy()
environment['HYPERBACKUP_KEYSTORE_PATH'] = str(key)
environment['HYPERBACKUP_KEYSTORE_PASSWORD'] = credentials['password']
subprocess.run([str(root / 'gradlew'), *sys.argv[1:]], cwd=root, env=environment, check=True)
