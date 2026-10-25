#!/bin/bash
# CalculiX ccx 2.22 for Android (FEM's default solver), built from upstream source on a Debian host (no emulator).
# Fortran (ccx's 977 Fortran units, ARPACK, LLVM flang-rt's iso_fortran_env module) is cross-compiled by Debian's flang 21
# (--target=<abi>-linux-android26); C/C++ and the link use NDK 29's clang (also LLVM 21). Everything is linked statically
# into one executable: ccx objects + SPOOLES 2.2 + ARPACK (arpack-ng) + OpenBLAS (with its C translation of LAPACK, so no
# Fortran there) + LLVM flang-rt 21.1.8 (the Fortran runtime, which must match the flang version) + static libc++.
# Result: $P/ccx/ccx (engine/package_runtime.py ships it as jniLibs/<abi>/libccx.so).
#
# Sources ($FCDEPS/src, fetched + verified by engine/fetch_sources.sh from engine/sources.lock):
#   ccx/ccx.tar.bz2 ccx/spooles.tgz llvm/llvm-project-21.1.8.src.tar.xz openblas/OpenBLAS-0.3.34.tar.gz
#   arpack/arpack-ng-3.9.1.tar.gz        + patches engine/patches/ccx-android.patch, flang-rt-android.patch
# Debian trixie packages: gcc make cmake ninja-build perl python3 patch bzip2 xz-utils
#   and from trixie-backports: flang-21  (apt-get install -t trixie-backports flang-21; 21.1.8)
# Settings: engine/env.sh (FCDEPS, ANDROID_HOME / ANDROID_NDK, JOBS); FC (default /usr/lib/llvm-21/bin/flang)
# Usage: build_ccx.sh <abi>        (abi = x86_64 | arm64-v8a)
set -euo pipefail
ABI=${1:-x86_64}
. "$(dirname "$(readlink -f "$0")")/env.sh"          # D (FCDEPS), SRC ($D/src), NDK, TB, J, CMK, NINJA
TRIPLE=$(abi_triple $ABI)26; P=$D/$ABI
FC=${FC:-/usr/lib/llvm-21/bin/flang}   # flang-21's driver under the name flang-rt's cmake keys on (flang | flang-new)
LLVMV=21.1.8; OBV=0.3.34; ARV=3.9.1
FFL="--target=$TRIPLE -O2 -fPIC"
CC="$TB/clang --target=$TRIPLE -O2 -fPIC"
B=$D/build-ccx-$ABI; mkdir -p $B; cd $B
# parallel compile helpers (xargs -P): fco <src.f> <obj> <incdir>, cco <src.c> <obj> <flags...>
fco() { [ -f "$2" ] || "$FC" $FFL -I"$3" "${@:4}" -c "$1" -o "$2"; }
cco() { local s=$1 o=$2; shift 2; [ -f "$o" ] || $CC -w -fcommon -std=gnu89 -Wno-int-conversion -Wno-incompatible-pointer-types \
  -Wno-implicit-function-declaration -Wno-implicit-int "$@" -c "$s" -o "$o"; }
export -f fco cco; export FC FFL CC
"$FC" --version | grep -q "flang version 21\." || { echo "need flang 21 (flang-21 from trixie-backports)" >&2; exit 1; }

# ---- LLVM flang-rt (static Fortran runtime libflang_rt.runtime.a) for the target
RT=$B/libflang_rt.runtime.a
if [ ! -f $RT ]; then
  L=llvm-project-$LLVMV.src
  [ -f $L/.unpacked ] || { tar xf $SRC/llvm/$L.tar.xz $L/flang-rt $L/flang/include $L/flang/lib/Decimal $L/flang/module \
                              $L/flang/cmake $L/runtimes $L/cmake $L/llvm/cmake $L/llvm/utils/llvm-lit $L/third-party
                            patch -p1 -d $L < $ENGINE/patches/flang-rt-android.patch; touch $L/.unpacked; }
  # (CMake cannot try-link a Fortran program before this runtime exists: COMPILER_WORKS)
  $CMK -G Ninja -DCMAKE_MAKE_PROGRAM=$NINJA -S $L/runtimes -B flang-rt -DLLVM_ENABLE_RUNTIMES=flang-rt \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI \
    -DANDROID_PLATFORM=26 -DCMAKE_Fortran_COMPILER="$FC" -DCMAKE_Fortran_FLAGS="--target=$TRIPLE" \
    -DCMAKE_Fortran_COMPILER_WORKS=ON -DFLANG_RT_ENABLE_STATIC=ON -DFLANG_RT_ENABLE_SHARED=OFF \
    -DFLANG_RT_INCLUDE_TESTS=OFF -DLLVM_INCLUDE_TESTS=OFF > flang-rt.cmake.log
  $NINJA -C flang-rt -j$J > flang-rt.build.log
  cp "$(find flang-rt -name libflang_rt.runtime.a | head -1)" $RT
fi

