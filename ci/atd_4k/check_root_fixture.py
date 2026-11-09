#!/usr/bin/env python3
"""Require explicit execution of the disposable Root fixture test.

Supply only XML results from one emulator run. A missing, skipped,
assumption-violated, duplicated, failed, or malformed result is an error.
This checks test execution only. The caller must independently verify ARM64 and KernelSU.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

CLASS = 'io.github.kiritoxdone.hyperbackup.RootFixtureTest'
METHOD = 'nativeBackupRestoreWithSafetyCopy'


def validate(paths: list[Path]) -> None:
    found = []
    for path in paths:
        try:
            root = ET.parse(path).getroot()
        except (OSError, ET.ParseError) as error:
            raise ValueError(f'Cannot read test results: {path}') from error
        for case in root.iter('testcase'):
            if case.get('classname') == CLASS and case.get('name') == METHOD:
                found.append((path, case))
    if len(found) != 1:
        raise ValueError(f'Expected exactly one {CLASS}.{METHOD} result; found {len(found)}')
    path, case = found[0]
    statuses = {case.get(key, '').lower() for key in ('status', 'result')}
    if statuses & {'skipped', 'ignored', 'disabled', 'notrun'}:
        raise ValueError(f'Required root fixture was not run: {path}')
    if statuses & {'failed', 'failure', 'error'}:
        raise ValueError(f'Required root fixture failed: {path}')
    failed = [node for node in case if node.tag.rsplit('}', 1)[-1] in {'skipped', 'failure', 'error'}]
    if failed:
        details = ' '.join((node.get('message', '') + ' ' + ''.join(node.itertext())).strip() for node in failed)
        reason = 'skipped' if any(node.tag.rsplit('}', 1)[-1] == 'skipped' for node in failed) or 'AssumptionViolatedException' in details else 'failed'
        raise ValueError(f'Required root fixture {reason}: {path}')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('result_dir', type=Path, help='One emulator run androidTest-results directory')
    args = parser.parse_args()
    if not args.result_dir.is_dir():
        parser.error(f'Not a result directory: {args.result_dir}')
    paths = sorted(args.result_dir.rglob('TEST-*.xml'))
    try:
        validate(paths)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    print('Required disposable root backup/restore fixture executed and passed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
