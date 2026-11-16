#!/usr/bin/env python3
"""Merge FreeCAD's Simplified Chinese catalogs into MobileCAD's interface catalog.

    python3 tools/i18n_import_freecad.py /path/to/FreeCAD

FreeCAD draws the command labels, the task panels and the property labels the engine reports, so its own
Chinese wording is the right one for MobileCAD too: the user sees the same terms in both. This script reads
FreeCAD's Qt catalogs (``src/**/*_zh-CN.ts``) and rewrites everything below the
"FreeCAD vocabulary" marker of ``app/app/src/main/assets/i18n/zh-Hans.tsv``.

The lines above the marker are MobileCAD's own interface, written by hand; they are never touched and always
win over FreeCAD's wording. Run it again after updating the FreeCAD commit the app is built on.
"""
import argparse
import pathlib
import re
import xml.etree.ElementTree as ET

MARKER = "# ---------------------------------------------------------------- FreeCAD vocabulary"
CATALOG = pathlib.Path(__file__).resolve().parent.parent / "app/app/src/main/assets/i18n/zh-Hans.tsv"
HEADER = """# MobileCAD Simplified Chinese interface (简体中文)
#
# Line format: English<TAB>Chinese ('#' starts a comment). The English text is the key, so a string this file
# does not hold stays English and a partial catalog never breaks the interface.
#
# "FreeCAD vocabulary" below is derived from FreeCAD's own Simplified Chinese catalogs
# (src/**/translations/*_zh-CN.ts, LGPL-2.1-or-later, translated by the FreeCAD project's contributors), so
# commands, task panels and property labels read exactly as they do in FreeCAD. The entries above it are written
# for MobileCAD's own interface. Regenerate the FreeCAD part with tools/i18n_import_freecad.py.
"""


def freecad_entries(root: pathlib.Path) -> dict[str, str]:
    """English source -> Chinese translation, from every translated entry of FreeCAD's catalogs."""
    out: dict[str, str] = {}
    for ts in sorted(root.glob("src/**/*_zh-CN.ts")):
        try:
            tree = ET.parse(ts)
        except ET.ParseError as e:                      # a damaged catalog must not stop the import
            print(f"skipping {ts}: {e}")
            continue
        for context in tree.getroot().iter("context"):
            for message in context.iter("message"):
                source = (message.findtext("source") or "").strip()
                tr = message.find("translation")
                if not source or tr is None:
                    continue
                text = (tr.text or "").strip()
                if not text or tr.get("type") in ("unfinished", "vanished", "obsolete"):
                    continue
                if len(source) > 120 or "\n" in source or "\t" in source:
                    continue                            # paragraphs, not labels
                if ("<" in source and ">" in source) or "http://" in source or "https://" in source:
                    continue                            # markup and links read badly in a tooltip
                if not re.search(r"[A-Za-z]", source):
                    continue
                out.setdefault(source, text)
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("freecad", type=pathlib.Path, help="a FreeCAD source checkout")
    ap.add_argument("--catalog", type=pathlib.Path, default=CATALOG, help=f"catalog to update (default: {CATALOG})")
    args = ap.parse_args()

    entries = freecad_entries(args.freecad)
    if not entries:
        raise SystemExit(f"no *_zh-CN.ts catalogs found under {args.freecad}/src")

    text = args.catalog.read_text(encoding="utf-8")
    head = text.split(MARKER)[0].rstrip("\n")
    own = {line.split("\t", 1)[0] for line in head.splitlines() if line and not line.startswith("#")}
    rows = [f"{k}\t{v}" for k, v in sorted(entries.items()) if k not in own]
    args.catalog.write_text(head + "\n" + MARKER + "\n" + "\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows)} FreeCAD entries written to {args.catalog} ({len(own)} MobileCAD entries kept)")


if __name__ == "__main__":
    main()
