#!/bin/bash
# Website screenshot: grab the emulator screen, crop the Android status bar and bottom nav/dock, save serve/screens/<name>.jpg
#   ANDROID_SERIAL=emulator-5556 release/shot.sh name [top_px bottom_px]
set -e
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5556}; case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "refusing: not an emulator"; exit 1;; esac
A=$HOME/Android/sdk/platform-tools/adb; R=$(cd "$(dirname "$0")/.." && pwd); T=$(mktemp --suffix=.png)
$A exec-out screencap -p > $T
PY=${PILPY:-python3}
$PY - "$T" "$R/serve/screens/$1.jpg" "${2:-auto}" "${3:-auto}" <<'PYEOF'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB'); w, h = im.size
top = int(sys.argv[3]) if sys.argv[3] != 'auto' else round(h * 0.03)            # status bar
bot = int(sys.argv[4]) if sys.argv[4] != 'auto' else round(h * (0.076 if w > h else 0.03))  # dock / gesture bar
im = im.crop((0, top, w, h - bot))
if max(im.size) > 1600: im.thumbnail((1600, 1600))
im.save(sys.argv[2], quality=85, optimize=True, progressive=True); print(sys.argv[2], im.size)
PYEOF
rm -f $T
