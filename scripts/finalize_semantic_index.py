"""CPU-only GitHub release preparation, level-22 packaging and draft uploads."""
from __future__ import annotations

import argparse
import contextlib
import hashlib
import json
import os
import re
import subprocess
import tarfile
from pathlib import Path
from urllib.parse import quote

import requests
import zstandard

from kaggle_publish_distributions import Publisher, extract_complete
from kaggle_semantic_stream import PartsReader
from semantic_transfer import PART_BYTES, SplitWriter, sha256, unpack, validate_index

MODEL_SHA = "659226865abd3a1bc833565ae6b2e2f48abdd7136285824a12966d4d3294cbf8"
TOKENIZER_SHA = "0664287976ecb078bdfd8f5e5515dc87d8cb7f985a79a481aa1cdf7a7321c0e9"
BASE_FILES = ("seforim.db", "seforim.db.lucene", "seforim.db.lookup.lucene", "catalog.pb", "lexical.db", "release_info.txt")
PDF_DIRECTORY = "תלמוד בבלי"
COMPRESSION_JOB_BYTES = 128 * 1024 * 1024


def require_complete_base(base):
    for name in BASE_FILES:
        path = base / name
        if not path.exists() or (path.is_file() and not path.stat().st_size):
            raise ValueError(f"Base distribution is missing {name}")
        if path.is_dir() and not any(path.rglob("segments_*")):
            raise ValueError(f"Base distribution has no completed Lucene index: {name}")
    if not any((base / PDF_DIRECTORY).rglob("*.pdf")):
        raise ValueError("Complete distribution is missing Talmud PDFs")


def transfer_location(args):
    repository = args.transfer_repository or args.db_repository
    token = os.environ["DB_TOKEN"] if repository == args.db_repository else os.environ["GH_TOKEN"]
    return repository, token


def download_asset(asset, target, token):
    """Pin every input to the digest returned by the selected GitHub release."""
    digest = asset.get("digest", "") or ""
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
        raise ValueError(f"Missing asset checksum: {asset['name']}")
    target = Path(target)
    temporary = target.with_name(target.name + ".download")
    with requests.get(asset["url"], headers={"Authorization": f"Bearer {token}",
                      "Accept": "application/octet-stream"}, stream=True, timeout=(30, 120)) as response:
        response.raise_for_status()
        with temporary.open("wb") as output:
            for chunk in response.iter_content(1 << 20):
                if chunk:
                    output.write(chunk)
    if temporary.stat().st_size != asset["size"] or sha256(temporary) != digest[7:]:
        raise ValueError(f"Damaged release asset: {asset['name']}")
    temporary.replace(target)


def get_release(repository, tag, token):
    return Publisher(token).api("GET", f"repos/{repository}/releases/tags/{quote(tag, safe='')}")


def prepare(args):
    db_token = os.environ["DB_TOKEN"]
    hf_token = os.environ["HF_TOKEN"]
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=False)
    transfer_dir = work / "transfer"
    transfer_dir.mkdir()
    transfer_repository, transfer_token = transfer_location(args)
    transfer_release = get_release(transfer_repository, args.transfer_release_tag, transfer_token)
    assets = {asset["name"]: asset for asset in transfer_release["assets"]}
    download_asset(assets["semantic-transfer.json"], transfer_dir / "semantic-transfer.json", transfer_token)
    transfer = json.loads((transfer_dir / "semantic-transfer.json").read_text(encoding="utf-8"))
    if (transfer.get("databaseReleaseTag") != args.db_release_tag
            or transfer.get("modelSha256") != MODEL_SHA or transfer.get("tokenizerSha256") != TOKENIZER_SHA):
        raise ValueError("Transfer does not match the selected database and approved model")
    for number, part in enumerate(transfer["parts"], 1):
        if part["file"] != f"semantic-index.tar.zst.part{number:02d}":
            raise ValueError("Invalid transfer part name")
        download_asset(assets[part["file"]], transfer_dir / part["file"], transfer_token)
    unpack(transfer_dir, work / "semantic")
    for part in transfer["parts"]:
        (transfer_dir / part["file"]).unlink()

    release = get_release(args.db_repository, args.db_release_tag, db_token)
    db_dir = work / "database-download"
    db_dir.mkdir()
    parts = []
    for prefix in ("seforim_bundle-database-only.tar.zst", "seforim_bundle.tar.zst"):
        selected = sorted((asset for asset in release["assets"]
                           if re.fullmatch(re.escape(prefix) + r"\.part[0-9]+", asset["name"])),
                          key=lambda asset: int(asset["name"].rsplit("part", 1)[1]))
        if selected:
            if [asset["name"] for asset in selected] != [prefix + f".part{i + 1:02d}" for i in range(len(selected))]:
                raise ValueError("Database parts are missing")
        else:
            selected = [asset for asset in release["assets"] if asset["name"] == prefix]
        if selected:
            for asset in selected:
                path = db_dir / asset["name"]
                download_asset(asset, path, db_token)
                parts.append(path)
            break
    if not parts:
        raise ValueError("The database release has no complete base bundle")
    base = work / "base"
    base.mkdir()
    extract_complete(parts, base, PartsReader)
    for part in parts:
        part.unlink()
    if not any((base / PDF_DIRECTORY).rglob("*.pdf")):
        pdf_release = get_release(args.db_repository, args.pdf_release_tag, db_token)
        pdf_asset = next((asset for asset in pdf_release["assets"]
                          if asset["name"] == "talmud_bavli_latest.tar.zst"), None)
        if pdf_asset is None:
            raise ValueError("Selected PDF release has no complete Talmud archive")
        pdf_archive = db_dir / pdf_asset["name"]
        download_asset(pdf_asset, pdf_archive, db_token)
        pdf_stage = work / "pdf-supplement"
        pdf_stage.mkdir()
        extract_complete([pdf_archive], pdf_stage, PartsReader)
        import shutil
        shutil.copytree(pdf_stage / PDF_DIRECTORY, base / PDF_DIRECTORY, dirs_exist_ok=True)
        pdf_archive.unlink()
    require_complete_base(base)
    if sha256(base / "seforim.db") != transfer["databaseSha256"]:
        raise ValueError("Semantic index was generated from a different database")
    model = work / "semantic" / "model"
    model.mkdir()
    for name, digest in (("seforim-embed-round2-int8.onnx", MODEL_SHA), ("tokenizer.json", TOKENIZER_SHA)):
        path = model / name
        with requests.get(f"https://huggingface.co/ArieLLL123/Meivin-Embed-0.1/resolve/main/{name}",
                          headers={"Authorization": f"Bearer {hf_token}"}, stream=True, timeout=(30, 120)) as response:
            response.raise_for_status()
            with path.open("xb") as output:
                for chunk in response.iter_content(1 << 20):
                    if chunk:
                        output.write(chunk)
        if sha256(path) != digest:
            raise ValueError(f"Unexpected model file: {name}")
    print("Verified matching database, complete index and approved model", flush=True)


