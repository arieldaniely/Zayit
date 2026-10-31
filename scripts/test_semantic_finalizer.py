import contextlib
import json
import tempfile
import tarfile
import unittest
from types import SimpleNamespace
from pathlib import Path
from unittest.mock import patch

import zstandard

from finalize_semantic_index import BASE_FILES, COMPRESSION_JOB_BYTES, finish, package, require_complete_base, transfer_location, verify_package
from kaggle_semantic_stream import PartsReader
from semantic_transfer import sha256


class SemanticFinalizerTest(unittest.TestCase):
    def test_full_bundle_only_uploads_one_verified_level22_distribution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, semantic = root / "base", root / "semantic"
            base.mkdir()
            for name in BASE_FILES:
                if name.endswith("lucene"):
                    (base / name).mkdir()
                    (base / name / "segments_1").write_bytes(b"index")
                else:
                    (base / name).write_bytes(b"database")
            (base / "תלמוד בבלי").mkdir()
            (base / "תלמוד בבלי/test.pdf").write_bytes(b"%PDF-test")
            (semantic / "index").mkdir(parents=True)
            (semantic / "index/segments_1").write_bytes(b"semantic")
            (semantic / "model").mkdir()
            (semantic / "model/seforim-embed-round2-int8.onnx").write_bytes(b"model")
            (semantic / "model/tokenizer.json").write_bytes(b"tokenizer")
            (root / "transfer").mkdir()
            (root / "transfer/semantic-transfer.json").write_text(json.dumps({"databaseSha256": sha256(base / "seforim.db")}))
            args = SimpleNamespace(work=root, repo=root, db_repository="owner/library",
                                   db_release_tag="v4", transfer_release_tag="v4", full_bundle_only=True)
            summary = root / "summary.md"
            # No GH_TOKEN or app_repository: this path must need only library publication access.
            with (patch.dict("os.environ", {"DB_TOKEN": "test", "GITHUB_RUN_ID": "123",
                                           "GITHUB_STEP_SUMMARY": str(summary)}, clear=True),
                  patch("finalize_semantic_index.validate_index"),
                  patch("finalize_semantic_index.subprocess.run") as optimize,
                  patch("finalize_semantic_index.MODEL_SHA", sha256(semantic / "model/seforim-embed-round2-int8.onnx")),
                  patch("finalize_semantic_index.TOKENIZER_SHA", sha256(semantic / "model/tokenizer.json")),
                  patch("finalize_semantic_index.Publisher") as publisher):
                publisher.return_value.draft.return_value = {"html_url": "https://example.test/draft"}
                finish(args)
            publisher.assert_called_once_with("test")
            optimize.assert_called_once()
            manifest = json.loads((root / "output/bundle-manifest.json").read_text())
            self.assertEqual(22, manifest["compressionLevel"])
            self.assertIn("seforim.db.semantic/index/segments_1", manifest["contents"])
            self.assertIn("תלמוד בבלי/test.pdf", manifest["contents"])
            self.assertEqual(len(manifest["parts"]) + 1, publisher.return_value.upload.call_count)
            self.assertNotIn("Optional semantic supplement", summary.read_text())

    def test_transfer_defaults_to_database_repository(self):
        args = SimpleNamespace(transfer_repository=None, db_repository="owner/library")
        with patch.dict("os.environ", {"DB_TOKEN": "database-test", "GH_TOKEN": "app-test"}):
            self.assertEqual(("owner/library", "database-test"), transfer_location(args))
            args.transfer_repository = "owner/app"
            self.assertEqual(("owner/app", "app-test"), transfer_location(args))

    def test_complete_bundle_requires_pdf_and_both_completed_lexical_indexes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in BASE_FILES:
                if name.endswith("lucene"):
                    (root / name).mkdir()
                    (root / name / "segments_1").write_bytes(b"index")
                else:
                    (root / name).write_bytes(b"fixture")
            with self.assertRaisesRegex(ValueError, "PDF"):
                require_complete_base(root)
            pdf = root / "תלמוד בבלי" / "ברכות.pdf"
            pdf.parent.mkdir()
            pdf.write_bytes(b"%PDF-fixture")
            require_complete_base(root)
            (root / "seforim.db.lookup.lucene/segments_1").unlink()
            with self.assertRaisesRegex(ValueError, "completed Lucene"):
                require_complete_base(root)

    def test_verification_detects_modified_part_and_missing_content(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.write_bytes(b"content")
            manifest = package([(source, "seforim.db")], root / "bundle.tar.zst")
            manifest["contents"]["missing"] = {"size": 1, "sha256": "0" * 64}
            with self.assertRaisesRegex(ValueError, "missing"):
                verify_package(root, manifest)
            path = root / manifest["parts"][0]["file"]
            path.write_bytes(b"damaged")
            with self.assertRaisesRegex(ValueError, "Damaged archive part"):
                verify_package(root, manifest)

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
            parameter_type = zstandard.ZstdCompressionParameters
            with (patch("finalize_semantic_index.zstandard.ZstdCompressor", wraps=compressor) as compression,
                  patch("finalize_semantic_index.zstandard.ZstdCompressionParameters", wraps=parameter_type) as parameters):
                manifest = package([(database, "seforim.db"), (root / "index", "seforim.db.semantic/index")],
                                   output, part_bytes=97)
                parameters.from_level.assert_called_once_with(22, threads=2, job_size=COMPRESSION_JOB_BYTES, write_checksum=1)
                self.assertIsInstance(compression.call_args.kwargs["compression_params"], parameter_type)
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
