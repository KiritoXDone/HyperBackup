import importlib.util
from pathlib import Path
import unittest
import tempfile
import json

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('patch', ROOT / 'engine/patches/generate.py')
patch = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patch)

class PatchContract(unittest.TestCase):
    def test_all_anchors_match_pinned_source(self):
        pristine = patch.SOURCE.read_text()
        generated = patch.generate(pristine)
        self.assertIn('_hb_dispatch "$HYPERBACKUP_COMMAND"', generated)
        self.assertIn('exit 75; }', generated)
        self.assertLess(len(generated.splitlines()) - len(pristine.splitlines()), 50)

    def test_business_function_bodies_are_retained(self):
        pristine = patch.SOURCE.read_text()
        generated = patch.generate(pristine)
        for name, end in [('backup', '# 增量備份:'), ('remote_test_menu', '\n#')]:
            body = pristine[pristine.index('\n' + name + '() {') + 1:]
            body = body[:body.index(end)]
            self.assertTrue(body in generated, 'Business body changed: ' + name)

    def test_source_drift_is_a_build_failure(self):
        with self.assertRaises(AssertionError):
            patch.generate(patch.SOURCE.read_text().replace('_backup_entry_event() {', '_backup_entry_event_v2() {'))

    def test_defaults_exclude_template_comments_and_preserve_source_values(self):
        with tempfile.TemporaryDirectory() as directory:
            patch.main(directory)
            root = Path(directory)
            defaults = json.loads((root / 'config-defaults.json').read_text())
            keys = set((root / 'config-keys.txt').read_text().splitlines())
            self.assertTrue(set(defaults['backup']).issubset(keys))
            self.assertIn('whitelist', defaults['backup'])
            self.assertIn('system', defaults['backup'])
            self.assertEqual('1', defaults['backup']['backup_messages'])
            self.assertEqual('0', defaults['restore']['restore_messages'])

    def test_dispatch_contains_only_fixed_business_entries(self):
        text = (ROOT / 'engine/patches/bridge.sh').read_text()
        dispatch = text[text.index('_hb_dispatch()'):]
        self.assertNotIn('eval ', dispatch)
        for command in ['backup', 'Restore', 'wifi', 'Restore4', 'remote_stream_restore', 'backup_update_apk', 'delete_remote_uninstalled_apps']:
            self.assertIn(command, dispatch)

if __name__ == '__main__': unittest.main()