def package(mappings, output, *, part_bytes=PART_BYTES):
    """Recompress the contents as one level-22 stream; never concatenate old frames."""
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    writer = SplitWriter(output, part_bytes)
    names = set()
    contents = {}
    try:
        parameters = zstandard.ZstdCompressionParameters.from_level(
            22, threads=2, job_size=COMPRESSION_JOB_BYTES, write_checksum=1,
        )
        with zstandard.ZstdCompressor(compression_params=parameters).stream_writer(writer, closefd=False) as compressed:
            with tarfile.open(fileobj=compressed, mode="w|") as archive:
                for source, prefix in mappings:
                    source = Path(source)
                    files = sorted(source.rglob("*")) if source.is_dir() else [source]
                    for path in files:
                        if path.is_symlink():
                            raise ValueError("Archive inputs must not contain symbolic links")
                        if path.is_file() and path.name != "write.lock":
                            name = prefix + ("/" + path.relative_to(source).as_posix() if source.is_dir() else "")
                            if name in names or name.startswith("/") or ".." in name.split("/"):
                                raise ValueError(f"Duplicate or unsafe archive path: {name}")
                            names.add(name)
                            contents[name] = {"size": path.stat().st_size, "sha256": sha256(path)}
                            archive.add(path, arcname=name, recursive=False)
    finally:
        writer.close()
    manifest = {"formatVersion": 1, "file": output.name, "size": writer.total,
                "sha256": writer.digest.hexdigest(), "parts": writer.parts, "compressionLevel": 22,
                "contents": contents}
    verify_package(output.parent, manifest)
    return manifest


def verify_package(directory, manifest):
    paths = [directory / part["file"] for part in manifest["parts"]]
    whole = hashlib.sha256()
    for path, part in zip(paths, manifest["parts"]):
        if path.stat().st_size != part["size"] or sha256(path) != part["sha256"]:
            raise ValueError(f"Damaged archive part: {path.name}")
        with path.open("rb") as source:
            for chunk in iter(lambda: source.read(1 << 20), b""):
                whole.update(chunk)
    if whole.hexdigest() != manifest["sha256"]:
        raise ValueError("Archive checksum mismatch")
    seen = set()
    with contextlib.closing(PartsReader(paths)) as source:
        with zstandard.ZstdDecompressor().stream_reader(source) as decompressed:
            with tarfile.open(fileobj=decompressed, mode="r|") as archive:
                for member in archive:
                    expected = manifest["contents"].get(member.name)
                    if not member.isfile() or expected is None or member.name in seen:
                        raise ValueError(f"Unexpected archived entry: {member.name}")
                    digest = hashlib.sha256()
                    with archive.extractfile(member) as content:
                        for chunk in iter(lambda: content.read(1 << 20), b""):
                            digest.update(chunk)
                    if member.size != expected["size"] or digest.hexdigest() != expected["sha256"]:
                        raise ValueError(f"Damaged archived file: {member.name}")
                    seen.add(member.name)
            while decompressed.read(1 << 20):
                pass
    if seen != set(manifest["contents"]):
        raise ValueError("Archive is missing required files")


