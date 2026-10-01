"""End-to-end Kaggle T4 x2 build from the published SeforimLibrary DB release."""

from __future__ import annotations

import argparse
import contextlib
import hashlib
import json
import os
import re
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


def download(url: str, target: Path, expected_sha: str, *, token: str | None = None) -> None:
    if target.is_file() and sha256(target) == expected_sha:
        print(f"Verified existing {target.name}", flush=True)
        return
    partial = target.with_name(target.name + ".download")
    offset = partial.stat().st_size if partial.exists() else 0
    headers = {"Range": f"bytes={offset}-"} if offset else {}
    if token:
        headers.update(Authorization=f"Bearer {token}", Accept="application/octet-stream")
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


def release_assets(repository: str, tag: str, token: str | None) -> list[dict]:
    from urllib.parse import quote

    headers = {"Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    response = requests.get(
        f"https://api.github.com/repos/{repository}/releases/tags/{quote(tag, safe='')}",
        headers=headers, timeout=(30, 120),
    )
    response.raise_for_status()
    assets = response.json()["assets"]
    for prefix in ("seforim_bundle-database-only.tar.zst", "seforim_bundle.tar.zst"):
        single = [asset for asset in assets if asset["name"] == prefix]
        parts = sorted(
            (asset for asset in assets if re.fullmatch(re.escape(prefix) + r"\.part\d+", asset["name"])),
            key=lambda asset: int(asset["name"].rsplit("part", 1)[1]),
        )
        selected = parts or single
        if not selected:
            continue
        if parts and [int(asset["name"].rsplit("part", 1)[1]) for asset in parts] != list(range(1, len(parts) + 1)):
            raise ValueError("Database archive parts are not consecutive")
        for asset in selected:
            if not re.fullmatch(r"sha256:[0-9a-f]{64}", asset.get("digest") or ""):
                raise ValueError(f"Release asset has no SHA-256 digest: {asset['name']}")
        return selected
    raise FileNotFoundError(f"No database bundle in {repository}@{tag}")


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
    token = os.getenv("GH_TOKEN")
    if args.all_distributions and not token:
        from kaggle_secrets import UserSecretsClient

        token = UserSecretsClient().get_secret("GH_TOKEN")
    if args.all_distributions:
        from kaggle_publish_distributions import Publisher

        final_tag = args.publish_tag or args.db_release_tag + '-full'
        if final_tag == args.db_release_tag:
            raise ValueError('Final release must use a new tag')
        publisher = Publisher(token)
        publisher.draft(args.db_repository, final_tag, f'Distributions for {args.db_release_tag}')
        publisher.draft(args.app_repository, 'semantic-round2-' + final_tag, f'Semantic index for {args.db_release_tag}')
    assets = release_assets(args.db_repository, args.db_release_tag, token)
    identity = {"repository": args.db_repository, "tag": args.db_release_tag,
                "allDistributions": args.all_distributions,
                "assets": [{"name": asset["name"], "digest": asset["digest"]} for asset in assets]}
    marker = args.work / "database-source.json"
    if marker.exists():
        if json.loads(marker.read_text()) != identity:
            raise RuntimeError("This work directory belongs to another database; select a new --work directory")
    else:
        if (args.work / "seforim.db").exists() or (args.work / "index-single").exists():
            raise RuntimeError("Untracked previous build; select a new --work directory")
        marker.write_text(json.dumps(identity), encoding="utf-8")
    download_dir = args.work / "release"
    download_dir.mkdir(exist_ok=True)
    parts = []
    for asset in assets:
        name, digest = asset["name"], asset["digest"].removeprefix("sha256:")
        path = download_dir / name
        download(asset["url"] if token else asset["browser_download_url"], path, digest, token=token)
        parts.append(path)
    database = args.work / "seforim.db"
    if args.all_distributions:
        from kaggle_publish_distributions import extract_complete

        extract_complete(parts, args.work, MultipartStream)
        for name in ('seforim.db', 'seforim.db.lucene', 'seforim.db.lookup.lucene',
                     'catalog.pb', 'lexical.db', 'release_info.txt'):
            if not (args.work / name).exists():
                raise FileNotFoundError(f'Missing base artifact: {name}')
    else:
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
    vectors_dir = args.work / "vectors-single"
    vectors_dir.mkdir(exist_ok=True)
    embed_script = args.repo / "scripts/kaggle_embed_vectors.py"
    worker_files = [vectors_dir / f"worker-{gpu}.json" for gpu in range(2)]
    vector_files = [vectors_dir / f"shard-{shard:02d}.bin" for shard in range(2)]
    index = args.work / "index-single"
    has_workers = all(f.is_file() and json.loads(f.read_text()).get("vectorEncoding") == "int8-maxabs-v1"
                      for f in worker_files)
    has_vectors = all(f.is_file() for f in vector_files)
    has_indexed = (index / "shard-00" / "semantic.properties").is_file()

    if has_workers and (has_vectors or has_indexed):
        print("GPU encoding already completed; skipping vector generation", flush=True)
        counts = [json.loads(f.read_text()) for f in worker_files]
        print("GPU workers:", counts, flush=True)
    else:
        processes = []
        for gpu in range(2):
            command = [
                sys.executable, str(embed_script), "--db", str(database), "--checkpoint", str(checkpoint),
                "--output", str(vectors_dir), "--gpu", str(gpu), "--gpus", "2", "--shards", "2",
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
    if any(worker.get("vectorEncoding") != "int8-maxabs-v1" for worker in counts):
        raise RuntimeError("GPU workers must produce int8 vectors")
    gradle = library / "gradlew"
    gradle.chmod(gradle.stat().st_mode | 0o111)
    index.mkdir(exist_ok=True)
    # Both GPUs write temporary vector streams; one writer builds the final index.
    manifest = index / "shard-00" / "semantic.properties"
    expected_vectors = sum(sum(worker["counts"].values()) for worker in counts)
    properties = {}
    if manifest.is_file():
        content = manifest.read_text(encoding="utf-8", errors="ignore")
        properties = dict(line.split("=", 1) for line in content.splitlines()
                          if "=" in line and not line.startswith("#"))
    complete = (
        properties.get("format") == "zayit-round2-1"
        and properties.get("shardIndex") == "0"
        and properties.get("shardCount") == "1"
        and properties.get("vectorEncoding") == "int8-maxabs-v1"
        and properties.get("databaseSha256") == database_sha
        and properties.get("modelSha256") == MODEL_SHA
        and properties.get("tokenizerSha256") == TOKENIZER_SHA
        and properties.get("dimension") == "256"
        and expected_vectors > 0
        and properties.get("indexed") == str(expected_vectors)
        and properties.get("eligible") == str(expected_vectors)
    )
    if complete:
        print("Skipping completed unified index", flush=True)
    else:
        for gpu, path in enumerate(vector_files):
            expected_bytes = 12 + sum(counts[gpu]["counts"].values()) * (20 + 256)
            if not path.is_file() or path.stat().st_size != expected_bytes:
                raise ValueError(f"Missing or incomplete GPU vector file: {path}")
        run_command([
            str(gradle), ":search:buildSemanticIndexFromVectors",
            f"-PseforimDb={database}", f"-PsemanticModelDir={model_dir}",
            f"-PsemanticVectors={vectors_dir}", f"-PsemanticIndexDir={index}",
            "-PshardIndex=0", "-PshardCount=1",
            "--no-daemon", "--no-configuration-cache", "--max-workers=4",
        ], cwd=library, env=env)
    for path in vector_files:
        path.unlink(missing_ok=True)

    if args.all_distributions:
        from kaggle_publish_distributions import build_distributions

        build_distributions(args, database, model_dir, index, env, token, sha256, download,
                            run_command, MultipartStream)
        return

    archive = args.output / "semantic-bundle.tar.zst"
    run_command([
        str(gradle), ":packaging:packageSemanticBundle",
        f"-PseforimDb={database}", f"-PsemanticModelDir={model_dir}",
        f"-PsemanticIndexDir={index}", f"-PsemanticBundleOutput={archive}",
        "--no-daemon", "--no-configuration-cache", "--max-workers=4",
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
    parser.add_argument("--db-release-tag", required=True)
    parser.add_argument("--db-repository", default="arieldaniely/SeforimLibrary")
    parser.add_argument("--all-distributions", action="store_true")
    parser.add_argument("--publish-tag")
    parser.add_argument("--pdf-release-tag")
    parser.add_argument("--app-repository", default="arieldaniely/Zayit")
    parser.add_argument("--work", type=Path, default=Path("/kaggle/temp/zayit-semantic"))
    parser.add_argument("--output", type=Path, default=Path("/kaggle/working"))
    parser.add_argument("--batch", type=int, default=128)
    parser.add_argument("--amp", action=argparse.BooleanOptionalAction, default=True)
    main(parser.parse_args())
