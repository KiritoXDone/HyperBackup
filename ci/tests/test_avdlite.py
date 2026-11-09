import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('avdlite_ci', Path(__file__).resolve().parents[1] / 'avdlite.py')
avdlite = importlib.util.module_from_spec(spec)
spec.loader.exec_module(avdlite)


class AvdLiteBundleTest(unittest.TestCase):
    def test_modified_cached_image_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'image.zip'
            archive.write_bytes(b'original')
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            avdlite.verify_archive(archive, digest)
            archive.write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'digest mismatch'):
                avdlite.verify_archive(archive, digest)

    def test_bundle_cannot_escape_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'image.zip'
            with zipfile.ZipFile(archive, 'w') as bundle:
                bundle.writestr('../escape', b'bad')
            with self.assertRaisesRegex(ValueError, 'Unsafe'):
                avdlite.extract_archive(archive, Path(directory) / 'bundle')
            self.assertFalse((Path(directory) / 'escape').exists())

    def test_bundle_rejects_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            archive = Path(directory) / 'image.zip'
            with zipfile.ZipFile(archive, 'w') as bundle:
                link = zipfile.ZipInfo('link')
                link.external_attr = 0o120777 << 16
                bundle.writestr(link, '../outside')
            with self.assertRaisesRegex(ValueError, 'Unsafe'):
                avdlite.extract_archive(archive, Path(directory) / 'bundle')
