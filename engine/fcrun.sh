#!/bin/bash
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}   # the emulator, not a phone that may be plugged in
# Runs a Python script inside the app's embedded FreeCAD (debug build) and prints `result` / traceback.
# Usage: fcrun.sh script.py      (the script may set `result`; `App` and `doc` are predefined)
ADB=$HOME/Android/sdk/platform-tools/adb
B64=$(base64 -w0 "$1")
$ADB logcat -c
$ADB shell am start -n app.mobilecad/.MainActivity --es fcscript64 "$B64" > /dev/null
for i in $(seq 1 120); do out=$($ADB logcat -d -s fcscript:I | grep -v "^-" ); [ -n "$out" ] && break; sleep 1; done
$ADB logcat -d -s fcscript:I | sed 's/^.*fcscript: //' | grep -v "^-"
