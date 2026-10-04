"""CPU-only regression checks for publication and the runnable notebook."""

import ast
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from kaggle_publish_distributions import Publisher, build_distributions, concatenate_archives


class FakePublisher(Publisher):
    def __init__(self, token=None):
        self.releases = {}
        self.created = []

    def draft(self, repository, tag, notes):
        key = (repository, tag)
        if key in self.releases:
            release = self.releases[key]
            if not release['draft']:
                raise ValueError('Already published')
            return release
        release = {'id': len(self.created) + 1, 'draft': True}
        self.releases[key] = release
        self.created.append(key)
        return release

    def api(self, method, path, **kwargs):
        release_id = int(path.rsplit('/', 1)[1])
        return next(release for release in self.releases.values() if release['id'] == release_id)


class PublicationTest(unittest.TestCase):
    def test_existing_full_does_not_block_new_release_and_drafts_resume(self):
        publisher = FakePublisher()
        publisher.releases[('db', 'base-full')] = {'id': 99, 'draft': False}
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            tag = publisher.allocate('db', 'app', 'base-full', work)
            self.assertNotEqual('base-full', tag)
            self.assertEqual(tag, publisher.allocate('db', 'app', 'base-full', work))
            self.assertEqual(2, len(publisher.created))
            self.assertEqual(('app', 'semantic-round2-' + tag), publisher.created[1])
            for release in publisher.releases.values():
                release['draft'] = False
            new_tag = publisher.allocate('db', 'app', 'base-full', work)
            self.assertNotEqual(tag, new_tag)
            self.assertEqual(4, len(publisher.created))

    def test_partial_publication_allocates_another_pair(self):
        publisher = FakePublisher()
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            tag = publisher.allocate('db', 'app', 'base-full', work)
            publisher.releases[('app', 'semantic-round2-' + tag)]['draft'] = False
            self.assertNotEqual(tag, publisher.allocate('db', 'app', 'base-full', work))

    def test_other_repository_cannot_resume_publication(self):
        publisher = FakePublisher()
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            publisher.allocate('db', 'app', 'base-full', work)
            with self.assertRaises(ValueError):
                publisher.allocate('other-db', 'app', 'base-full', work)

    def test_notebook_code_and_embedded_scripts_match_repository(self):
        root = Path(__file__).resolve().parents[1]
        notebook = json.loads((root / 'notebooks/build_semantic_index_kaggle_t4x2.ipynb').read_text(encoding='utf-8'))
        for cell in notebook['cells']:
            if cell['cell_type'] != 'code' or cell['id'] == 'dependencies':
                continue
            tree = ast.parse(''.join(cell['source']))
            if cell['id'] == 'release-tools':
                assignment = next(node for node in tree.body if isinstance(node, ast.Assign))
                for name, content in ast.literal_eval(assignment.value).items():
                    # Kotlin/Gradle entries intentionally overlay the upstream library checkout.
                    if name.startswith('scripts/'):
                        self.assertEqual((root / name).read_text(encoding='utf-8'), content, name)

    def test_concatenation_splits_across_source_and_frame_boundaries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = [root / 'base.part01', root / 'base.part02', root / 'vectors']
            for source, data in zip(sources, (b'abc', b'defgh', b'ijklm')):
                source.write_bytes(data)
            parts = concatenate_archives(sources, root / 'bundle.tar.zst', split_bytes=5)
            self.assertEqual([b'abcde', b'fghij', b'klm'], [part.read_bytes() for part in parts])
            self.assertEqual(['bundle.tar.zst.part01', 'bundle.tar.zst.part02', 'bundle.tar.zst.part03'],
                             [part.name for part in parts])

    def test_failed_concatenation_removes_partial_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / 'base'
            source.write_bytes(b'abc')
            with self.assertRaises(FileNotFoundError):
                concatenate_archives([source, root / 'missing'], root / 'bundle.tar.zst', split_bytes=2)
            self.assertEqual([source], list(root.iterdir()))

    def test_six_distributions_use_level_22_and_publish_after_uploads(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / 'seforim.db'
            database.write_bytes(b'database')
            for name in ('seforim.db.lucene', 'seforim.db.lookup.lucene', 'catalog.pb', 'lexical.db', 'release_info.txt'):
                (root / name).touch()
            args = SimpleNamespace(work=root, output=root, repo=root, publish_tag='base-full-new',
                                   db_release_tag='base', db_repository='db', app_repository='app', pdf_release_tag='pdf')
            events = []
            commands = []
            uploaded = {}
            base = root / 'base.tar.zst'
            base.write_bytes(b'compressed-base')

            class RecordingPublisher(FakePublisher):
                def upload(self, repository, release, file, digest):
                    events.append(('upload', repository, file.name))
                    uploaded[(repository, file.name)] = file.read_bytes()
                    return {'browser_download_url': 'https://example.com/' + file.name}

                def publish(self, repository, release, latest):
                    events.append(('publish', repository))

            def run(command, **kwargs):
                commands.append(command)
                output = next(arg.split('=', 1)[1] for arg in command
                              if arg.startswith(('-PbundleOutput=', '-PsemanticBundleOutput=')))
                archive = Path(output)
                # Every variant exercises multipart uploads, including PDF and semantic supplements.
                for part in range(1, 4):
                    archive.with_name(archive.name + f'.part{part:02d}').write_bytes(b'archive')
                if ':packaging:packageSemanticBundle' in command:
                    (archive.parent / 'semantic-bundle.json').write_text('{"parts":3}', encoding='utf-8')

            response = SimpleNamespace(raise_for_status=lambda: None, json=lambda: {
                'tag_name': 'pdf', 'assets': [{'name': 'talmud_bavli_latest.tar.zst',
                                             'digest': 'sha256:' + '0' * 64, 'browser_download_url': 'https://example.com/pdf'}],
            })
            def download(url, path, digest):
                path.write_bytes(b'pdf')

            with patch('kaggle_publish_distributions.Publisher', RecordingPublisher), \
                    patch('kaggle_publish_distributions.requests.get', return_value=response), \
                    patch('kaggle_publish_distributions.extract_complete') as extract:
                build_distributions(args, database, root / 'model', root / 'index', {}, 'test-token',
                                    lambda path: '0' * 64, download, run, [base])
            extract.assert_not_called()
            self.assertEqual(1, len(commands))
            self.assertIn(':packaging:packageSemanticBundle', commands[0])
            self.assertTrue(all('-PzstdLevel=22' in command for command in commands))
            manifest = json.loads((root / 'distributions.json').read_text(encoding='utf-8'))
            self.assertEqual(7, len(manifest['assets']))
            self.assertEqual(b'compressed-base', uploaded[('db', 'seforim_bundle-database-only.tar.zst')])
            self.assertEqual(b'compressed-basepdf', uploaded[('db', 'seforim_bundle-no-vectors.tar.zst')])
            self.assertEqual(b'compressed-base' + b'archive' * 3, uploaded[('db', 'seforim_bundle-no-pdf.tar.zst')])
            self.assertEqual(b'compressed-basepdf' + b'archive' * 3, uploaded[('db', 'seforim_bundle.tar.zst')])
            self.assertEqual(b'pdf', uploaded[('db', 'talmud_bavli_latest.tar.zst')])
            self.assertEqual(b'archive' * 3, uploaded[('app', 'semantic-bundle.tar.zst')])
            self.assertTrue(base.is_file())
            self.assertEqual(1, json.loads(uploaded[('db', 'semantic-bundle.json')])['parts'])
            self.assertEqual(22, manifest['compressionLevel'])
            self.assertEqual(1, manifest['shardCount'])
            self.assertEqual([('publish', 'app'), ('publish', 'db')], events[-2:])
            self.assertTrue(all(event[0] == 'upload' for event in events[:-2]))


if __name__ == '__main__':
    unittest.main()