def finish(args):
    work = args.work.resolve()
    base, semantic = work / "base", work / "semantic"
    require_complete_base(base)
    transfer = json.loads((work / "transfer/semantic-transfer.json").read_text(encoding="utf-8"))
    validate_index(semantic / "index", transfer["databaseSha256"], MODEL_SHA, TOKENIZER_SHA)
    if sha256(base / "seforim.db") != transfer["databaseSha256"]:
        raise ValueError("Database fingerprint changed after preparation")
    for name, digest in (("seforim-embed-round2-int8.onnx", MODEL_SHA), ("tokenizer.json", TOKENIZER_SHA)):
        if sha256(semantic / "model" / name) != digest:
            raise ValueError(f"Unexpected model file: {name}")
    env = dict(os.environ)
    wrapper = "gradlew.bat" if os.name == "nt" else "gradlew"
    subprocess.run([str(args.repo.resolve() / "SeforimLibrary" / wrapper), ":search:optimizeSemanticIndex",
                    f"-PseforimDb={base / 'seforim.db'}", f"-PsemanticModelDir={semantic / 'model'}",
                    f"-PsemanticIndexDir={semantic / 'index'}", "-PsemanticHeap=4g",
                    "--no-daemon", "--no-configuration-cache", "--max-workers=2"],
                   cwd=args.repo / "SeforimLibrary", env=env, check=True)
    db_publisher = Publisher(os.environ["DB_TOKEN"])
    suffix = os.environ["GITHUB_RUN_ID"] + "-" + os.environ.get("GITHUB_RUN_ATTEMPT", "1")
    db_tag, app_tag = args.db_release_tag + "-semantic-" + suffix, "semantic-round2-" + suffix
    notes = (f"Verified semantic index for {args.db_repository}@{args.db_release_tag}. "
             "Model: Judaic Semantic Embedding project, Otzaria; CC BY-NC-SA 4.0. "
             "Repacked using zstd level 22. Draft for review; existing releases are unchanged.")
    # Allocate the output draft before doing the expensive compression.
    db_release = db_publisher.draft(args.db_repository, db_tag, notes)
    output = work / "output"
    variants = [
        (db_publisher, args.db_repository, db_release, "seforim_bundle.tar.zst",
         [(base / name, name) for name in BASE_FILES]
         + [(base / PDF_DIRECTORY, PDF_DIRECTORY)]
         + [(semantic, "seforim.db.semantic")], "bundle-manifest.json"),
    ]
    app_release = None
    if not args.full_bundle_only:
        app_publisher = Publisher(os.environ["GH_TOKEN"])
        app_release = app_publisher.draft(args.app_repository, app_tag, notes)
        variants.insert(0, (
            app_publisher, args.app_repository, app_release, "semantic-bundle.tar.zst",
            [(semantic / "model", "model"), (semantic / "index", "index")], "semantic-archive-manifest.json",
        ))
    for publisher, repository, release, filename, mappings, manifest_name in variants:
        manifest = package(mappings, output / filename)
        manifest.update(databaseSha256=transfer["databaseSha256"], databaseReleaseTag=args.db_release_tag,
                        transferReleaseTag=args.transfer_release_tag)
        for part in manifest["parts"]:
            publisher.upload(repository, release, output / part["file"], part["sha256"])
        path = output / manifest_name
        path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        publisher.upload(repository, release, path, sha256(path))
        if filename == "semantic-bundle.tar.zst":
            install_manifest = {"format": "zayit-round2-1", "archiveType": "tar.zst",
                                "databaseSha256": transfer["databaseSha256"], "parts": len(manifest["parts"])}
            path = output / "semantic-bundle.json"
            path.write_text(json.dumps(install_manifest) + "\n", encoding="utf-8")
            publisher.upload(repository, release, path, sha256(path))
        for part in manifest["parts"]:
            (output / part["file"]).unlink()
    with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as summary:
        summary.write(f"Verified draft outputs (zstd level 22):\n\n"
                      f"- Database with semantic search: {db_release['html_url']}\n"
                      f"- Database SHA-256: `{transfer['databaseSha256']}`\n")
        if app_release is not None:
            summary.write(f"- Optional semantic supplement: {app_release['html_url']}\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("stage", choices=("prepare", "finish"))
    parser.add_argument("--repo", type=Path, default=Path.cwd())
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--db-release-tag", required=True)
    parser.add_argument("--transfer-release-tag", required=True)
    parser.add_argument("--db-repository", default="arieldaniely/SeforimLibrary")
    parser.add_argument("--transfer-repository", help="Defaults to the database repository containing the Kaggle transfer")
    parser.add_argument("--pdf-release-tag", default="v2-20261001115917-full")
    parser.add_argument("--app-repository", default="arieldaniely/Zayit")
    parser.add_argument("--full-bundle-only", action="store_true",
                        help="Create only the complete database distribution, without a second app release")
    args = parser.parse_args()
    (prepare if args.stage == "prepare" else finish)(args)
