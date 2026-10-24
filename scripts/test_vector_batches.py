import sys
import unittest

from vector_batches import encode_with_retry


class OutOfMemory(Exception):
    pass


class VectorBatchesTest(unittest.TestCase):
    def test_splits_preserve_order_and_do_not_retain_failed_gpu_frames(self):
        output, releases, splits = [], [], []

        def encode(batch):
            self.assertIsNone(sys.exception(), "Retry still retains the failed GPU traceback")
            if len(batch) > 2:
                raise OutOfMemory()
            output.extend(batch)

        encode_with_retry(list(range(7)), encode, OutOfMemory, lambda: releases.append(True),
                          lambda left, right: splits.append((left, right)))
        self.assertEqual(list(range(7)), output)
        self.assertEqual([(3, 4), (1, 2), (2, 2)], splits)
        self.assertEqual(3, len(releases))

    def test_one_record_oom_and_non_memory_failures_are_propagated(self):
        def oom(batch):
            raise OutOfMemory()

        with self.assertRaises(OutOfMemory):
            encode_with_retry([1], oom, OutOfMemory, lambda: self.fail("No smaller batch exists"), lambda *_: None)

        def io_error(batch):
            raise OSError("disk full")

        with self.assertRaises(OSError):
            encode_with_retry([1, 2], io_error, OutOfMemory, lambda: self.fail("Not a GPU failure"), lambda *_: None)


if __name__ == "__main__":
    unittest.main()
