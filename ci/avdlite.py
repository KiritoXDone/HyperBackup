#!/usr/bin/env python3
"""Pinned AvdLite platform/UI checks; arm64 backup parity is a separate gate."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import time
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / 'ci/avdlite.lock.json').read_text())
SERIAL = 'emulator-5554'


def verify_archive(path, digest):
    actual = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            actual.update(chunk)
    if actual.hexdigest() != digest:
        raise ValueError('AvdLite release digest mismatch')


def extract_archive(path, destination):
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            target = (destination / item.filename).resolve()
            if not target.is_relative_to(destination.resolve()) or (item.external_attr >> 16) & 0o170000 == 0o120000:
                raise ValueError('Unsafe AvdLite bundle member: ' + item.filename)
        archive.extractall(destination)


def prepare(sdk, work, cache):
    cache.mkdir(parents=True, exist_ok=True)
    archive = cache / LOCK['asset']
    if not archive.exists():
        partial = archive.with_suffix('.partial')
        repository = LOCK['source'].removeprefix('https://github.com/')
        urls = [f"https://api.github.com/repos/{repository}/releases/assets/{LOCK['asset_id']}",
                f"{LOCK['source']}/releases/download/{LOCK['release']}/{LOCK['asset']}"]
        for url in urls:
            result = subprocess.run(['curl', '--fail', '--location', '--retry', '2', '--connect-timeout', '20',
                                     '--max-time', '600', '-H', 'Accept: application/octet-stream',
                                     '--output', str(partial), url])
            if result.returncode == 0:
                break
        else:
            raise RuntimeError('Both publisher download endpoints failed')
        verify_archive(partial, LOCK['sha256'])
        partial.replace(archive)
    verify_archive(archive, LOCK['sha256'])
    bundle = work / 'bundle'
    bundle.mkdir(parents=True)
    extract_archive(archive, bundle)
    importers = list(bundle.rglob('import-avd.py'))
    if len(importers) != 1:
        raise ValueError('Expected one AvdLite importer')
    manifest = json.loads((importers[0].parent / 'manifest.json').read_text())
    if not manifest['ksu'] or str(manifest['image']['api']) != LOCK['api']:
        raise ValueError('AvdLite manifest does not match the pinned KSU image')
    subprocess.run(['python3', str(importers[0]), '--sdk', str(sdk), '--avd-home', str(work / 'avds'),
                    '--name', 'HyperBackupCI'], check=True)
    config = work / 'avds/HyperBackupCI.avd/config.ini'
    values = dict(line.split('=', 1) for line in config.read_text().splitlines() if '=' in line)
    values.update({'hw.ramSize': '2048', 'hw.cpu.ncore': '2', 'fastboot.forceColdBoot': 'yes',
                   'fastboot.forceFastBoot': 'no', 'disk.dataPartition.size': '8G'})
    config.write_text(''.join(f'{key}={value}\n' for key, value in values.items()))
    (work / 'image-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    shutil.rmtree(bundle)


def run(sdk, work):
    evidence = work / 'evidence'
    evidence.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, ANDROID_AVD_HOME=str(work / 'avds'),
               ANDROID_EMULATOR_HOME=str(work / 'emulator-home'),
               ANDROID_SERIAL=SERIAL, QT_QPA_PLATFORM='offscreen')
    Path(env['ANDROID_EMULATOR_HOME']).mkdir(exist_ok=True)
    adb = str(sdk / 'platform-tools/adb')
    process = None
    fixture = '/data/local/tmp/HyperBackupAvdLite-' + uuid.uuid4().hex
    report = {'image': LOCK, 'checks': {}, 'business_parity': 'not_run_arm64_payload_on_x86_64'}

    def call(*args, check=True, timeout=30):
        result = subprocess.run([adb, '-s', SERIAL, *args], env=env, capture_output=True,
                                text=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout.strip()

    def shell(*args, **kwargs):
        return call('shell', shlex.join(args), **kwargs)

    def wait(probe, label, timeout=300):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError(f'Emulator exited: {process.returncode}')
            if probe():
                return
            time.sleep(2)
        raise TimeoutError(label)

    def ready():
        wait(lambda: shell('getprop', 'sys.boot_completed', check=False) == '1', 'Android boot')
        wait(lambda: shell('getprop', 'sys.avdlite.ksu_ready', check=False) == '1', 'KernelSU initialization')
        if 'uid=0' not in shell('su', '-c', 'id'):
            raise AssertionError('KernelSU shell authorization failed')
        if shell('getenforce') != 'Enforcing':
            raise AssertionError('SELinux must remain Enforcing')

    try:
        subprocess.run([adb, 'start-server'], env=env, check=True)
        with (evidence / 'emulator.log').open('w') as log:
            process = subprocess.Popen([str(sdk / 'emulator/emulator'), '-avd', 'HyperBackupCI', '-port', '5554',
                                        '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-no-metrics',
                                        '-gpu', 'swiftshader_indirect', '-feature', '-Vulkan', '-accel', 'on'],
                                       env=env, stdout=log, stderr=subprocess.STDOUT)
        ready()
        report['checks']['cold_boot_root_enforcing'] = True
        report['checks']['abi'] = shell('getprop', 'ro.product.cpu.abi')
        if report['checks']['abi'] != LOCK['abi']:
            raise AssertionError('Unexpected emulator ABI')
        report['checks']['kernel'] = shell('uname', '-r')
        report['checks']['ksu'] = shell('su', '-c', '/data/adb/ksud debug version')
        before = shell('cat', '/proc/sys/kernel/random/boot_id')
        call('reboot')
        wait(lambda: shell('cat', '/proc/sys/kernel/random/boot_id', check=False) not in ('', before), 'Reboot identity')
        ready()
        report['checks']['reboot_root_enforcing'] = True
        shell('mkdir', '-p', fixture)
        call('push', str(ROOT / 'engine/upstream/backup_script/tools/classes.dex'), fixture + '/classes.dex')
        shell('chmod', '444', fixture + '/classes.dex')
        cc = shell('env', 'CLASSPATH=' + fixture + '/classes.dex', '/system/bin/app_process', '/system/bin',
                   'com.xayah.dex.CCUtil', 'selftest', timeout=90)
        (evidence / 'ccutil.txt').write_text(cc + '\n')
        if 'CCUTIL_SELFTEST_OK' not in cc:
            raise AssertionError('Pinned CCUtil self-test did not pass')
        report['checks']['pinned_ccutil'] = True
        shell('input', 'keyevent', 'KEYCODE_WAKEUP')
        shell('wm', 'dismiss-keyguard')
        for key in ('window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'):
            shell('settings', 'put', 'global', key, '0')
        command = ['bash', 'ci/gradle.sh', '-PciUi=true',
                   '-Pandroid.testInstrumentationRunnerArguments.displayScenarios=true',
                   ':app:connectedDebugAndroidTest', '--console=plain']
        subprocess.run(command, cwd=ROOT, env=env, check=True, timeout=900)
        report['checks']['app_ui'] = True
        report['status'] = 'passed'
    except Exception as error:
        report.update(status='failed', error=str(error))
        raise
    finally:
        if process is not None:
            for filename, args in [('logcat.txt', ('logcat', '-d')), ('windows.txt', ('dumpsys', 'window'))]:
                try:
                    (evidence / filename).write_text(shell(*args, check=False))
                except (subprocess.TimeoutExpired, OSError):
                    pass
            try:
                shell('rm', '-rf', fixture, check=False)
                call('pull', '/sdcard/Download/HyperBackupUi', str(evidence / 'ui'), check=False)
                call('emu', 'kill', check=False)
            except (subprocess.TimeoutExpired, OSError):
                pass
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        (evidence / 'report.json').write_text(json.dumps(report, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['prepare', 'run'])
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--work', type=Path, default=ROOT / 'work/avdlite')
    parser.add_argument('--cache', type=Path, default=ROOT / 'work/avdlite-cache')
    args = parser.parse_args()
    if os.environ.get('CI') != 'true' or os.uname().machine != 'x86_64':
        raise SystemExit('Only run in an isolated x86_64 CI runner')
    if args.command == 'prepare':
        prepare(args.sdk.resolve(), args.work.resolve(), args.cache.resolve())
    else:
        run(args.sdk.resolve(), args.work.resolve())
