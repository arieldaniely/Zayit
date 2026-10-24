"""Small streaming primitives shared by Kaggle and the CPU-only release action."""


class PartsReader:
    def __init__(self, paths):
        self.paths = iter(paths)
        self.current = next(self.paths).open("rb")

    def read(self, size=-1):
        if size == 0:
            return b""
        if size < 0:
            return b"".join(iter(lambda: self.read(1 << 20), b""))
        while self.current is not None:
            block = self.current.read(size)
            if block:
                return block
            self.current.close()
            path = next(self.paths, None)
            if path is None:
                self.current = None
                return b""
            self.current = path.open("rb")
        return b""

    def close(self):
        if self.current is not None:
            self.current.close()
            self.current = None
