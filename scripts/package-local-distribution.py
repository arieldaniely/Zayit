"""Stream a complete local distribution at Zstandard level 22 directly into <2 GB parts.

No second copy of the archive is made. Verification re-reads every archived byte
and compares content hashes; optionally extracts semantic assets for Lucene checks.
"""

import argparse
import hashlib
import io
import json
import os
import tarfile
import time
from pathlib import Path

import zstandard

PART_BYTES = 1_900_000_000
JOB_BYTES = 128 * 1024 * 1024


class SplitWriter:
    def __init__(self, directory, part_bytes=PART_BYTES):
        if not 0 < part_bytes < 2_000_000_000:
            raise ValueError('Parts must be strictly smaller than 2 GB')
        self.directory, self.limit = directory, part_bytes
        self.parts, self.output, self.size = [], None, 0
        self.digest = None

    def write(self, data):
        view = memoryview(data)
        size = len(view)
        while view:
            if self.output is None:
                path = self.directory / f'seforim_bundle.tar.zst.part{len(self.parts) + 1:02d}'
                self.output = path.with_name(path.name + '.partial').open('xb')
                self.digest, self.size = hashlib.sha256(), 0
            count = min(len(view), self.limit - self.size)
            chunk = view[:count]
            self.output.write(chunk)
            self.digest.update(chunk)
            self.size += count
            view = view[count:]
            if self.size == self.limit:
                self.finish_part()
        return size

    def flush(self):
        if self.output:
            self.output.flush()

    def finish_part(self):
        if self.output:
            path = Path(self.output.name)
            self.output.flush()
            os.fsync(self.output.fileno())
            self.output.close()
            target = path.with_name(path.name.removesuffix('.partial'))
            path.rename(target)
            self.parts.append(dict(name=target.name, bytes=self.size, sha256=self.digest.hexdigest()))
            print(f'Completed {target.name}: {self.size:,} bytes', flush=True)
            self.output = None


class HashReader:
    def __init__(self, source):
        self.source, self.digest = source, hashlib.sha256()

    def read(self, size=-1):
        data = self.source.read(size)
        self.digest.update(data)
        return data


class PartsReader(io.RawIOBase):
    def __init__(self, paths):
        self.paths, self.current = iter(paths), None

    def readable(self):
        return True

    def readinto(self, buffer):
        while True:
            if self.current is None:
                path = next(self.paths, None)
                if path is None:
                    return 0
                self.current = path.open('rb')
            count = self.current.readinto(buffer)
            if count:
                return count
            self.current.close()
            self.current = None

    def close(self):
        if self.current:
            self.current.close()
        super().close()