# ---- OpenBLAS (BLAS + C LAPACK, serial, static)
OB=$B/openblas-inst/lib/libopenblas.a
if [ ! -f $OB ]; then
  [ -d OpenBLAS-$OBV ] || tar xzf $SRC/openblas/OpenBLAS-$OBV.tar.gz
  if [ $ABI = arm64-v8a ]; then OBT="TARGET=ARMV8"; else OBT="TARGET=PRESCOTT DYNAMIC_ARCH=1"; fi  # emulator ABI: any x86_64 CPU
  make -C OpenBLAS-$OBV -j$J $OBT HOSTCC=gcc CC="$TB/clang --target=$TRIPLE" AR=$TB/llvm-ar RANLIB=$TB/llvm-ranlib \
    NOFORTRAN=1 C_LAPACK=1 USE_THREAD=0 USE_LOCKING=1 NO_SHARED=1 BUILD_TESTING=0 libs netlib > openblas.build.log 2>&1
  make -C OpenBLAS-$OBV $OBT HOSTCC=gcc CC="$TB/clang --target=$TRIPLE" NOFORTRAN=1 C_LAPACK=1 USE_THREAD=0 NO_SHARED=1 \
    PREFIX=$B/openblas-inst install > openblas.install.log 2>&1
fi

# ---- ARPACK (arpack-ng, serial, 32-bit integers, static): the plain Fortran units of SRC + UTIL
AR=$B/libarpack.a
if [ ! -f $AR ]; then
  [ -d arpack-ng-$ARV ] || tar xzf $SRC/arpack/arpack-ng-$ARV.tar.gz
  mkdir -p arpack
  for f in arpack-ng-$ARV/SRC/*.f arpack-ng-$ARV/UTIL/*.f; do b=${f##*/}; echo "$f arpack/${b%.f}.o arpack-ng-$ARV/SRC"; done | \
    xargs -P$J -L1 bash -c 'fco "$@"' _
  $TB/llvm-ar rcs $AR arpack/*.o
fi

# ---- sources: ccx (patched) + SPOOLES
if [ ! -f src.done ]; then
  mkdir -p SPOOLES.2.2; tar xzf $SRC/ccx/spooles.tgz -C SPOOLES.2.2; tar xjf $SRC/ccx/ccx.tar.bz2
  patch -p0 -d CalculiX/ccx_2.22/src < $ENGINE/patches/ccx-android.patch
  touch src.done
fi
CS=$B/CalculiX/ccx_2.22/src; SP=$B/SPOOLES.2.2

# ---- SPOOLES 2.2 (serial)
if [ ! -f spooles.a ]; then
  mkdir -p spooles
  for f in $SP/*/src/*.c; do
    case $f in */MPI/*|*/MT/*|*/drivers/*) continue;; esac
    r=${f#$SP/}; echo "$f spooles/${r//\//_}.o -I$SP"
  done | xargs -P$J -L1 bash -c 'cco "$@"' _
  $TB/llvm-ar rcs spooles.a spooles/*.o
fi

# ---- ccx C sources
# sources as listed in Makefile.inc (gauss.f, xlocal.f ... are include files, not units)
CLIST=$(python3 -I -c "import re,os;print(' '.join(f for f in re.findall(r'(\S+\.c)\b', open('$CS/Makefile.inc').read()) if os.path.exists('$CS/'+f)))")
FLIST=$(python3 -I -c "import re,os;print(' '.join(f for f in re.findall(r'(\S+\.f)\b', open('$CS/Makefile.inc').read()) if os.path.exists('$CS/'+f)))")
mkdir -p c f
# (ccx's Makefile passes -DARCH="Linux", which make's shell turns into the bare token: CalculiX.h tests '#if ARCH == Linux')
for f in $CLIST ccx_2.22.c; do echo "$CS/$f c/${f%.c}.o -I$SP -DSPOOLES -DARPACK -DMATRIXSTORAGE -DNETWORKOUT -DARCH=Linux"; done | \
  xargs -P$J -L1 bash -c 'cco "$@"' _

# ---- ccx Fortran sources (serial build: OpenMP-using units only need an empty omp_lib module)
if [ ! -f f/.done ]; then
  printf '      module omp_lib\n      end module omp_lib\n' > f/omp_lib.f90; : > f/omp_lib.h
  (cd f && "$FC" $FFL -c omp_lib.f90 -o omp_lib.o)     # omp_lib.mod lands in f/, found via -I f below
  for f in $FLIST; do echo "$CS/$f f/${f%.f}.o $CS -I$B/f"; done | xargs -P$J -L1 bash -c 'fco "$@"' _
  touch f/.done
fi

# ---- link (all static except Android's libc/libm/libdl)
$TB/clang++ --target=$TRIPLE -O2 -o ccx c/*.o f/*.o spooles.a $AR $OB $RT -lm -static-libstdc++
mkdir -p $P/ccx && cp ccx $P/ccx/ccx
echo "CCX DONE $ABI"
