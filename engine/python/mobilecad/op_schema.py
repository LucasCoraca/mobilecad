"""Op argument checks and metadata for mobilecad.bridge.

Kept free of FreeCAD imports: the host test suite runs it directly
(engine/python/tests/test_op_schema.py). The checks are intentionally
conservative -- they reject misspelled/unknown arguments and report missing
required ones with a readable message, but never coerce values.
"""
import inspect


class BadArguments(Exception):
    pass


def _params(fn):
    out = {}
    for name, p in inspect.signature(fn).parameters.items():
        if p.kind in (inspect.Parameter.POSITIONAL_OR_KEYWORD, inspect.Parameter.KEYWORD_ONLY):
            out[name] = p
    return out


def validate(fn, req):
    params = _params(fn)
    unknown = sorted(k for k in req if k not in params)
    if unknown:
        raise BadArguments("unknown argument(s) %s; accepted: %s" % (", ".join(unknown), ", ".join(sorted(params)) or "-"))
    missing = [n for n, p in params.items() if p.default is inspect.Parameter.empty and n not in req]
    if missing:
        raise BadArguments("missing required argument(s): %s" % ", ".join(missing))
    return dict(req)


def describe(fn):
    params = []
    for n, p in _params(fn).items():
        item = {"name": n, "required": p.default is inspect.Parameter.empty}
        if p.default is not inspect.Parameter.empty:
            item["default"] = p.default if isinstance(p.default, (str, int, float, bool, type(None))) else repr(p.default)
        ann = p.annotation
        if ann is not inspect.Parameter.empty and isinstance(ann, type) and ann.__module__ == "builtins":
            item["type"] = ann.__name__
        params.append(item)
    return {"name": fn.__name__, "params": params, "doc": (inspect.getdoc(fn) or "").splitlines()[0] if inspect.getdoc(fn) else ""}
