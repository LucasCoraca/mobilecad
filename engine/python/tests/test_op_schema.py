import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from mobilecad import op_schema  # noqa: E402


class OpSchemaTest(unittest.TestCase):
    def test_accepts_known_arguments(self):
        def op(name, count=1):
            return name
        self.assertEqual(op_schema.validate(op, {"name": "Pad", "count": 2}), {"name": "Pad", "count": 2})

    def test_rejects_unknown_argument(self):
        def op(name):
            return name
        with self.assertRaises(op_schema.BadArguments) as ctx:
            op_schema.validate(op, {"nmae": "Pad"})
        self.assertIn("nmae", str(ctx.exception))
        self.assertIn("name", str(ctx.exception))

    def test_reports_missing_required(self):
        def op(name, length):
            return name
        with self.assertRaises(op_schema.BadArguments) as ctx:
            op_schema.validate(op, {"name": "Pad"})
        self.assertIn("length", str(ctx.exception))

    def test_describe_lists_params(self):
        def op(name, length=10.0, visible=True):
            pass
        info = op_schema.describe(op)
        self.assertEqual(info["name"], "op")
        self.assertEqual([p["name"] for p in info["params"]], ["name", "length", "visible"])
        self.assertTrue(info["params"][0]["required"])
        self.assertEqual(info["params"][1]["default"], 10.0)


if __name__ == "__main__":
    unittest.main()
