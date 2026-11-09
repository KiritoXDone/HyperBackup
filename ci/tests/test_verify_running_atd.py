import hashlib
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'atd_4k'))
from verify_running_atd import verify


def digest(data):
    return hashlib.sha256(data).hexdigest()


class RuntimeVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.sdk = root / 'sdk'
        self.image = self.sdk / 'system-images/android-36/aosp_atd/arm64-v8a'
        self.image.mkdir(parents=True)
        self.config = root / 'avds/CI_ATD.avd/config.ini'
        self.config.parent.mkdir(parents=True)
        self.config.write_text('image.sysdir.1=system-images/android-36/aosp_atd/arm64-v8a/\n')
        metadata = b'AndroidVersion.ApiLevel=36\nSystemImage.Abi=arm64-v8a\nSystemImage.TagId=aosp_atd\n'
        kernel, patched = b'fixture kernel', b'fixture ramdisk'
        for name, data in [('source.properties', metadata), ('kernel-ranchu', kernel), ('ramdisk.img', patched)]:
            (self.image / name).write_bytes(data)
        self.lock = {'system_image': {'sdk_package': 'system-images;android-36;aosp_atd;arm64-v8a',
                                      'kernel_release': '6.6.66-android15-8-fixture-4k', 'kmi': 'android15-6.6',
                                      'source_properties_sha256': digest(metadata), 'kernel_sha256': digest(kernel),
                                      'ramdisk_sha256': 'a' * 64},
                     'kernelsu': {'version': 'v3.3.1', 'kernel_version_code': 32680,
                                  'module': {'sha256': 'b' * 64}, 'manager': {'sha256': 'c' * 64},
                                  'host_tools': {'linux-x86_64': {'sha256': 'f' * 64}}}}
        self.prepared = {'sdk_package': self.lock['system_image']['sdk_package'],
                         'kernel_release': self.lock['system_image']['kernel_release'], 'kmi': 'android15-6.6',
                         'expected_page_size': 4096, 'kernelsu': 'v3.3.1', 'module_sha256': 'b' * 64,
                         'kernel_sha256': digest(kernel), 'source_properties_sha256': digest(metadata),
                         'host_tool_platform': 'linux-x86_64', 'host_tool_sha256': 'f' * 64,
                         'stock_ramdisk_sha256': 'a' * 64, 'full_archive_verified': True,
                         'archive_sha256': 'd' * 64, 'patched_ramdisk_sha256': digest(patched)}
        self.commands = {
            ('shell', 'getprop ro.kernel.qemu'): '1', ('emu', 'avd', 'name'): 'CI_ATD\nOK',
            ('shell', 'getprop sys.boot_completed'): '1', ('shell', 'getprop ro.product.cpu.abi'): 'arm64-v8a',
            ('shell', 'getprop ro.build.version.sdk'): '36', ('shell', 'getconf PAGESIZE'): '4096',
            ('shell', 'uname -r'): self.lock['system_image']['kernel_release'], ('shell', 'getenforce'): 'Enforcing',
            ('shell', 'su -c id'): 'uid=0(root) gid=0(root)',
            ('shell', "su -c '/data/adb/ksud debug version'"): 'Kernel Version: 32680',
            ('shell', 'pm path me.weishu.kernelsu'): 'package:/data/app/~~abc/me.weishu.kernelsu-xyz/base.apk',
            ('shell', "su -c 'sha256sum /data/app/~~abc/me.weishu.kernelsu-xyz/base.apk'"): 'c' * 64 + '  base.apk',
        }
        self.calls = []

    def runner(self, command, **kwargs):
        self.calls.append(command)
        return SimpleNamespace(returncode=0, stdout=self.commands[tuple(command[3:])], stderr='')

    def run_verify(self, serial='emulator-5554'):
        return verify(serial, 'adb', self.sdk, self.config, self.prepared, self.lock, runner=self.runner)

    def test_valid_environment_does_not_claim_app_root(self):
        report = self.run_verify()
        self.assertEqual(4096, report['page_size'])
        self.assertTrue(report['shell_root_verified'])
        self.assertFalse(report['app_root_verified'])

    def test_never_queries_a_physical_device(self):
        with self.assertRaisesRegex(ValueError, 'explicit emulator serial'):
            self.run_verify('192.0.2.1:5555')
        self.assertFalse(self.calls)

    def test_rejects_16kb_x86_and_non_emulators(self):
        for key, value, message in [
            (('shell', 'getprop ro.kernel.qemu'), '0', 'as an emulator'),
            (('shell', 'getprop ro.product.cpu.abi'), 'x86_64', 'API or ABI'),
            (('shell', 'getconf PAGESIZE'), '16384', '4 KB guest'),
        ]:
            with self.subTest(key=key):
                old = self.commands[key]
                self.commands[key] = value
                with self.assertRaisesRegex(ValueError, message):
                    self.run_verify()
                self.commands[key] = old

    def test_rejects_wrong_root_kernel_and_manager(self):
        for key, value, message in [
            (('shell', 'su -c id'), 'uid=2000(shell)', 'shell Root'),
            (('shell', "su -c '/data/adb/ksud debug version'"), 'Kernel Version: 0', 'kernel version'),
            (('shell', "su -c 'sha256sum /data/app/~~abc/me.weishu.kernelsu-xyz/base.apk'"), 'e' * 64, 'official APK'),
        ]:
            with self.subTest(key=key):
                old = self.commands[key]
                self.commands[key] = value
                with self.assertRaisesRegex(ValueError, message):
                    self.run_verify()
                self.commands[key] = old

    def test_rejects_unverified_archive_and_changed_ramdisk(self):
        self.prepared['full_archive_verified'] = False
        with self.assertRaisesRegex(ValueError, 'full official image'):
            self.run_verify()
        self.assertFalse(self.calls)
        self.prepared['full_archive_verified'] = True
        (self.image / 'ramdisk.img').write_bytes(b'changed')
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            self.run_verify()

    def test_rejects_other_system_image_and_avd(self):
        self.config.write_text('image.sysdir.1=system-images/android-36/google_apis/arm64-v8a/\n')
        with self.assertRaisesRegex(ValueError, 'pinned official ARM64 ATD'):
            self.run_verify()
        self.commands[('emu', 'avd', 'name')] = 'ANOTHER_AVD\nOK'
        with self.assertRaisesRegex(ValueError, 'AVD config does not match'):
            self.run_verify()


if __name__ == '__main__':
    unittest.main()
