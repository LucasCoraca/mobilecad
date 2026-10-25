#!/bin/bash
# Downloads every source archive listed in engine/sources.lock into $FCDEPS/src and checks its SHA-256.
# Lock format, one file per line: <file name in $FCDEPS/src> <sha256> <url>   ('#' starts a comment)
# Usage: fetch_sources.sh [name-pattern]      (files already present with the right hash are kept)
set -euo pipefail
D=${FCDEPS:-$HOME/Android/fcdeps}; S=$D/src; mkdir -p $S
LOCK=$(dirname "$(readlink -f "$0")")/sources.lock
grep -vE '^\s*(#|$)' "$LOCK" | while read -r f sum url; do
  [ -n "${1:-}" ] && [[ $f != $1 ]] && continue
  mkdir -p "$(dirname "$S/$f")"
  if [ -f "$S/$f" ] && echo "$sum  $S/$f" | sha256sum -c --status; then continue; fi
  echo "fetch $f"
  curl -fsSL --retry 5 --retry-delay 10 -o "$S/$f.part" "$url"
  echo "$sum  $S/$f.part" | sha256sum -c --status || { echo "SHA-256 mismatch: $f ($url)" >&2; exit 1; }
  mv "$S/$f.part" "$S/$f"
done
