#!/bin/bash
# Fetches the Termux packages build_ccx.sh uses: flang (+ libLLVM / libclang-cpp and their deps, x86_64, run on the
# emulator as the Fortran compiler; libflang_rt.runtime.a per ABI), arpack-ng and libopenblas per ABI.
set -euo pipefail
X=$HOME/Android/fcdeps/src/termux; mkdir -p $X; cd $X
B=https://packages.termux.dev/apt/termux-main
get() {  # arch package dir
  [ -f pk_$1 ] || curl -sL --max-time 300 -o pk_$1 $B/dists/stable/main/binary-$1/Packages
  F=$(awk -v P="Package: $2" '$0==P,/^$/' pk_$1 | grep '^Filename' | cut -d' ' -f2)
  [ -d $3 ] || { curl -sL --max-time 900 -o $3.deb $B/$F; mkdir -p $3; (cd $3 && ar x ../$3.deb && tar xf data.tar.*); }
}
for a in x86_64 aarch64; do get $a flang flang_$a; get $a arpack-ng arpack_$a; get $a libopenblas openblas_$a; done
for p in libllvm clang libffi libxml2 zlib zstd libiconv liblzma libicu; do get x86_64 $p host_$p; done
# the compiler on the emulator: flang + the libraries it needs (Termux prefix layout under /data/local/tmp/tx)
U=data/data/com.termux/files/usr; S=$X/stage; rm -rf $S; mkdir -p $S/usr/bin $S/usr/lib $S/usr/include
cp flang_x86_64/$U/bin/flang $S/usr/bin/; cp -r flang_x86_64/$U/include/flang $S/usr/include/
for p in host_*; do cp -P $p/$U/lib/*.so* $S/usr/lib/ 2>/dev/null || true; done
cp $HOME/Android/sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so $S/usr/lib/
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
ADB=$HOME/Android/sdk/platform-tools/adb
$ADB shell rm -rf /data/local/tmp/tx; $ADB push $S /data/local/tmp/tx > /dev/null; echo "termux flang staged"
