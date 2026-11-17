"""Bounded LRU cache for tessellation results.

Deliberately free of FreeCAD imports so it can be unit-tested on a host
(see engine/python/tests/test_meshcache.py). Keys are opaque tuples; the bridge
uses (document, object, tolerance, shape stamp).
"""
import threading
from collections import OrderedDict


class MeshCache:
    def __init__(self, max_entries=256, max_bytes=192 * 1024 * 1024):
        self.max_entries = max_entries
        self.max_bytes = max_bytes
        self._data = OrderedDict()
        self._bytes = 0
        self._lock = threading.Lock()
        self.hits = 0
        self.misses = 0

    def get(self, key):
        with self._lock:
            try:
                value = self._data.pop(key)
            except KeyError:
                self.misses += 1
                return None
            self._data[key] = value          # most recently used
            self.hits += 1
            return value

    def put(self, key, value):
        if not value:
            return
        size = len(value)
        if size > self.max_bytes:
            return
        with self._lock:
            old = self._data.pop(key, None)
            if old is not None:
                self._bytes -= len(old)
            self._data[key] = value
            self._bytes += size
            while self._data and (len(self._data) > self.max_entries or self._bytes > self.max_bytes):
                _, evicted = self._data.popitem(last=False)
                self._bytes -= len(evicted)

    def clear(self):
        with self._lock:
            self._data.clear()
            self._bytes = 0
            self.hits = 0
            self.misses = 0

    def stats(self):
        with self._lock:
            return {"entries": len(self._data), "bytes": self._bytes,
                    "hits": self.hits, "misses": self.misses,
                    "max_entries": self.max_entries, "max_bytes": self.max_bytes}
