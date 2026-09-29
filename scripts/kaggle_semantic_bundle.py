"""End-to-end Kaggle T4 x2 build from the published SeforimLibrary DB release."""

from __future__ import annotations

import argparse
import contextlib
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tarfile
from pathlib import Path

import requests
import torch
import zstandard
from huggingface_hub import hf_hub_download, snapshot_download


RELEASE_TAG = "v2-20260814115718"
RELEASE_BASE = f"https://github.com/arieldaniely/SeforimLibrary/releases/download/{RELEASE_TAG}"
DB_PARTS = {
    "seforim_bundle.tar.zst.part01": "6bf7fdcbef5ce531f98e29fb7c5b2bc4bc6c7aeaa0ef2e4001efc90e2a5e6749",
    "seforim_bundle.tar.zst.part02": "73085109dee96e2134cb9662807b5afcda0d693543423f931aaf109248a75a13",
}
SOURCE_REPO = "ArieLLL123/judaic-semantic-teacher-8x512-retrieval-v5-round2"
SOURCE_REVISION = "8d7016cf472fb436dbd3ac843ef4d407c28d08f4"
ONNX_REPO = "ArieLLL123/judaic-semantic-round2-onnx-zayit"
MODEL_SHA = "659226865abd3a1bc833565ae6b2e2f48abdd7136285824a12966d4d3294cbf8"
TOKENIZER_SHA = "0664287976ecb078bdfd8f5e5515dc87d8cb7f985a79a481aa1cdf7a7321c0e9"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url: str, target: Path, expected_sha: str) -> None:
    if target.is_file() and sha256(target) == expected_sha:
        print(f"Verified existing {target.name}", flush=True)
        return
    partial = target.with_name(target.name + ".download")
    offset = partial.stat().st_size if partial.exists() else 0
    headers = {"Range": f"bytes={offset}-"} if offset else {}
    with requests.get(url, headers=headers, stream=True, timeout=(30, 120)) as response:
        response.raise_for_status()
        if offset and response.status_code != 206:
            offset = 0
        with partial.open("ab" if offset else "wb") as output:
            for chunk in response.iter_content(chunk_size=1 << 20):
                if chunk:
                    output.write(chunk)
    actual = sha256(partial)
    if actual != expected_sha:
        raise ValueError(f"SHA-256 mismatch for {target.name}: {actual}")
    partial.replace(target)
    print(f"Downloaded and verified {target.name}", flush=True)


class MultipartStream:
    """Read split .zst files as one compressed byte stream."""

    def __init__(self, parts: list[Path]) -> None:
        self.parts = iter(parts)
        self.current = next(self.parts).open("rb")

    def read(self, size: int = -1) -> bytes:
        if size < 0:
            return b"".join(iter(lambda: self.read(1 << 20), b""))
        while True:
            data = self.current.read(size)
            if data:
                return data
            self.current.close()
            next_part = next(self.parts, None)
            if next_part is None:
                return b""
            self.current = next_part.open("rb")

    def close(self) -> None:
        self.current.close()


def extract_database(parts: list[Path], destination: Path) -> None:
    if destination.is_file():
        print(f"Using existing {destination}", flush=True)
        return
    stream = MultipartStream(parts)
    temporary = destination.with_suffix(".building")
    try:
        with contextlib.closing(stream), zstandard.ZstdDecompressor().stream_reader(stream) as reader:
            with tarfile.open(fileobj=reader, mode="r|") as archive:
                for member in archive:
                    if member.isfile() and Path(member.name).name == "seforim.db":
                        with archive.extractfile(member) as source, temporary.open("wb") as output:
                            shutil.copyfileobj(source, output, 1 << 20)
                        temporary.replace(destination)
                        print(f"Extracted {destination} ({destination.stat().st_size:,} bytes)", flush=True)
                        return
        raise FileNotFoundError("seforim.db was not found in the release archive")
    finally:
        temporary.unlink(missing_ok=True)


def get_hf_token() -> str:
    token = os.getenv("HF_TOKEN")
    if not token:
        from kaggle_secrets import UserSecretsClient

        token = UserSecretsClient().get_secret("HF_TOKEN")
    if not token:
        raise RuntimeError("Set an approved HF_TOKEN in Kaggle Secrets")
    return token


def fetch_models(work: Path) -> tuple[Path, Path]:
    token = get_hf_token()
    source = Path(snapshot_download(
        repo_id=SOURCE_REPO, revision=SOURCE_REVISION, token=token,
        allow_patterns=["round2/final/*", "round2/final/**/*"],
        local_dir=work / "checkpoint-download",
    )) / "round2" / "final"
    for name in ("sentence_encoder_config.json", "projection.safetensors", "backbone/model.safetensors",
                 "backbone/config.json", "tokenizer/tokenizer.json"):
        if not (source / name).is_file():
            raise FileNotFoundError(source / name)
    model_dir = work / "model"
    model_dir.mkdir(exist_ok=True)
    for name, expected in (("seforim-embed-round2-int8.onnx", MODEL_SHA),
                           ("tokenizer.json", TOKENIZER_SHA)):
        path = Path(hf_hub_download(repo_id=ONNX_REPO, filename=name, token=token))
        if sha256(path) != expected:
            raise ValueError(f"Unexpected {name} from Hugging Face")
        shutil.copy2(path, model_dir / name)
    return source, model_dir


