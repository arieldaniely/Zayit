"""Extract one tar.zst archive or its ordered parts; requires zstandard."""

import argparse
import contextlib
import shutil
import tarfile
from pathlib import Path

import zstandard


class PartsReader:
    def __init__(self, paths):
        self.paths = iter(paths)
        self.current = next(self.paths).open('rb')

    def read(self, size=-1):
        while True:
            chunk = self.current.read(size)
            if chunk:
                return chunk
            self.current.close()
            path = next(self.paths, None)
            if path is None:
                return b''
            self.current = path.open('rb')

    def close(self):
        self.current.close()


def extract(first, destination):
    import re

    first = first.resolve()
    match = re.fullmatch(r'(.+\.tar\.zst)\.part(\d+)', first.name)
    paths = [first]
    if match:
        paths = sorted(first.parent.glob(match[1] + '.part*'), key=lambda p: int(p.name.rsplit('part', 1)[1]))
        if [int(p.name.rsplit('part', 1)[1]) for p in paths] != list(range(1, len(paths) + 1)):
            raise ValueError('Missing archive parts')
    destination = destination.resolve()
    destination.mkdir(parents=True, exist_ok=True)
    with contextlib.closing(PartsReader(paths)) as source:
        with zstandard.ZstdDecompressor().stream_reader(source) as reader:
            with tarfile.open(fileobj=reader, mode='r|', ignore_zeros=True) as archive:
                for member in archive:
                    name = member.name.removeprefix('./')
                    if (destination / 'seforim.db').is_file() and name.split('/', 1)[0] in ('model', 'index'):
                        name = 'seforim.db.semantic/' + name
                    target = (destination / name).resolve()
                    if not target.is_relative_to(destination):
                        raise ValueError('Archive path escapes destination')
                    if member.isdir():
                        target.mkdir(parents=True, exist_ok=True)
                    elif member.isfile():
                        target.parent.mkdir(parents=True, exist_ok=True)
                        with archive.extractfile(member) as content, target.open('xb') as output:
                            shutil.copyfileobj(content, output, 1 << 20)
                    else:
                        raise ValueError('Unsupported archive entry')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    extract(args.archive, args.destination)
