#!/usr/bin/env python3
"""CI only: a loopback, disposable Samba share. Never run on a user's NAS."""
import os
from pathlib import Path
import subprocess

assert os.environ.get('CI') == 'true', 'Use only in isolated CI runners'
root = Path('work/smb-fixture').resolve()
root.mkdir(parents=True, exist_ok=True)
root.chmod(0o777)
subprocess.run(['sudo', 'useradd', '--no-create-home', 'ci'], check=True)
subprocess.run(['sudo', 'smbpasswd', '-a', '-s', 'ci'], input='fixture-only\nfixture-only\n', text=True, check=True)
config = Path('work/smb.conf').resolve()
config.write_text(f'''[global]
  server role = standalone server
  interfaces = lo
  bind interfaces only = yes
  smb ports = 1445
  server min protocol = SMB2
  server signing = mandatory
  log file = {Path('work/samba.log').resolve()}
[backup]
  path = {root}
  read only = no
  valid users = ci
''')
subprocess.Popen(['sudo', '/usr/sbin/smbd', '--foreground', '--no-process-group', '--configfile', str(config)], stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
