#!/usr/bin/env python3
"""CI only: a loopback, disposable Samba share. Never run on a user's NAS."""
import os
from pathlib import Path
import pwd
import socket
import subprocess
import time

assert os.environ.get('CI') == 'true', 'Use only in isolated CI runners'
root = Path('work/smb-fixture').resolve()
root.mkdir(parents=True, exist_ok=True)
root.chmod(0o777)
runtime = Path('work/smb-runtime').resolve()
for directory in ['pid', 'lock', 'state', 'cache', 'private']:
    runtime.joinpath(directory).mkdir(parents=True, exist_ok=True)
try:
    pwd.getpwnam('ci')
except KeyError:
    subprocess.run(['sudo', 'useradd', '--no-create-home', 'ci'], check=True)
config = Path('work/smb.conf').resolve()
config.write_text(f'''[global]
  server role = standalone server
  interfaces = 127.0.0.1/8
  bind interfaces only = yes
  disable netbios = yes
  smb ports = 1445
  server min protocol = SMB2
  server signing = mandatory
  pid directory = {runtime / 'pid'}
  lock directory = {runtime / 'lock'}
  state directory = {runtime / 'state'}
  cache directory = {runtime / 'cache'}
  private dir = {runtime / 'private'}
  log file = {Path('work/samba.log').resolve()}
[backup]
  path = {root}
  read only = no
  valid users = ci
''')
subprocess.run(['testparm', '--suppress-prompt', str(config)], check=True)
subprocess.run(['sudo', 'smbpasswd', '-c', str(config), '-a', '-s', 'ci'],
               input='fixture-only\nfixture-only\n', text=True, check=True)
# Foreground smbd treats EOF on a pipe as parent death; use a non-pipe stdin.
with Path('work/smbd.log').open('w') as log:
    process = subprocess.Popen(['sudo', '/usr/sbin/smbd', '--foreground', '--no-process-group', '--configfile', str(config)],
                               stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    for _ in range(100):
        if process.poll() is not None:
            raise RuntimeError('Samba fixture exited; inspect work/smbd.log and work/samba.log')
        try:
            with socket.create_connection(('127.0.0.1', 1445), timeout=0.2):
                print('Isolated Samba fixture is ready on 127.0.0.1:1445')
                break
        except OSError:
            time.sleep(0.2)
    else:
        process.terminate()
        raise RuntimeError('Samba fixture did not become ready')
