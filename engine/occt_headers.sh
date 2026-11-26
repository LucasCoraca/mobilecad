#!/bin/bash
# OCCT's headers for the app's native mesher (app/app/src/main/cpp/mcmesh.cpp): unpacked from the source archive the
# runtime was built from (serve/source/occt-*.tar.gz, see engine/sources.lock) into ~/Android/occt-inc.
set -e
SRC=$(ls "$(dirname "$0")"/../serve/source/occt-*.tar.gz | head -1)
TMP=$(mktemp -d); OUT=$HOME/Android/occt-inc
tar xzf "$SRC" -C "$TMP"
mkdir -p "$OUT"
find "$TMP"/*/src -maxdepth 2 \( -name '*.hxx' -o -name '*.lxx' -o -name '*.gxx' -o -name '*.pxx' -o -name '*.h' \) -exec cp -t "$OUT" {} +
rm -r -- "$TMP"
echo "OCCT headers in $OUT"
