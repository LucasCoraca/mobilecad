import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from mobilecad.meshcache import MeshCache  # noqa: E402


class MeshCacheTest(unittest.TestCase):
    def test_hit_and_miss(self):
        c = MeshCache(max_entries=2)
        c.put(("doc", "Pad", 0.05, 1), b"abc")
        self.assertEqual(c.get(("doc", "Pad", 0.05, 1)), b"abc")
        self.assertIsNone(c.get(("doc", "Pad", 0.05, 2)))
        self.assertEqual(c.stats()["hits"], 1)
        self.assertEqual(c.stats()["misses"], 1)

    def test_evicts_least_recently_used(self):
        c = MeshCache(max_entries=2, max_bytes=1024)
        c.put("a", b"1")
        c.put("b", b"2")
        c.get("a")            # a becomes most recent
        c.put("c", b"3")      # b evicted
        self.assertIsNotNone(c.get("a"))
        self.assertIsNone(c.get("b"))
        self.assertIsNotNone(c.get("c"))

    def test_byte_budget(self):
        c = MeshCache(max_entries=10, max_bytes=10)
        c.put("a", b"12345678")
        c.put("b", b"12345678")   # evicts a
        self.assertIsNone(c.get("a"))
        self.assertIsNotNone(c.get("b"))
        self.assertLessEqual(c.stats()["bytes"], 10)

    def test_ignores_oversized_value(self):
        c = MeshCache(max_entries=10, max_bytes=4)
        c.put("big", b"12345")
        self.assertIsNone(c.get("big"))


if __name__ == "__main__":
    unittest.main()
