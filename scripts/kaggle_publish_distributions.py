"""Package and publish six distributions sequentially, keeping archives out of saved Kaggle output."""

from __future__ import annotations

import contextlib
import uuid
import json
import re
import shutil
import tarfile
from pathlib import Path
from urllib.parse import quote

import requests


def extract_complete(parts, destination, stream_class):
    """A completion marker prevents reuse of a partially extracted bundle."""
    import zstandard

    destination = destination.resolve()
    marker = destination / '.base-extracted'
    if marker.exists():
        return
    with contextlib.closing(stream_class(parts)) as source:
        with zstandard.ZstdDecompressor().stream_reader(source) as reader:
            with tarfile.open(fileobj=reader, mode='r|') as archive:
                for member in archive:
                    target = (destination / member.name).resolve()
                    if not target.is_relative_to(destination):
                        raise ValueError('Archive entry escapes destination')
                    if member.isdir():
                        target.mkdir(parents=True, exist_ok=True)
                    elif member.isfile():
                        target.parent.mkdir(parents=True, exist_ok=True)
                        temporary = target.with_name(target.name + '.extracting')
                        with archive.extractfile(member) as content, temporary.open('wb') as output:
                            shutil.copyfileobj(content, output, 1 << 20)
                        temporary.replace(target)
                    else:
                        raise ValueError('Unsupported archive entry')
    marker.touch()


class Publisher:
    def __init__(self, token):
        self.session = requests.Session()
        self.session.headers.update(Authorization=f'Bearer {token}', Accept='application/vnd.github+json')

    def api(self, method, path, **kwargs):
        response = self.session.request(method, 'https://api.github.com/' + path, timeout=(30, 120), **kwargs)
        response.raise_for_status()
        return response.json()

    def draft(self, repository, tag, notes):
        path = f'repos/{repository}/releases'
        response = self.session.get(f'https://api.github.com/{path}/tags/{quote(tag, safe="")}', timeout=(30, 120))
        if response.status_code == 404:
            release = self.api('POST', path, json={'tag_name': tag, 'name': tag, 'body': notes, 'draft': True})
        else:
            response.raise_for_status()
            release = response.json()
        if not release['draft']:
            raise ValueError(f'Release {repository}@{tag} is already published; choose a new tag')
        return release

    def allocate(self, repository, app_repository, requested_tag, work):
        """Resume our drafts, or allocate a fresh tag even if FULL already exists."""
        state = work / 'publication.json'
        if state.exists():
            saved = json.loads(state.read_text(encoding='utf-8'))
            if (saved['repository'], saved['appRepository']) != (repository, app_repository):
                raise ValueError('Publication work directory belongs to another repository')
            releases = [self.api('GET', f'repos/{repo}/releases/{saved[key]}')
                        for repo, key in ((repository, 'releaseId'), (app_repository, 'semanticReleaseId'))]
            if all(release['draft'] for release in releases):
                return saved['tag']
        # GitHub permits only one release per tag. A unique derived tag preserves existing releases.
        while True:
            tag = requested_tag + '-' + uuid.uuid4().hex[:12]
            try:
                release = self.draft(repository, tag, 'Building all six distributions')
                semantic = self.draft(app_repository, 'semantic-round2-' + tag, 'Building semantic supplement')
                break
            except requests.HTTPError as error:
                if error.response.status_code != 422:
                    raise
        state.write_text(json.dumps({'repository': repository, 'appRepository': app_repository,
                                     'tag': tag, 'releaseId': release['id'],
                                     'semanticReleaseId': semantic['id']}), encoding='utf-8')
        print('Publication tag:', tag, flush=True)
        return tag

    def upload(self, repository, release, file, digest):
        for attempt in range(3):
            try:
                return self._upload(repository, release, file, digest)
            except requests.RequestException:
                if attempt == 2:
                    raise
                print(f'Retrying upload: {file.name}', flush=True)

    def _upload(self, repository, release, file, digest):
        # Recheck draft status before every replacement, including retries.
        current = self.api('GET', f'repos/{repository}/releases/{release["id"]}')
        if not current['draft']:
            raise ValueError('Release was published while this build was running')
        for asset in current['assets']:
            if asset['name'] != file.name:
                continue
            if asset.get('digest') == f'sha256:{digest}' and asset['size'] == file.stat().st_size:
                return asset
            response = self.session.delete(asset['url'], timeout=(30, 120))
            response.raise_for_status()
        url = release['upload_url'].split('{', 1)[0]
        with file.open('rb') as source:
            response = self.session.post(url, params={'name': file.name}, data=source,
                                         headers={'Content-Type': 'application/octet-stream'}, timeout=(30, 1800))
        response.raise_for_status()
        asset = response.json()
        if asset.get('digest') != f'sha256:{digest}' or asset['size'] != file.stat().st_size:
            raise ValueError(f'Uploaded asset checksum mismatch: {file.name}')
        print(f'Uploaded {repository}: {file.name}', flush=True)
        return asset

    def publish(self, repository, release, latest):
        return self.api('PATCH', f'repos/{repository}/releases/{release["id"]}',
                        json={'draft': False, 'prerelease': False, 'make_latest': str(latest).lower()})


