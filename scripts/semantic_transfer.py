"""Stream the semantic index into verified 1.9 GiB parts; no database or models."""
from __future__ import annotations

import hashlib
import json
import re
import shutil
import tarfile
from pathlib import Path

import zstandard

PART_BYTES = int(1.9 * 1024**3)
ARCHIVE = "semantic-index.tar.zst"


def sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


class SplitWriter:
    def __init__(self, target, limit=PART_BYTES):
        if limit <= 0:
            raise ValueError("Part size must be positive")
        self.target, self.limit = Path(target), limit
        self.parts, self.output = [], None
        self.digest, self.total = hashlib.sha256(), 0

    def writable(self):
        return True

    def write(self, data):
        remaining = memoryview(data)
        original = len(remaining)
        while remaining:
            if self.output is None:
                self.path = self.target.with_name(self.target.name + f".part{len(self.parts) + 1:02d}")
                self.output = self.path.open("xb")
                self.part_digest, self.size = hashlib.sha256(), 0
            count = min(len(remaining), self.limit - self.size)
            chunk, remaining = remaining[:count], remaining[count:]
            self.output.write(chunk)
            self.part_digest.update(chunk)
            self.digest.update(chunk)
            self.size += count
            self.total += count
            if self.size == self.limit:
                self._finish_part()
        return original

    def flush(self):
        if self.output:
            self.output.flush()

    def _finish_part(self):
        if self.output:
            self.output.close()
            self.parts.append({"file": self.path.name, "size": self.size, "sha256": self.part_digest.hexdigest()})
            self.output = None

    def close(self):
        self._finish_part()


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines()
                if "=" in line and not line.startswith("#"))


def validate_index(index, database_sha, model_sha, tokenizer_sha):
    shards = sorted(Path(index).glob("shard-*"))
    if not shards or [path.name for path in shards] != [f"shard-{i:02d}" for i in range(len(shards))]:
        raise ValueError("Missing semantic shards")
    for number, shard in enumerate(shards):
        manifest = properties(shard / "semantic.properties")
        expected = {"format": "zayit-round2-1", "dimension": "256", "vectorEncoding": "int8-maxabs-v1",
                    "databaseSha256": database_sha, "modelSha256": model_sha, "tokenizerSha256": tokenizer_sha,
                    "shardIndex": str(number), "shardCount": str(len(shards))}
        if any(manifest.get(key) != value for key, value in expected.items()):
            raise ValueError(f"Incompatible semantic shard: {shard.name}")
        if int(manifest.get("indexed", "0")) <= 0 or manifest.get("indexed") != manifest.get("eligible"):
            raise ValueError(f"Incomplete semantic shard: {shard.name}")
    return shards


def pack(index, output, *, database_sha, model_sha, tokenizer_sha, database_tag, level=3, part_bytes=PART_BYTES):
    if level not in range(1, 7):
        raise ValueError("Kaggle transfer compression must be between 1 and 6")
    validate_index(index, database_sha, model_sha, tokenizer_sha)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    previous = output / "semantic-transfer.json"
    if previous.exists():
        saved = json.loads(previous.read_text(encoding="utf-8"))
        identity = {"databaseSha256": database_sha, "modelSha256": model_sha, "tokenizerSha256": tokenizer_sha,
                    "databaseReleaseTag": database_tag}
        if any(saved.get(key) != value for key, value in identity.items()):
            raise ValueError("Output belongs to another semantic build; use a new output directory")
        if saved.get("parts") and all(
            re.fullmatch(re.escape(ARCHIVE) + r"\.part[0-9]+", part["file"])
            and (output / part["file"]).is_file()
            and (output / part["file"]).stat().st_size == part["size"]
            and sha256(output / part["file"]) == part["sha256"] for part in saved["parts"]
        ):
            return saved
    # Retry only our generated parts; unrelated saved output is never removed.
    for path in output.glob(ARCHIVE + ".part*"):
        if re.fullmatch(re.escape(ARCHIVE) + r"\.part[0-9]+", path.name):
            path.unlink()
    previous.unlink(missing_ok=True)
    writer = SplitWriter(output / ARCHIVE, part_bytes)
    try:
        with zstandard.ZstdCompressor(level=level, threads=2, write_checksum=True).stream_writer(writer, closefd=False) as compressed:
            with tarfile.open(fileobj=compressed, mode="w|") as archive:
                for path in sorted(Path(index).rglob("*")):
                    if path.is_symlink():
                        raise ValueError("Symbolic links are not allowed in a semantic transfer")
                    if path.is_file() and path.name != "write.lock":
                        archive.add(path, arcname="index/" + path.relative_to(index).as_posix(), recursive=False)
    finally:
        writer.close()
    manifest = {"formatVersion": 1, "kind": "zayit-semantic-index-transfer", "file": ARCHIVE,
                "size": writer.total, "sha256": writer.digest.hexdigest(), "parts": writer.parts,
                "databaseSha256": database_sha, "modelSha256": model_sha, "tokenizerSha256": tokenizer_sha,
                "databaseReleaseTag": database_tag, "compressionLevel": level}
    (output / "semantic-transfer.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return manifest


def unpack(source, destination):
    from contextlib import closing
    from kaggle_semantic_stream import PartsReader

    source, destination = Path(source), Path(destination).resolve()
    manifest = json.loads((source / "semantic-transfer.json").read_text(encoding="utf-8"))
    if manifest.get("formatVersion") != 1 or manifest.get("kind") != "zayit-semantic-index-transfer":
        raise ValueError("Unsupported semantic transfer")
    parts = manifest["parts"]
    if not parts or [part["file"] for part in parts] != [ARCHIVE + f".part{i + 1:02d}" for i in range(len(parts))]:
        raise ValueError("Missing or unordered semantic transfer parts")
    digest, size = hashlib.sha256(), 0
    for part in parts:
        path = source / part["file"]
        if path.stat().st_size != part["size"] or sha256(path) != part["sha256"]:
            raise ValueError(f"Damaged semantic part: {path.name}")
        with path.open("rb") as content:
            for block in iter(lambda: content.read(1 << 20), b""):
                digest.update(block)
                size += len(block)
    if digest.hexdigest() != manifest["sha256"] or size != manifest["size"]:
        raise ValueError("Damaged combined semantic archive")
    destination.mkdir(parents=True, exist_ok=False)
    seen = set()
    with closing(PartsReader([source / part["file"] for part in parts])) as stream:
        with zstandard.ZstdDecompressor().stream_reader(stream) as decompressed:
            with tarfile.open(fileobj=decompressed, mode="r|") as archive:
                for member in archive:
                    name = member.name
                    if not re.fullmatch(r"index/shard-[0-9]+/[^/]+", name) or not member.isfile() or name in seen:
                        raise ValueError(f"Unexpected semantic archive entry: {name}")
                    seen.add(name)
                    target = (destination / name).resolve()
                    if not target.is_relative_to(destination):
                        raise ValueError("Archive entry escapes destination")
                    target.parent.mkdir(parents=True, exist_ok=True)
                    with archive.extractfile(member) as content, target.open("xb") as output:
                        shutil.copyfileobj(content, output, 1 << 20)
    validate_index(destination / "index", manifest["databaseSha256"], manifest["modelSha256"], manifest["tokenizerSha256"])
    return manifest
