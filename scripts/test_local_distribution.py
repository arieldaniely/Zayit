"""Local split-stream packaging and verification regressions."""

import importlib.util
import json
import tarfile
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('local_distribution', Path(__file__).with_name('package-local-distribution.py'))
distribution = importlib.util.module_from_spec(spec)
spec.loader.exec_module(distribution)


class LocalDistributionTest(unittest.TestCase):
    def test_split_writer_preserves_bytes_and_hashes_across_boundaries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            writer = distribution.SplitWriter(root, 31)
            expected = bytes(range(256))
            for offset in range(0, len(expected), 47):
                self.assertEqual(len(expected[offset:offset + 47]), writer.write(expected[offset:offset + 47]))
            writer.finish_part()
            self.assertEqual(expected, b''.join((root / p['name']).read_bytes() for p in writer.parts))
            self.assertTrue(all(p['bytes'] <= 31 for p in writer.parts))
            self.assertTrue(all(distribution.sha256(root / p['name']) == p['sha256'] for p in writer.parts))
            with distribution.PartsReader([root / p['name'] for p in writer.parts]) as reader:
                self.assertEqual(expected, reader.read())

    def test_complete_level22_archive_verifies_and_extracts_semantic_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database, semantic, output = [root / p for p in ('database', 'semantic', 'output')]
            for name in ['seforim.db', 'catalog.pb', 'lexical.db', 'release_info.txt',
                         'seforim.db.lucene/segments_1', 'seforim.db.lookup.lucene/segments_1', 'תלמוד בבלי/ברכות.pdf']:
                path = database / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode('utf-8'))
            for name in ['model/seforim-embed-round2-int8.onnx', 'model/tokenizer.json', 'index/shard-00/segments_1']:
                path = semantic / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(name.encode('utf-8'))
            properties = dict(shardCount='1', vectorEncoding='int8-maxabs-v1',
                              databaseSha256=distribution.sha256(database / 'seforim.db'),
                              modelSha256=distribution.sha256(semantic / 'model/seforim-embed-round2-int8.onnx'),
                              tokenizerSha256=distribution.sha256(semantic / 'model/tokenizer.json'))
            (semantic / 'index/shard-00/semantic.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
            distribution.package(database, semantic, output, threads=0)
            extracted = root / 'extracted'
            distribution.verify(output, extracted)
            self.assertEqual((semantic / 'index/shard-00/segments_1').read_bytes(),
                             (extracted / 'index/shard-00/segments_1').read_bytes())
            self.assertTrue(json.loads((output / 'verification.json').read_text())['verified'])
            manifest = json.loads((output / 'distributions.json').read_text())
            self.assertEqual(22, manifest['compressionLevel'])
            self.assertEqual(11, len(manifest['contents']))
            staged, assembled = root / 'staged', root / 'assembled'
            distribution.package(database, None, staged, threads=0, base_only=True)
            distribution.package(database, semantic, assembled, threads=0, base_archive=staged)
            distribution.verify(assembled, root / 'assembled-semantic')
            assembled_manifest = json.loads((assembled / 'distributions.json').read_text())
            self.assertEqual(manifest['contents'], assembled_manifest['contents'])
            installed = root / 'installed'
            with distribution.PartsReader([assembled / p['name'] for p in assembled_manifest['assets']]) as parts:
                with distribution.zstandard.ZstdDecompressor().stream_reader(parts, read_across_frames=True) as decoded:
                    with tarfile.open(fileobj=decoded, mode='r|', ignore_zeros=True) as archive:
                        archive.extractall(installed, filter='data')
            distribution.verify_installed(assembled, installed)
            self.assertTrue(json.loads((assembled / 'installed-verification.json').read_text())['verified'])
            pdf = installed / 'תלמוד בבלי/ברכות.pdf'
            corrupted_pdf = bytearray(pdf.read_bytes())
            corrupted_pdf[0] ^= 1
            pdf.write_bytes(corrupted_pdf)
            with self.assertRaises(ValueError):
                distribution.verify_installed(assembled, installed)
            part = output / manifest['assets'][0]['name']
            damaged = bytearray(part.read_bytes())
            damaged[len(damaged) // 2] ^= 1
            part.write_bytes(damaged)
            with self.assertRaises(ValueError):
                distribution.verify(output)

    def test_rejects_part_sizes_at_or_above_two_gigabytes(self):
        for size in (0, 2_000_000_000, 2_147_483_648):
            with self.assertRaises(ValueError):
                distribution.SplitWriter(Path('.'), size)


if __name__ == '__main__':
    unittest.main()
