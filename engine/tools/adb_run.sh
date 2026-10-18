#!/bin/bash
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}   # the emulator, not a phone that may be plugged in
# CMAKE_CROSSCOMPILING_EMULATOR for Android cross builds: runs the binary on the emulator through adb (try_run checks,
# HDF5's H5detect / H5make_libsettings).  Plain-name arguments that the program writes as files are pulled back into
# the host working directory.  Usage: adb_run.sh <binary> [args...]
ADB=$HOME/Android/sdk/platform-tools/adb
BIN=$1; shift
W=/data/local/tmp/xrun/$$
$ADB shell "mkdir -p $W" >/dev/null
$ADB push "$BIN" $W/prog >/dev/null 2>&1
LIBS=$HOME/Android/sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib
case "$(file -b "$BIN")" in *aarch64*) T=aarch64-linux-android;; *) T=x86_64-linux-android;; esac
$ADB push $LIBS/$T/libc++_shared.so $W/ >/dev/null 2>&1
# inputs a generator reads from its working directory (HDF5: libhdf5.settings)
for f in ./*.settings; do [ -f "$f" ] && $ADB push "$f" $W/ >/dev/null 2>&1; done
ARGS=""; for a in "$@"; do ARGS="$ARGS '$a'"; done
$ADB shell "cd $W && chmod 755 prog && LD_LIBRARY_PATH=$W ./prog $ARGS; echo __RC=\$?" > /tmp/adb_run.$$ 2>&1
RC=$(grep -o '__RC=[0-9]*' /tmp/adb_run.$$ | tail -1 | cut -d= -f2)
grep -v '__RC=' /tmp/adb_run.$$ | tr -d '\r'
for a in "$@"; do case "$a" in -*|*/*) ;; *) $ADB pull $W/$a ./$a >/dev/null 2>&1 ;; esac; done
rm -f /tmp/adb_run.$$; $ADB shell "rm -rf $W" >/dev/null
exit ${RC:-1}
