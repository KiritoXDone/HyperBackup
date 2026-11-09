import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location('alignment', Path(__file__).resolve().parents[1] / 'check_native_alignment.py')
alignment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(alignment)


def elf(load_end, relro_end, load_alignment=0x4000):
    data = bytearray(64 + 56 * 2)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 2)
    struct.pack_into('<IIQQQQQQ', data, 64, alignment.LOAD, 6, 0, 0x4000, 0, 0, load_end - 0x4000, load_alignment)
    struct.pack_into('<IIQQQQQQ', data, 120, alignment.RELRO, 4, 0, 0x4000, 0, 0, relro_end - 0x4000, 1)
    return data


class NativeAlignmentTest(unittest.TestCase):
    def test_load_alignment_alone_does_not_prove_relro_compatibility(self):
        with self.assertRaisesRegex(ValueError, 'RELRO'):
            alignment.check_elf(elf(0x9000, 0x5000), 'old-library.so')

    def test_relro_page_boundary_keeps_following_data_writable(self):
        alignment.check_elf(elf(0x9000, 0x8000), 'aligned.so')

    def test_dedicated_relro_load_can_end_between_pages(self):
        alignment.check_elf(elf(0x6000, 0x6000), 'separate-load.so')

    def test_four_k_load_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'LOAD'):
            alignment.check_elf(elf(0x9000, 0x8000, 0x1000), 'four-k.so')

    def test_truncated_header_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'program-header'):
            alignment.check_elf(elf(0x9000, 0x8000)[:-1], 'truncated.so')
