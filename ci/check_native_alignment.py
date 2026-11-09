#!/usr/bin/env python3
"""Check APK JNI LOAD/RELRO segments and uncompressed ZIP entries for 16 KB pages."""
import argparse
from pathlib import Path
import struct
from zipfile import ZipFile, ZIP_STORED

PAGE = 16384
LOAD = 1
RELRO = 0x6474E552


def check_elf(data, name):
    if data[:6] != b'\x7fELF\x02\x01' or len(data) < 64:
        raise ValueError(f'{name}: expected little-endian ELF64')
    offset = struct.unpack_from('<Q', data, 32)[0]
    stride, count = struct.unpack_from('<HH', data, 54)
    if stride < 56 or not 0 < count <= 4096 or offset + stride * count > len(data):
        raise ValueError(f'{name}: invalid program-header table')
    headers = [struct.unpack_from('<IIQQQQQQ', data, offset + stride * i) for i in range(count)]
    loads = [h for h in headers if h[0] == LOAD]
    if not loads:
        raise ValueError(f'{name}: no LOAD segments')
    for _, _, file_offset, address, _, _, _, alignment in loads:
        if alignment < PAGE or (address - file_offset) % PAGE:
            raise ValueError(f'{name}: LOAD is not 16 KB aligned')
    for kind, _, _, address, _, _, memory_size, _ in headers:
        if kind != RELRO:
            continue
        end = address + memory_size
        if end % PAGE == 0:
            continue
        # Android also accepts RELRO ending exactly with its containing LOAD,
        # since no writable data then shares the protected trailing page.
        if not any(h[3] <= address < h[3] + h[6] and h[3] + h[6] == end for h in loads):
            raise ValueError(f'{name}: RELRO does not end at a 16 KB or LOAD boundary')


def check_apk(path):
    checked = []
    with ZipFile(path) as apk, Path(path).open('rb') as raw:
        for entry in apk.infolist():
            if not entry.filename.startswith('lib/') or not entry.filename.endswith('.so'):
                continue
            check_elf(apk.read(entry), entry.filename)
            if entry.compress_type == ZIP_STORED:
                raw.seek(entry.header_offset + 26)
                name_size, extra_size = struct.unpack('<HH', raw.read(4))
                if (entry.header_offset + 30 + name_size + extra_size) % PAGE:
                    raise ValueError(f'{entry.filename}: APK entry is not 16 KB aligned')
            checked.append(entry.filename)
    if not checked:
        raise ValueError(f'{path}: no JNI libraries found')
    print(f'{path}: {len(checked)} JNI libraries pass 16 KB LOAD, RELRO and ZIP alignment')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', nargs='+', type=Path)
    args = parser.parse_args()
    for path in args.apk:
        check_apk(path)
