#!/bin/bash
# CalculiX ccx 2.22 for Android (FEM's default solver).  The NDK has no Fortran compiler, so the 977 Fortran sources are
# compiled by LLVM flang 21 from Termux, run on the emulator (engine/tools: adb), for the target ABI; the C sources, SPOOLES
# and the link use the NDK.  ARPACK + OpenBLAS (LAPACK) come from Termux's Android packages, the Fortran runtime from
# Termux's flang package (libflang_rt.runtime.a).  Result: $P/ccx/ccx (+ libarpack.so.2, libopenblas.so).
# Usage: build_ccx.sh <abi>        (needs the emulator running and engine/build_ccx_termux.sh having fetched packages)
set -euo pipefail
ABI=${1:-x86_64}
D=$HOME/Android/fcdeps; P=$D/$ABI; S=$D/src/ccx; X=$D/src/termux
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
ADB=$HOME/Android/sdk/platform-tools/adb
T=$HOME/Android/sdk/ndk/29.0.14206865/toolchains/llvm/prebuilt/linux-x86_64
TRIPLE=$([ $ABI = arm64-v8a ] && echo aarch64-linux-android26 || echo x86_64-linux-android26)
TX=$([ $ABI = arm64-v8a ] && echo aarch64 || echo x86_64)
U=data/data/com.termux/files/usr
CC="$T/bin/clang --target=$TRIPLE -O2 -fPIC -w -fcommon -std=gnu89 -Wno-int-conversion -Wno-incompatible-pointer-types -Wno-implicit-function-declaration -Wno-implicit-int"
B=$D/build-ccx-$ABI; mkdir -p $B/spooles $B/c $B/f; cd $B

# ---- SPOOLES 2.2 (serial)
if [ ! -f spooles.a ]; then
  for f in $S/SPOOLES.2.2/*/src/*.c; do
    case $f in */MPI/*|*/MT/*|*/drivers/*) continue;; esac
    o=spooles/$(echo ${f#$S/SPOOLES.2.2/} | tr / _).o; [ -f $o ] || $CC -I$S/SPOOLES.2.2 -c $f -o $o
  done
  $T/bin/llvm-ar rcs spooles.a spooles/*.o
fi

# ---- ccx C sources
SRC=$S/CalculiX/ccx_2.22/src
CDEFS='-DARCH="Linux" -DSPOOLES -DARPACK -DMATRIXSTORAGE -DNETWORKOUT'
# sources as listed in Makefile.inc (gauss.f, xlocal.f ... are include files, not units)
CLIST=$(python3 -c "import re,os;print(' '.join(f for f in re.findall(r'(\S+\.c)\b', open('$SRC/Makefile.inc').read()) if os.path.exists('$SRC/'+f)))") 
FLIST=$(python3 -c "import re,os;print(' '.join(f for f in re.findall(r'(\S+\.f)\b', open('$SRC/Makefile.inc').read()) if os.path.exists('$SRC/'+f)))")
for f in $CLIST ccx_2.22.c; do o=c/$(basename ${f%.c}).o; [ -f $o ] || eval $CC $CDEFS -I$S/SPOOLES.2.2 -c $SRC/$f -o $o; done

# ---- ccx Fortran sources: flang on the emulator
if [ ! -f f/.done ]; then
  W=/data/local/tmp/ccxf; $ADB shell rm -rf $W; $ADB shell mkdir -p $W
  $ADB push $SRC/. $W/src >/dev/null
  # serial build: OpenMP-using files only need an (empty) omp_lib module / include
  printf '      module omp_lib\n      end module omp_lib\n' > /tmp/claude-1000/omp_lib.f90; : > /tmp/claude-1000/omp_lib.h
  $ADB push /tmp/claude-1000/omp_lib.f90 /tmp/claude-1000/omp_lib.h $W/src/ >/dev/null
  echo "$FLIST" > /tmp/claude-1000/flist.txt; $ADB push /tmp/claude-1000/flist.txt $W/ >/dev/null
  $ADB shell "cd $W/src && export LD_LIBRARY_PATH=/data/local/tmp/tx/usr/lib && mkdir -p ../o && /data/local/tmp/tx/usr/bin/flang -c --target=$TRIPLE omp_lib.f90 -o ../o_omp.o && for f in \$(cat ../flist.txt); do /data/local/tmp/tx/usr/bin/flang -c -O2 -fPIC -I. --target=$TRIPLE \$f -o ../o/\${f%.f}.o 2>>../errors.txt || echo FAIL \$f >> ../errors.txt; done"
  $ADB shell "cat $W/errors.txt" | grep -E "FAIL|error" | head -20 || true
  $ADB pull $W/o/. f/ >/dev/null
  touch f/.done
fi

# ---- link
RTL=$X/flang_$TX/$U/lib/libflang_rt.runtime.a
AR=$X/arpack_$TX/$U/lib; OB=$X/openblas_$TX/$U/lib
$T/bin/clang++ --target=$TRIPLE -O2 -o ccx c/*.o $(ls f/*.o) spooles.a -L$AR -l:libarpack.so.2 -L$OB -lopenblas $RTL -lm -static-libstdc++ \
  -Wl,--allow-shlib-undefined 2>&1 | tail -20
mkdir -p $P/ccx && cp ccx $P/ccx/ccx && cp -L $AR/libarpack.so.2 $OB/libopenblas.so $P/ccx/
echo "CCX DONE $ABI"
