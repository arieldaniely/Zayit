import contextlib
import json
import tempfile
import tarfile
import unittest
from pathlib import Path
from unittest.mock import patch

import zstandard

from finalize_semantic_index import package, prepare
from kaggle_semantic_stream import PartsReader
from semantic_transfer import sha256


class SemanticFinalizerTest(unittest.TestCase):
    def test_recompresses_all_components_with_level_22_and_verified_parts(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "seforim.db"
            database.write_bytes(b"new database with metadata")
            index = root / "index/shard-00"
            index.mkdir(parents=True)
            (index / "segments_1").write_bytes(bytes(range(256)) * 10)
            (index / "write.lock").touch()
            output = root / "output/bundle.tar.zst"
            compressor = zstandard.ZstdCompressor
            with patch("finalize_semantic_index.zstandard.ZstdCompressor", wraps=compressor) as compression:
                manifest = package([(database, "seforim.db"), (root / "index", "seforim.db.semantic/index")],
                                   output, part_bytes=97)
                compression.assert_called_once_with(level=22, threads=2, write_checksum=True)
            paths = [output.parent / part["file"] for part in manifest["parts"]]
            self.assertGreater(len(paths), 1)
            for path, part in zip(paths, manifest["parts"]):
                self.assertLessEqual(path.stat().st_size, 97)
                self.assertEqual(sha256(path), part["sha256"])
            with contextlib.closing(PartsReader(paths)) as source:
                with zstandard.ZstdDecompressor().stream_reader(source) as decompressed:
                    with tarfile.open(fileobj=decompressed, mode="r|") as archive:
                        contents = {member.name: archive.extractfile(member).read() for member in archive}
            self.assertEqual({"seforim.db", "seforim.db.semantic/index/shard-00/segments_1"}, set(contents))
            self.assertEqual(database.read_bytes(), contents["seforim.db"])

    def test_duplicate_or_unsafe_paths_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            file = root / "source"
            file.write_text("content")
            with self.assertRaisesRegex(ValueError, "unsafe"):
                package([(file, "../escape")], root / "bad.tar.zst")
            with self.assertRaisesRegex(ValueError, "Duplicate"):
                package([(file, "same"), (file, "same")], root / "duplicate.tar.zst")

    def test_parts_reader_handles_repeated_eof_and_unbounded_read(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = [Path(directory) / name for name in ("one", "two")]
            for path in paths:
                path.write_bytes(b"test")
            with contextlib.closing(PartsReader(paths)) as source:
                self.assertEqual(b"", source.read(0))
                self.assertEqual(b"testtest", source.read())
                self.assertEqual(b"", source.read())
                self.assertEqual(b"", source.read(1))


if __name__ == "__main__":
    unittest.main()