def install_java25(work: Path) -> Path:
    java_home = os.getenv("JAVA_HOME")
    if java_home:
        result = subprocess.run([str(Path(java_home) / "bin/java"), "-version"], capture_output=True, text=True)
        if 'version "25' in result.stderr or 'version "25' in result.stdout:
            return Path(java_home)
    archive = work / "jdk25.tar.gz"
    if not archive.exists():
        url = "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse"
        with requests.get(url, stream=True, timeout=(30, 120)) as response:
            response.raise_for_status()
            with archive.open("wb") as output:
                for chunk in response.iter_content(1 << 20):
                    if chunk:
                        output.write(chunk)
    directory = work / "jdk25"
    directory.mkdir(exist_ok=True)
    subprocess.run(["tar", "-xzf", str(archive), "-C", str(directory)], check=True)
    homes = list(directory.glob("jdk-25*"))
    if len(homes) != 1:
        raise RuntimeError("Could not locate extracted JDK 25")
    return homes[0]


def run_command(command: list[str], *, cwd: Path | None = None, env: dict[str, str] | None = None) -> None:
    print("Running:", " ".join(command[:4]), "...", flush=True)
    subprocess.run(command, cwd=cwd, env=env, check=True)


def main(args: argparse.Namespace) -> None:
    if torch.cuda.device_count() != 2 or not all("T4" in torch.cuda.get_device_name(i) for i in range(2)):
        raise RuntimeError("Select Kaggle GPU T4 x2 in Notebook Settings")
    args.work.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    download_dir = args.work / "release"
    download_dir.mkdir(exist_ok=True)
    parts = []
    for name, digest in DB_PARTS.items():
        path = download_dir / name
        download(f"{RELEASE_BASE}/{name}", path, digest)
        parts.append(path)
    database = args.work / "seforim.db"
    extract_database(parts, database)
    database_sha = sha256(database)
    print("Database SHA-256:", database_sha, flush=True)
    with sqlite3.connect(f"file:{database}?mode=ro", uri=True) as connection:
        print("Database rows:", connection.execute("SELECT COUNT(*) FROM line").fetchone()[0], flush=True)
        if connection.execute("PRAGMA quick_check").fetchone()[0] != "ok":
            raise ValueError("The extracted database failed SQLite quick_check")
    for part in parts:
        part.unlink()

    checkpoint, model_dir = fetch_models(args.work)
    java_home = install_java25(args.work)
    env = dict(os.environ, JAVA_HOME=str(java_home), RAYON_NUM_THREADS="2", OMP_NUM_THREADS="2")
    vectors_dir = args.work / "vectors"
    vectors_dir.mkdir(exist_ok=True)
    embed_script = args.repo / "scripts/kaggle_embed_vectors.py"
    processes = []
    for gpu in range(2):
        command = [
            sys.executable, str(embed_script), "--db", str(database), "--checkpoint", str(checkpoint),
            "--output", str(vectors_dir), "--gpu", str(gpu), "--gpus", "2", "--shards", "8",
            "--batch", str(args.batch), "--onnx", str(model_dir / "seforim-embed-round2-int8.onnx"),
        ]
        if args.amp:
            command.append("--amp")
        processes.append(subprocess.Popen(command, env=env))
    statuses = [process.wait() for process in processes]
    if any(status != 0 for status in statuses):
        raise RuntimeError(f"GPU encoding failed: {statuses}")
    counts = [json.loads((vectors_dir / f"worker-{gpu}.json").read_text()) for gpu in range(2)]
    print("GPU workers:", counts, flush=True)

    library = args.repo / "SeforimLibrary"
    gradle = library / "gradlew"
    gradle.chmod(gradle.stat().st_mode | 0o111)
    index = args.work / "index"
    for shard in range(8):
        vector_file = vectors_dir / f"shard-{shard:02d}.bin"
        run_command([
            str(gradle), ":search:buildSemanticIndexFromVectors",
            f"-PseforimDb={database}", f"-PsemanticModelDir={model_dir}",
            f"-PsemanticVectors={vector_file}", f"-PsemanticIndexDir={index}",
            f"-PshardIndex={shard}", "-PshardCount=8", "--no-daemon", "--max-workers=4",
        ], cwd=library, env=env)
        vector_file.unlink()

    archive = args.output / "semantic-bundle.tar.zst"
    run_command([
        str(gradle), ":packaging:packageSemanticBundle",
        f"-PseforimDb={database}", f"-PsemanticModelDir={model_dir}",
        f"-PsemanticIndexDir={index}", f"-PsemanticBundleOutput={archive}",
        "--no-daemon", "--max-workers=4",
    ], cwd=library, env=env)
    outputs = sorted(args.output.glob("semantic-bundle.tar.zst*"))
    manifest = args.output / "semantic-bundle.json"
    outputs.append(manifest)
    checksums = args.output / "checksums.sha256"
    checksums.write_text("".join(f"{sha256(path)}  {path.name}\n" for path in outputs), encoding="utf-8")
    print("Ready for download:", *[str(path) for path in outputs + [checksums]], sep="\n", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--work", type=Path, default=Path("/kaggle/temp/zayit-semantic"))
    parser.add_argument("--output", type=Path, default=Path("/kaggle/working"))
    parser.add_argument("--batch", type=int, default=128)
    parser.add_argument("--amp", action=argparse.BooleanOptionalAction, default=True)
    main(parser.parse_args())
