import importlib.util
from pathlib import Path
import unittest
import tempfile
import hashlib
import json
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'ci' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
release = load('release')
coverage = load('check_coverage')

class ReleaseContracts(unittest.TestCase):
    def test_exact_changelog_section(self):
        self.assertEqual('- One\n', release.notes('# Changes\n\n## 1.2.3\n\n- One\n\n## 1.2.2\n\n- Old\n', '1.2.3'))
    def test_missing_duplicate_or_empty_notes_fail(self):
        for text in ['## 1.2.2\n- Other', '## 1.2.3\n', '## 1.2.3\n- One\n## 1.2.3\n- Two']:
            with self.assertRaises(ValueError): release.notes(text, '1.2.3')
    def test_pending_capability_blocks_formal_release(self):
        manifest = {'upstream': 'a'*40, 'capabilities': [{'id': 'CAP-01', 'operations': ['backup'], 'status': 'pending', 'evidence': []}]}
        coverage.validate(manifest, {'CAP-01'}, {'backup'})
        with self.assertRaises(ValueError): coverage.validate(manifest, {'CAP-01'}, {'backup'}, release=True)
        with self.assertRaises(ValueError): coverage.validate(manifest, {'CAP-01'}, {'backup', 'restore'})
    def test_verified_requires_evidence(self):
        manifest = {'upstream': 'a'*40, 'capabilities': [{'id': 'CAP-01', 'operations': ['backup'], 'status': 'verified', 'evidence': []}]}
        with self.assertRaises(ValueError): coverage.validate(manifest, {'CAP-01'}, {'backup'}, release=True)
    def test_wrong_version_tag_is_rejected(self):
        with self.assertRaises(ValueError): release.prepare('v999.0.0')

    def test_apk_payload_must_match_both_manifest_and_native_provenance(self):
        binary = b'pinned-native'
        native = {'sha256': hashlib.sha256(binary).hexdigest()}
        provenance = json.dumps(native).encode()
        files = {'speednative': binary, 'provenance.json': provenance}
        manifest = ''.join(f'{hashlib.sha256(body).hexdigest()}  {name}\n' for name, body in files.items())
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'fixture.apk'
            for tampered in [False, True]:
                with ZipFile(apk, 'w') as archive:
                    for name, body in files.items():
                        archive.writestr('assets/upstream/' + name, b'changed' if tampered and name == 'speednative' else body)
                    archive.writestr('assets/upstream/PAYLOAD-SHA256SUMS', manifest)
                if tampered:
                    with self.assertRaises(ValueError): release.verify_payload(apk, native)
                else:
                    release.verify_payload(apk, native)
                    with self.assertRaises(ValueError): release.verify_payload(apk, {'sha256': '0' * 64})
