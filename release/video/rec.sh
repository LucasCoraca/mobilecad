#!/bin/bash
# Records one launch-video scene from the emulator: rec.sh NAME SECONDS WxH "input args" "sleep N" ... (taps run during the recording)
# rec NAME SECONDS SIZE -- actions run while recording (each action: "cmd args")
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5558}; case "$ANDROID_SERIAL" in emulator-*) ;; *) echo "refusing: not an emulator"; exit 1;; esac; A=$HOME/Android/sdk/platform-tools/adb
N=$1; T=$2; SZ=$3; shift 3
$A shell rm -f /sdcard/rec.mp4
$A shell screenrecord --size $SZ --bit-rate 20000000 --time-limit $T /sdcard/rec.mp4 &
P=$!; sleep 0.8
for a in "$@"; do if [[ $a == sleep* ]]; then $a; else $A shell input $a; fi; done
wait $P; sleep 1; $A pull /sdcard/rec.mp4 $(dirname "$0")/clips/$N.mp4 >/dev/null
ffprobe -v error -count_frames -select_streams v -show_entries stream=nb_read_frames,duration -of csv=p=0 $(dirname "$0")/clips/$N.mp4 | sed "s/^/$N: /"
