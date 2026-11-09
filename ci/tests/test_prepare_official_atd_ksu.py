import gzip
import hashlib
from pathlib import Path
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
from zipfile import ZipFile
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'atd_4k'))
from prepare_official_atd_ksu import cpio_entries, prepare


def digest(data):
    return hashlib.sha256(data).hexdigest()


def entry(name, data):
    name = name.encode() + b'\0'
    fields = [1, 0o100644, 0, 0, 1, 0, len(data), 0, 0, 0, 0, len(name), 0]
    result = b'070701' + b''.join(f'{field:08x}'.encode() for field in fields) + name
    result += b'\0' * (-len(result) % 4)
    result += data
    return result + b'\0' * (-len(result) % 4)


class RamdiskPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.image = self.root / 'sdk-image'
        self.image.mkdir()
        self.output_dir = self.root / 'output'
        self.output_dir.mkdir()
        self.output = self.output_dir / 'ramdisk.img'
        self.ksud = self.root / 'ksud'
        self.module = self.root / 'module.ko'
        self.ksud.write_bytes(b'pinned-host-tool')
        self.module.write_bytes(b'pinned-kernel-module')
        self.release = '6.6.66-android15-8-example-4k'
        config = gzip.compress(b'CONFIG_ARM64_4K_PAGES=y\nCONFIG_MODULES=y\n')
        kernel = gzip.compress(b'Linux version ' + self.release.encode() + b'\0IKCFG_ST' + config + b'IKCFG_ED')
        files = {'source.properties': b'official fixture metadata', 'kernel-ranchu': kernel, 'ramdisk.img': b'original ramdisk',
                 'system.img': b'fixture system image', 'vendor.img': b'fixture vendor image'}
        for name, data in files.items():
            (self.image / name).write_bytes(data)
        self.lock = {
            'system_image': {'sdk_package': 'fixture', 'source_properties_sha256': digest(files['source.properties']),
                             'kernel_sha256': digest(files['kernel-ranchu']), 'ramdisk_sha256': digest(files['ramdisk.img']),
                             'kernel_release': self.release, 'kmi': 'android15-6.6', 'expected_page_size': 4096},
            'kernelsu': {'version': 'v3.3.1', 'module': {'sha256': digest(self.module.read_bytes())},
                         'host_tools': {'test-host': {'sha256': digest(self.ksud.read_bytes())}}}}
        self.archive = self.root / 'official.zip'
        with ZipFile(self.archive, 'w') as archive:
            for name, data in files.items():
                archive.writestr('arm64-v8a/' + name, data)
        self.pin_archive()
        self.calls = []

    def pin_archive(self):
        data = self.archive.read_bytes()
        self.lock['system_image']['archive_size'] = len(data)
        self.lock['system_image']['archive_sha1_google_index'] = hashlib.sha1(data, usedforsecurity=False).hexdigest()

    def runner(self, command, **kwargs):
        self.calls.append(command)
        if command[0] == 'lz4':
            raw = (entry('ksu_config', b'allow_shell=1') + entry('kernelsu.ko', self.module.read_bytes()) +
                   entry('TRAILER!!!', b''))
            return SimpleNamespace(stdout=raw)
        target = Path(command[command.index('--out') + 1]) / command[command.index('--out-name') + 1]
        target.write_bytes(b'patched ramdisk')
        return SimpleNamespace(stdout=b'', stderr=b'')

    def run_prepare(self, **kwargs):
        kwargs.setdefault('archive', self.archive)
        return prepare(self.image, self.ksud, self.module, self.output, self.lock,
                       host='test-host', runner=kwargs.pop('runner', self.runner), **kwargs)

    def test_produces_separate_overlay_and_preserves_original(self):
        report = self.run_prepare()
        self.assertEqual(b'original ramdisk', (self.image / 'ramdisk.img').read_bytes())
        self.assertEqual(b'patched ramdisk', self.output.read_bytes())
        self.assertFalse(report['boot_verified'])
        self.assertFalse(report['app_root_verified'])
        self.assertTrue(report['full_archive_verified'])
        self.assertEqual(digest(self.archive.read_bytes()), report['archive_sha256'])
        self.assertEqual(4096, report['expected_page_size'])
        self.assertIn('--allow-shell', self.calls[0])
        self.assertEqual('android15-6.6', self.calls[0][self.calls[0].index('--kmi') + 1])

    def test_rejects_modified_module_before_running_patcher(self):
        self.module.write_bytes(b'tampered')
        with self.assertRaisesRegex(ValueError, 'checksum mismatch'):
            self.run_prepare()
        self.assertFalse(self.calls)
        self.assertFalse(self.output.exists())

    def test_rejects_existing_output_and_writes_inside_sdk(self):
        self.output.write_bytes(b'existing file')
        with self.assertRaisesRegex(ValueError, 'Output must be new'):
            self.run_prepare()
        self.assertEqual(b'existing file', self.output.read_bytes())
        with self.assertRaisesRegex(ValueError, 'must not overwrite'):
            prepare(self.image, self.ksud, self.module, self.image / 'new.img', self.lock, host='test-host', runner=self.runner)

    def test_rejects_16kb_kernel_even_if_its_hash_matches(self):
        config = gzip.compress(b'CONFIG_ARM64_16K_PAGES=y\nCONFIG_MODULES=y\n')
        kernel = gzip.compress(b'Linux version ' + self.release.encode() + b'\0IKCFG_ST' + config + b'IKCFG_ED')
        (self.image / 'kernel-ranchu').write_bytes(kernel)
        self.lock['system_image']['kernel_sha256'] = digest(kernel)
        with self.assertRaisesRegex(ValueError, '4 KB pages'):
            self.run_prepare()
        self.assertFalse(self.calls)

    def test_rejects_missing_module_or_config_in_output(self):
        def missing_module(command, **kwargs):
            if command[0] == 'lz4':
                return SimpleNamespace(stdout=entry('ksu_config', b'allow_shell=1') + entry('TRAILER!!!', b''))
            return self.runner(command, **kwargs)
        with self.assertRaisesRegex(ValueError, 'lacks the pinned module'):
            self.run_prepare(runner=missing_module)
        self.assertFalse(self.output.exists())

    def test_rejects_corrupt_cpio_and_patch_failure(self):
        with self.assertRaisesRegex(ValueError, 'cpio header'):
            cpio_entries(b'070701bad')
        def fail(command, **kwargs):
            raise subprocess.CalledProcessError(1, command, stderr=b'patch failed')
        with self.assertRaisesRegex(RuntimeError, 'patch failed'):
            self.run_prepare(runner=fail)
        self.assertFalse(self.output.exists())

    def test_requires_full_archive_in_ci(self):
        with mock.patch.dict('os.environ', {'CI': 'true'}):
            with self.assertRaisesRegex(ValueError, 'archive is required'):
                self.run_prepare(archive=None, components_only=True)
        self.assertFalse(self.calls)

    def test_rejects_tampered_non_kernel_system_file(self):
        (self.image / 'system.img').write_bytes(b'changed system image')
        with self.assertRaisesRegex(ValueError, 'differs from archive'):
            self.run_prepare()
        self.assertFalse(self.calls)

    def test_rejects_archive_digest_mismatch(self):
        self.archive.write_bytes(self.archive.read_bytes() + b'tampered')
        with self.assertRaisesRegex(ValueError, 'archive size mismatch'):
            self.run_prepare()
        self.assertFalse(self.calls)

    def test_rejects_traversal_even_with_matching_archive_digest(self):
        with ZipFile(self.archive, 'a') as archive:
            archive.writestr('../escape', b'bad')
        self.pin_archive()
        with self.assertRaisesRegex(ValueError, 'Unexpected official ATD archive member'):
            self.run_prepare()
        self.assertFalse(self.calls)


if __name__ == '__main__':
    unittest.main()