def sha256(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def reuse_release_base(database_dir, source_manifest, pdf_archive, output):
    """Preserve a checksummed release's compressed bytes and append its verified PDF supplement."""
    source_manifest = Path(source_manifest)
    source = json.loads(source_manifest.read_text(encoding='utf-8'))
    paths = []
    for number, part in enumerate(source['parts'], 1):
        name = part['file']
        if name != f"{source['file']}.part{number:02d}" or Path(name).name != name:
            raise ValueError('Missing or unsafe release part')
        path = source_manifest.parent / name
        if path.stat().st_size != part['size'] or sha256(path) != part['sha256']:
            raise ValueError(f'Damaged release part: {name}')
        paths.append(path)
    whole = hashlib.sha256()
    for path in paths:
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1 << 20), b''):
                whole.update(chunk)
    if whole.hexdigest() != source['sha256'] or sum(p.stat().st_size for p in paths) != source['size']:
        raise ValueError('Release archive fingerprint mismatch')
    if output.exists() and any(output.iterdir()):
        raise ValueError('Use an empty output directory')
    contents = []
    for name in ['seforim.db', 'catalog.pb', 'lexical.db', 'release_info.txt',
                 'seforim.db.lucene', 'seforim.db.lookup.lucene', 'תלמוד בבלי']:
        path = database_dir / name
        if not path.exists():
            raise FileNotFoundError(name)
        for file in sorted(path.rglob('*')) if path.is_dir() else [path]:
            if file.is_file():
                contents.append(dict(name=file.relative_to(database_dir).as_posix(),
                                     bytes=file.stat().st_size, sha256=sha256(file)))
    output.mkdir(parents=True, exist_ok=True)
    writer = SplitWriter(output)
    for path in [*paths, pdf_archive]:
        with path.open('rb') as stream:
            for chunk in iter(lambda: stream.read(1 << 20), b''):
                writer.write(chunk)
    writer.finish_part()
    manifest = dict(defaultBundle='seforim_bundle', compressionLevel=None, shardCount=0,
                    databaseSha256=next(e['sha256'] for e in contents if e['name'] == 'seforim.db'),
                    preservedRelease=dict(sourceSha256=source['sha256'], pdfSha256=sha256(pdf_archive)),
                    assets=writer.parts, contents=contents)
    (output / 'distributions.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    verify(output)


def package(database_dir, semantic_dir, output, threads=6, base_only=False, base_archive=None, compression_level=22):
    if output.exists() and any(output.iterdir()):
        raise ValueError('Use an empty output directory')
    required = ['seforim.db', 'catalog.pb', 'lexical.db', 'release_info.txt',
                'seforim.db.lucene', 'seforim.db.lookup.lucene', 'תלמוד בבלי']
    for name in required:
        if not (database_dir / name).exists():
            raise FileNotFoundError(name)
    if not any((database_dir / 'תלמוד בבלי').rglob('*.pdf')):
        raise ValueError('No PDFs')
    properties = dict(databaseSha256=sha256(database_dir / 'seforim.db'))
    if not base_only:
        if [p.name for p in (semantic_dir / 'index').iterdir() if p.is_dir()] != ['shard-00']:
            raise ValueError('Expected one completed semantic shard')
        properties = dict(line.split('=', 1) for line in (semantic_dir / 'index/shard-00/semantic.properties').read_text().splitlines()
                          if '=' in line and not line.startswith('#'))
        if properties.get('shardCount') != '1' or properties.get('vectorEncoding') != 'int8-maxabs-v1':
            raise ValueError('Expected a unified int8 index')
        for path, key in [(database_dir / 'seforim.db', 'databaseSha256'),
                          (semantic_dir / 'model/seforim-embed-round2-int8.onnx', 'modelSha256'),
                          (semantic_dir / 'model/tokenizer.json', 'tokenizerSha256')]:
            if sha256(path) != properties[key]:
                raise ValueError(f'Fingerprint mismatch: {path}')
    entries = []
    for name in ([] if base_archive else required):
        path = database_dir / name
        entries.extend((file, file.relative_to(database_dir).as_posix())
                       for file in (sorted(path.rglob('*')) if path.is_dir() else [path])
                       if file.is_file() and file.name != 'write.lock')
    if not base_only:
        entries.extend((file, 'seforim.db.semantic/' + file.relative_to(semantic_dir).as_posix())
                       for file in sorted(semantic_dir.rglob('*')) if file.is_file() and file.name != 'write.lock')
    output.mkdir(parents=True, exist_ok=True)
    writer, contents = SplitWriter(output), []
    if base_archive:
        base = json.loads((base_archive / 'distributions.json').read_text(encoding='utf-8'))
        preserved = base.get('compressionLevel') is None and base.get('preservedRelease')
        if (base['shardCount'] != 0 or (base['compressionLevel'] != 22 and not preserved)
                or base['databaseSha256'] != properties['databaseSha256']):
            raise ValueError('Incompatible staged base archive')
        contents.extend(base['contents'])
        for part in base['assets']:
            path = base_archive / part['name']
            if path.stat().st_size != part['bytes'] or sha256(path) != part['sha256']:
                raise ValueError(f'Damaged staged base part: {path}')
            with path.open('rb') as source:
                for chunk in iter(lambda: source.read(1 << 20), b''):
                    writer.write(chunk)
    started = time.monotonic()
    parameters = zstandard.ZstdCompressionParameters.from_level(
        compression_level, threads=threads, job_size=JOB_BYTES, write_checksum=1,
    )
    compressor = zstandard.ZstdCompressor(compression_params=parameters)
    with compressor.stream_writer(writer, closefd=False) as compressed:
        with tarfile.open(fileobj=compressed, mode='w|', format=tarfile.PAX_FORMAT) as archive:
            for number, (path, name) in enumerate(entries):
                before = path.stat()
                info = archive.gettarinfo(str(path), arcname=name)
                if not info.isfile() or path.is_symlink():
                    raise ValueError(f'Expected a regular file: {path}')
                with path.open('rb') as source:
                    reader = HashReader(source)
                    archive.addfile(info, reader)
                after = path.stat()
                if (before.st_size, before.st_mtime_ns) != (after.st_size, after.st_mtime_ns):
                    raise ValueError(f'Source changed while packaging: {path}')
                contents.append(dict(name=name, bytes=info.size, sha256=reader.digest.hexdigest()))
                if info.size > 50_000_000 or number % 100 == 0:
                    print(f'Packed {number + 1}/{len(entries)}: {name} ({info.size:,} bytes), elapsed {time.monotonic() - started:.0f}s', flush=True)
    writer.finish_part()
    if len(writer.parts) == 1:
        part = writer.parts[0]
        (output / part['name']).rename(output / 'seforim_bundle.tar.zst')
        part['name'] = 'seforim_bundle.tar.zst'
    fingerprints = {entry['name']: entry['sha256'] for entry in contents}
    checks = [('seforim.db', 'databaseSha256')]
    if not base_only:
        checks += [('seforim.db.semantic/model/seforim-embed-round2-int8.onnx', 'modelSha256'),
                   ('seforim.db.semantic/model/tokenizer.json', 'tokenizerSha256')]
    for name, key in checks:
        if fingerprints[name] != properties[key]:
            raise ValueError(f'Source fingerprint changed while packaging: {name}')
    manifest = dict(defaultBundle='seforim_bundle', compressionLevel=compression_level, workers=threads, jobBytes=JOB_BYTES, partBytes=PART_BYTES,
                    shardCount=0 if base_only else 1, vectorEncoding=None if base_only else 'int8-maxabs-v1',
                    databaseSha256=properties['databaseSha256'],
                    assets=writer.parts, contents=contents)
    if base_archive and base.get('preservedRelease'):
        manifest.update(compressionLevel=None, preservedRelease=base['preservedRelease'],
                        compressionStreams=['preserved release and PDF supplement', f'semantic: zstd level {compression_level}'])
    elif base_archive and base['compressionLevel'] != compression_level:
        manifest.update(compressionLevel=None,
                        compressionStreams=[f"base: zstd level {base['compressionLevel']}", f'semantic: zstd level {compression_level}'])
    (output / 'distributions.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
    (output / 'checksums.sha256').write_text(''.join(f"{p['sha256']}  {p['name']}\n" for p in writer.parts), encoding='utf-8')
    print(f'Packaged {len(contents)} files in {len(writer.parts)} parts', flush=True)


def verify(output, extract_semantic=None):
    manifest = json.loads((output / 'distributions.json').read_text(encoding='utf-8'))
    for part in manifest['assets']:
        path = output / part['name']
        if path.stat().st_size != part['bytes'] or part['bytes'] >= 2_000_000_000 or sha256(path) != part['sha256']:
            raise ValueError(f'Invalid part: {path}')
    expected = {entry['name']: entry for entry in manifest['contents']}
    seen = set()
    with PartsReader([output / p['name'] for p in manifest['assets']]) as parts:
        with zstandard.ZstdDecompressor().stream_reader(parts, read_across_frames=True) as decompressed:
            with tarfile.open(fileobj=decompressed, mode='r|', ignore_zeros=True) as archive:
                for entry in archive:
                    entry.name = entry.name.removeprefix('./').rstrip('/')
                    if entry.isdir():
                        if '..' in entry.name.split('/') or entry.name.startswith('/') or not any(
                                name.startswith(entry.name + '/') for name in expected):
                            raise ValueError(f'Unexpected directory: {entry.name}')
                        continue
                    if entry.name not in expected or entry.name in seen or not entry.isfile():
                        raise ValueError(f'Unexpected entry: {entry.name}')
                    digest = hashlib.sha256()
                    destination = None
                    if extract_semantic and entry.name.startswith('seforim.db.semantic/'):
                        relative = Path(entry.name).relative_to('seforim.db.semantic')
                        if relative.is_absolute() or '..' in relative.parts:
                            raise ValueError('Unsafe path')
                        path = extract_semantic / relative
                        path.parent.mkdir(parents=True, exist_ok=True)
                        destination = path.open('xb')
                    try:
                        with archive.extractfile(entry) as source:
                            for chunk in iter(lambda: source.read(1 << 20), b''):
                                digest.update(chunk)
                                if destination:
                                    destination.write(chunk)
                    finally:
                        if destination:
                            destination.close()
                    if entry.size != expected[entry.name]['bytes'] or digest.hexdigest() != expected[entry.name]['sha256']:
                        raise ValueError(f'Content mismatch: {entry.name}')
                    seen.add(entry.name)
                    if entry.size > 50_000_000 or len(seen) % 100 == 0:
                        print(f'Verified {len(seen)}/{len(expected)}: {entry.name}', flush=True)
            # Consume the frame trailer to validate the Zstandard checksum.
            while decompressed.read(1 << 20):
                pass
    if seen != set(expected):
        raise ValueError('Missing archive entries')
    (output / 'verification.json').write_text(json.dumps(dict(verified=True, files=len(seen),
                parts=len(manifest['assets']), uncompressedBytes=sum(e['bytes'] for e in expected.values()))), encoding='utf-8')
    print(f'Verified all {len(seen)} files and compressed parts', flush=True)


def verify_installed(output, installed):
    """Check every file written by the actual desktop installer against the release manifest."""
    manifest = json.loads((output / 'distributions.json').read_text(encoding='utf-8'))
    root = installed.resolve()
    for number, entry in enumerate(manifest['contents'], 1):
        relative = Path(entry['name'])
        if relative.is_absolute() or '..' in relative.parts:
            raise ValueError('Unsafe installed entry')
        path = (root / relative).resolve()
        if not path.is_relative_to(root) or not path.is_file():
            raise ValueError(f'Missing installed file: {entry["name"]}')
        if path.stat().st_size != entry['bytes'] or sha256(path) != entry['sha256']:
            raise ValueError(f'Installed content mismatch: {entry["name"]}')
        if entry['bytes'] > 50_000_000 or number % 100 == 0:
            print(f'Checked installed {number}/{len(manifest["contents"])}: {entry["name"]}', flush=True)
    (output / 'installed-verification.json').write_text(json.dumps(dict(
        verified=True, files=len(manifest['contents']), desktopInstalledContent=True,
    )), encoding='utf-8')
    print('PASS: every desktop-installed file matches the original release content', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--database-dir', type=Path)
    parser.add_argument('--semantic-dir', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--threads', type=int, default=6)
    parser.add_argument('--compression-level', type=int, choices=range(1, 23), default=22)
    parser.add_argument('--base-only', action='store_true', help='Stage database, text indexes and PDFs while vectors are built')
    parser.add_argument('--base-archive', type=Path, help='Append semantic assets to a verified staged base without recompressing it')
    parser.add_argument('--reuse-release-base', type=Path, help='Manifest beside original release parts to preserve their compression')
    parser.add_argument('--pdf-archive', type=Path, help='Complete PDF supplement to include in the reused base')
    parser.add_argument('--verify', action='store_true')
    parser.add_argument('--verify-installed', type=Path, help='Verify all files written by the real desktop installer')
    parser.add_argument('--extract-semantic', type=Path)
    args = parser.parse_args()
    if args.verify_installed:
        verify_installed(args.output, args.verify_installed)
    elif args.verify:
        verify(args.output, args.extract_semantic)
    elif args.reuse_release_base:
        if args.database_dir is None or args.pdf_archive is None:
            parser.error('--database-dir and --pdf-archive are required to reuse a release base')
        reuse_release_base(args.database_dir, args.reuse_release_base, args.pdf_archive, args.output)
    else:
        if args.database_dir is None or (args.semantic_dir is None and not args.base_only):
            parser.error('--database-dir and --semantic-dir are required for packaging')
        if args.base_only and args.base_archive:
            parser.error('--base-only and --base-archive are mutually exclusive')
        package(args.database_dir, args.semantic_dir, args.output, args.threads, args.base_only, args.base_archive, args.compression_level)
