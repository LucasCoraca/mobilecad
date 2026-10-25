#!/bin/bash
# HDF5 1.10 + MED-fichier 4.1 for Android (needed by FreeCAD's bundled SMESH -> FEM).  Usage: build_med.sh <abi>
# HDF5's configure-time try_run checks are preloaded (engine/cmake/hdf5-android-cache.cmake) and its build-time
# generators (H5detect, H5make_libsettings) are replaced by their recorded Android output (engine/tools/hdf5_pregen_run.sh).
set -euo pipefail
ABI=${1:-x86_64}
. "$(dirname "$(readlink -f "$0")")/env.sh"
P=$D/$ABI
unpack hdf5/hdf5-1.10.11.tar.gz hdf5/hdf5-1.10.11; unpack med/med-fichier_4.1.0+repack.orig.tar.gz med/med-fichier-4.1.0+repack
TC="-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$P -DCMAKE_FIND_ROOT_PATH=$P -G Ninja -DCMAKE_MAKE_PROGRAM=$NINJA"

B=$D/build-hdf5-$ABI; rm -rf $B; mkdir -p $B; cd $B
export HDF5_PREGEN_ABI=$ABI
$CMK -C $ENGINE/cmake/hdf5-android-cache.cmake $SRC/hdf5/hdf5-1.10.11 $TC -DCMAKE_CROSSCOMPILING_EMULATOR=$ENGINE/tools/hdf5_pregen_run.sh \
  -DBUILD_SHARED_LIBS=ON -DBUILD_STATIC_LIBS=OFF -DONLY_SHARED_LIBS=ON -DBUILD_TESTING=OFF -DHDF5_BUILD_TOOLS=OFF -DHDF5_BUILD_EXAMPLES=OFF \
  -DHDF5_BUILD_CPP_LIB=OFF -DHDF5_BUILD_FORTRAN=OFF -DHDF5_BUILD_JAVA=OFF -DHDF5_BUILD_HL_LIB=ON -DHDF5_ENABLE_Z_LIB_SUPPORT=OFF \
  -DHDF5_ENABLE_SZIP_SUPPORT=OFF -DHDF5_ENABLE_THREADSAFE=OFF -DHDF5_INSTALL_CMAKE_DIR=share/cmake/hdf5 > configure.log 2>&1 || { tail -30 configure.log; exit 1; }
$CMK --build . -j$J > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -30; exit 1; }
$CMK --install . > install.log 2>&1

B=$D/build-med-$ABI; rm -rf $B; mkdir -p $B; cd $B
$CMK $SRC/med/med-fichier-4.1.0+repack $TC -DHDF5_ROOT_DIR=$P -DCMAKE_Fortran_COMPILER= -DMEDFILE_BUILD_TESTS=OFF -DMEDFILE_INSTALL_DOC=OFF \
  -DMEDFILE_BUILD_PYTHON=OFF -DMEDFILE_BUILD_SHARED_LIBS=ON -DMEDFILE_BUILD_STATIC_LIBS=OFF > configure.log 2>&1 || { tail -30 configure.log; exit 1; }
$CMK --build . -j$J > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -30; exit 1; }
$CMK --install . > install.log 2>&1
echo "MED DONE $ABI"
