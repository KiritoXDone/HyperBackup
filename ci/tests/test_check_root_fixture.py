import tempfile
import unittest
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'atd_4k'))
from check_root_fixture import CLASS, METHOD, validate


class RequiredRootFixtureTest(unittest.TestCase):
    def result(self, directory, child='', status=''):
        path = Path(directory) / 'TEST-device.xml'
        path.write_text(f'<testsuite><testcase classname="{CLASS}" name="{METHOD}" status="{status}">{child}</testcase></testsuite>')
        return path

    def test_pass_requires_real_testcase(self):
        with tempfile.TemporaryDirectory() as directory:
            validate([self.result(directory)])
            with self.assertRaisesRegex(ValueError, 'found 0'):
                validate([])

    def test_skips_and_assumptions_are_not_success(self):
        with tempfile.TemporaryDirectory() as directory:
            for child in ['<skipped/>', '<failure>org.junit.AssumptionViolatedException</failure>']:
                with self.assertRaisesRegex(ValueError, 'skipped'):
                    validate([self.result(directory, child)])
            with self.assertRaisesRegex(ValueError, 'not run'):
                validate([self.result(directory, status='notrun')])

    def test_failed_duplicated_and_broken_results_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.result(directory, '<failure>restore was wrong</failure>')
            with self.assertRaisesRegex(ValueError, 'failed'):
                validate([path])
            path = self.result(directory, status='failed')
            with self.assertRaisesRegex(ValueError, 'failed'):
                validate([path])
            path = self.result(directory)
            with self.assertRaisesRegex(ValueError, 'found 2'):
                validate([path, path])
            path.write_text('<bad')
            with self.assertRaisesRegex(ValueError, 'Cannot read'):
                validate([path])


if __name__ == '__main__':
    unittest.main()
