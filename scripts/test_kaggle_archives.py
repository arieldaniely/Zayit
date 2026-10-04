"""Regression tests with real TAR archives and concatenated zstd frames."""

import importlib.util
import io
import tarfile
import tempfile
import unittest
from pathlib import Path

import zstandard

from kaggle_publish_distributions import concatenate_archives, extract_complete


spec = importlib.util.spec_from_file_location('extract_library_archive',
                                            Path(__file__).with_name('extract-library-archive.py'))
extractor = importlib.util.module_from_spec(spec)
spec.loader.exec_module(extractor)


def write_archive(path, entries):
    content = io.BytesIO()
    with tarfile.open(fileobj=content, mode='w') as archive:
        for name, data in entries.items():
            entry = tarfile.TarInfo(name)
            entry.size = len(data)
            archive.addfile(entry, io.BytesIO(data))
    path.write_bytes(zstandard.ZstdCompressor().compress(content.getvalue()))


class KaggleArchiveTest(unittest.TestCase):
    def test_real_concatenated_frames_extract_all_components_from_single_file_and_parts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, pdf, semantic = [root / name for name in ('base.zst', 'pdf.zst', 'semantic.zst')]
            write_archive(base, {'seforim.db': b'database', 'lexical.db': b'dictionary'})
            write_archive(pdf, {'תלמוד בבלי/ברכות.pdf': b'pdf'})
            write_archive(semantic, {'model/tokenizer.json': b'tokenizer', 'index/shard-00/segments': b'vectors'})
            sources = [base, pdf, semantic]
            expected = b''.join(source.read_bytes() for source in sources)
            single = concatenate_archives(sources, root / 'single.tar.zst')
            parts = concatenate_archives(sources, root / 'split.tar.zst', split_bytes=31)
            self.assertEqual(expected, b''.join(part.read_bytes() for part in parts))
            for number, archives in enumerate((single, parts)):
                output = root / f'extracted-{number}'
                extractor.extract(archives[0], output)
                self.assertEqual(b'database', (output / 'seforim.db').read_bytes())
                self.assertEqual(b'pdf', (output / 'תלמוד בבלי/ברכות.pdf').read_bytes())
                self.assertEqual(b'tokenizer', (output / 'seforim.db.semantic/model/tokenizer.json').read_bytes())
                self.assertEqual(b'vectors', (output / 'seforim.db.semantic/index/shard-00/segments').read_bytes())

    def test_standalone_semantic_archive_keeps_original_layout(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            archive = root / 'semantic-bundle.tar.zst'
            write_archive(archive, {'model/tokenizer.json': b'tokenizer'})
            output = root / 'seforim.db.semantic'
            extractor.extract(archive, output)
            self.assertEqual(b'tokenizer', (output / 'model/tokenizer.json').read_bytes())

    def test_base_bundle_with_legacy_name_is_accepted_but_full_bundle_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base = root / 'seforim_bundle.tar.zst'
            pdf = root / 'pdf.tar.zst'
            write_archive(base, {'seforim.db': b'database'})
            write_archive(pdf, {'תלמוד בבלי/ברכות.pdf': b'pdf'})
            destination = root / 'base'
            destination.mkdir()
            extract_complete([base], destination, extractor.PartsReader, base_only=True)
            self.assertTrue((destination / '.base-extracted').is_file())
            # Model downloads use work/model after base extraction; they must not block resume.
            (destination / 'model').mkdir()
            extract_complete([base], destination, extractor.PartsReader, base_only=True)
            full = concatenate_archives([base, pdf], root / 'full.tar.zst')
            destination = root / 'full'
            destination.mkdir()
            with self.assertRaises(ValueError):
                extract_complete(full, destination, extractor.PartsReader, base_only=True)
            self.assertFalse((destination / '.base-extracted').exists())

    def test_unsafe_entry_in_appended_frame_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, unsafe = root / 'base.zst', root / 'unsafe.zst'
            write_archive(base, {'seforim.db': b'database'})
            write_archive(unsafe, {'../escaped': b'unsafe'})
            archive = concatenate_archives([base, unsafe], root / 'bundle.tar.zst')[0]
            with self.assertRaises(ValueError):
                extractor.extract(archive, root / 'extracted')
            self.assertFalse((root / 'escaped').exists())


if __name__ == '__main__':
    unittest.main()
