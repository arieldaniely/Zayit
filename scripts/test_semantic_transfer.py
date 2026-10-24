import json
import tempfile
import unittest
from pathlib import Path

from semantic_transfer import pack, unpack
from vector_checkpoint import VectorCheckpoint


class SemanticTransferTest(unittest.TestCase):
    def test_index_only_parts_round_trip_and_corruption(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            index = root / "index"
            shard = index / "shard-00"
            shard.mkdir(parents=True)
            (root / "seforim.db").write_bytes(b"must not be exported")
            (root / "model.onnx").write_bytes(b"must not be exported")
            (shard / "segments_1").write_bytes(bytes(range(256)) * 4)
            (shard / "semantic.properties").write_text(
                "format=zayit-round2-1\ndimension=256\nvectorEncoding=int8-maxabs-v1\n"
                "shardIndex=0\nshardCount=1\nindexed=2\neligible=2\n"
                "databaseSha256=db\nmodelSha256=model\ntokenizerSha256=tokenizer\n", encoding="utf-8")
            output = root / "output"
            args = dict(database_sha="db", model_sha="model", tokenizer_sha="tokenizer", database_tag="draft", part_bytes=97)
            manifest = pack(index, output, **args)
            self.assertGreater(len(manifest["parts"]), 1)
            self.assertTrue(all(part["size"] <= 97 for part in manifest["parts"]))
            restored = root / "restored"
            unpack(output, restored)
            self.assertEqual((shard / "segments_1").read_bytes(), (restored / "index/shard-00/segments_1").read_bytes())
            self.assertFalse((restored / "seforim.db").exists())
            self.assertFalse((restored / "model.onnx").exists())
            self.assertEqual(manifest, pack(index, output, **args))
            (output / manifest["parts"][0]["file"]).write_bytes(b"damaged")
            with self.assertRaisesRegex(ValueError, "Damaged"):
                unpack(output, root / "bad")
            # A failed upload/archive can be rebuilt without touching unrelated output.
            (output / "notes.txt").write_text("keep")
            pack(index, output, **args)
            self.assertEqual("keep", (output / "notes.txt").read_text())
            unpack(output, root / "repaired")

    def test_incompatible_or_incomplete_index_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(ValueError):
                pack(root, root / "output", database_sha="db", model_sha="m", tokenizer_sha="t", database_tag="v1")


class VectorCheckpointTest(unittest.TestCase):
    def test_resume_rolls_back_uncommitted_tail(self):
        with tempfile.TemporaryDirectory() as directory:
            args = (directory, 0, [0], {"database": "db", "model": "model"}, b"HEADER", 4)
            first = VectorCheckpoint(*args)
            first.outputs[0].write(b"abcd")
            first.counts[0] = 1
            first.commit(12, 5)
            first.outputs[0].write(b"tail")
            first.close()
            resumed = VectorCheckpoint(*args)
            self.assertEqual((12, 5, {0: 1}), (resumed.last_line_id, resumed.scanned, resumed.counts))
            resumed.outputs[0].write(b"next")
            resumed.counts[0] += 1
            resumed.commit(20, 8)
            resumed.close()
            self.assertEqual(b"HEADERabcdnext", (Path(directory) / "shard-00.bin").read_bytes())
            with self.assertRaisesRegex(ValueError, "another"):
                VectorCheckpoint(directory, 0, [0], {"database": "different"}, b"HEADER", 4)

    def test_truncated_committed_vectors_are_not_silently_reused(self):
        with tempfile.TemporaryDirectory() as directory:
            checkpoint = VectorCheckpoint(directory, 0, [0], {}, b"HEADER", 4)
            checkpoint.outputs[0].write(b"abcd")
            checkpoint.counts[0] = 1
            checkpoint.commit(1, 1)
            checkpoint.close()
            (Path(directory) / "shard-00.bin").write_bytes(b"HEADER")
            with self.assertRaisesRegex(ValueError, "Truncated"):
                VectorCheckpoint(directory, 0, [0], {}, b"HEADER", 4)


if __name__ == "__main__":
    unittest.main()