def build_distributions(args, database, model_dir, index, env, token, sha256, download, run_command, stream_class):
    publisher = Publisher(token)
    tag = args.publish_tag or args.db_release_tag + '-full'
    if tag == args.db_release_tag:
        raise ValueError('Use a new final tag; the source release must remain unchanged')
    notes = f'Six distributions for database {args.db_release_tag}. Database SHA-256: {sha256(database)}'
    release = publisher.draft(args.db_repository, tag, notes)
    semantic_tag = 'semantic-round2-' + tag
    semantic_release = publisher.draft(args.app_repository, semantic_tag, notes)
    work = args.work / 'distributions'
    work.mkdir(exist_ok=True)
    pdf_response = requests.get(
        'https://api.github.com/repos/Otzaria/otzaria-library/releases/' +
        (f'tags/{quote(args.pdf_release_tag, safe="")}' if args.pdf_release_tag else 'latest'), timeout=(30, 120),
    )
    pdf_response.raise_for_status()
    pdf_release = pdf_response.json()
    pdf_asset = next(asset for asset in pdf_release['assets'] if asset['name'] == 'talmud_bavli_latest.tar.zst')
    digest = pdf_asset.get('digest') or ''
    if not re.fullmatch(r'sha256:[0-9a-f]{64}', digest):
        raise ValueError('PDF release asset has no SHA-256 digest')
    pdf_source = work / 'pdf-source.json'
    identity = {'tag': pdf_release['tag_name'], 'digest': digest}
    if pdf_source.exists() and json.loads(pdf_source.read_text()) != identity:
        raise ValueError('PDF source changed; pin --pdf-release-tag to the original PDF release')
    pdf_source.write_text(json.dumps(identity), encoding='utf-8')
    pdf_archive = work / pdf_asset['name']
    download(pdf_asset['browser_download_url'], pdf_archive, digest.removeprefix('sha256:'))
    pdf_root = work / 'pdf'
    pdf_root.mkdir(exist_ok=True)
    extract_complete([pdf_archive], pdf_root, stream_class)
    pdf_dir = pdf_root / 'תלמוד בבלי'
    if not any(pdf_dir.rglob('*.pdf')):
        raise ValueError('PDF archive does not contain the expected תלמוד בבלי directory')
    pdf_archive.unlink()
    for name in ('seforim.db.lucene', 'seforim.db.lookup.lucene', 'catalog.pb', 'lexical.db', 'release_info.txt'):
        if not (database.parent / name).exists():
            raise FileNotFoundError(f'Missing base artifact: {name}')
    semantic_dir = work / 'semantic-single'
    semantic_dir.mkdir(exist_ok=True)
    # Links avoid copying large indexes; the existing JVM packager traverses their contents.
    for name, target in (('model', model_dir), ('index', index)):
        link = semantic_dir / name
        if not link.exists():
            link.symlink_to(target.resolve(), target_is_directory=True)
    library = args.repo / 'SeforimLibrary'
    gradle = str(library / 'gradlew')
    entries = []
    variants = [
        ('seforim_bundle-database-only', False, False, False),
        ('seforim_bundle-no-vectors', True, False, False),
        ('seforim_bundle-no-pdf', False, True, False),
        ('seforim_bundle', True, True, False),
        ('talmud_bavli_latest', True, False, True),
        ('semantic-bundle', False, True, False),
    ]
    for name, pdf, vectors, pdf_only in variants:
        stage = work / name
        stage.mkdir(exist_ok=True)
        archive = stage / (name + '.tar.zst')
        # A retry rebuilds this variant; remove only its prior generated archive files.
        for old in stage.glob(archive.name + '*'):
            if old.is_file():
                old.unlink()
        common = ['--no-daemon', '--no-configuration-cache', '--max-workers=4']
        if name == 'semantic-bundle':
            command = [gradle, ':packaging:packageSemanticBundle', f'-PseforimDb={database}',
                       f'-PsemanticModelDir={model_dir}', f'-PsemanticIndexDir={index}',
                       f'-PsemanticBundleOutput={archive}', '-PzstdLevel=22']
        else:
            command = [gradle, ':packaging:packageArtifacts', f'-PseforimDb={database}',
                       f'-PpdfLibraryDir={pdf_dir}', f'-PsemanticBundleDir={semantic_dir}',
                       f'-PincludePdf={str(pdf).lower()}', f'-PincludeVectors={str(vectors).lower()}',
                       f'-PpdfOnly={str(pdf_only).lower()}', f'-PbundleOutput={archive}', '-PzstdLevel=22',
                       '-x', ':packaging:writeReleaseInfo', '-x', ':packaging:downloadLexicalDb']
        run_command(command + common, cwd=library, env=env)
        parts = sorted(stage.glob(archive.name + '.part*'))
        files = parts or [archive]
        if name == 'semantic-bundle':
            files.append(stage / 'semantic-bundle.json')
        for file in files:
            if not file.is_file() or file.stat().st_size >= 2**31:
                raise ValueError(f'Asset missing or exceeds GitHub 2 GiB limit: {file.name}')
            digest = sha256(file)
            asset = publisher.upload(args.db_repository, release, file, digest)
            entries.append({'name': file.name, 'bytes': file.stat().st_size, 'sha256': digest,
                            'url': asset['browser_download_url']})
            if name == 'semantic-bundle':
                publisher.upload(args.app_repository, semantic_release, file, digest)
            file.unlink()
        # The JVM base packager retains the unsplit archive alongside its parts.
        archive.unlink(missing_ok=True)
    manifest = args.output / 'distributions.json'
    manifest.write_text(json.dumps({'databaseSha256': sha256(database), 'databaseRelease': args.db_release_tag,
                                   'compressionLevel': 22, 'vectorEncoding': 'int8-maxabs-v1', 'shardCount': 1,
                                   'releaseTag': tag, 'pdfSource': identity, 'defaultBundle': 'seforim_bundle', 'assets': entries},
                                  indent=2), encoding='utf-8')
    checksums = args.output / 'checksums.sha256'
    checksums.write_text(''.join(f"{entry['sha256']}  {entry['name']}\n" for entry in entries), encoding='utf-8')
    for file in (manifest, checksums):
        publisher.upload(args.db_repository, release, file, sha256(file))
    publisher.publish(args.app_repository, semantic_release, False)
    publisher.publish(args.db_repository, release, True)
    url = f'https://github.com/{args.db_repository}/releases/tag/{tag}'
    (args.output / 'published-release.txt').write_text(url + '\n', encoding='utf-8')
    print('Published all six distributions:', url, flush=True)
