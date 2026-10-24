"""Durable checkpoints for GPU vectors; a retry discards only the uncommitted tail."""
import json
import os
from pathlib import Path


class VectorCheckpoint:
    def __init__(self, directory, worker, shards, identity, header, record_bytes):
        self.directory = Path(directory)
        self.directory.mkdir(parents=True, exist_ok=True)
        self.path = self.directory / f"worker-{worker}.checkpoint.json"
        self.header, self.record_bytes, self.identity = header, record_bytes, identity
        self.counts = {shard: 0 for shard in shards}
        self.scanned, self.last_line_id = 0, -1
        saved = json.loads(self.path.read_text(encoding="utf-8")) if self.path.exists() else None
        if saved:
            if saved["identity"] != identity or set(map(int, saved["counts"])) != set(self.counts):
                raise ValueError("Checkpoint belongs to another database/model/partition")
            self.counts = {int(key): value for key, value in saved["counts"].items()}
            self.scanned, self.last_line_id = saved["scanned"], saved["lastLineId"]
        self.outputs = {}
        try:
            for shard, count in self.counts.items():
                path = self.directory / f"shard-{shard:02d}.bin"
                if saved:
                    expected = len(header) + count * record_bytes
                    if not path.is_file() or path.stat().st_size < expected:
                        raise ValueError("Truncated committed vector checkpoint")
                    stream = path.open("r+b", buffering=1 << 20)
                    self.outputs[shard] = stream
                    if stream.read(len(header)) != header:
                        raise ValueError("Invalid vector checkpoint header")
                    stream.truncate(expected)
                    stream.seek(expected)
                else:
                    # These exact files belong to this worker, and no committed checkpoint exists yet.
                    stream = path.open("wb", buffering=1 << 20)
                    self.outputs[shard] = stream
                    stream.write(header)
            self.commit(self.last_line_id, self.scanned)
        except BaseException:
            self.close()
            raise

    def commit(self, last_line_id, scanned):
        for output in self.outputs.values():
            output.flush()
            os.fsync(output.fileno())
        temporary = self.path.with_suffix(".writing")
        temporary.write_text(json.dumps({"identity": self.identity, "counts": self.counts,
                                         "scanned": scanned, "lastLineId": last_line_id}), encoding="utf-8")
        temporary.replace(self.path)
        self.last_line_id, self.scanned = last_line_id, scanned

    def close(self):
        for output in self.outputs.values():
            output.close()
