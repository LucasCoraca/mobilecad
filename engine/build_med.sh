#!/bin/bash
# HDF5 1.10 + MED-fichier 4.1 for Android (needed by FreeCAD's bundled SMESH -> FEM).  Usage: build_med.sh <abi>
# HDF5's configure-time try_run checks and its build-time generators (H5detect, H5make_libsettings) run on the
# emulator through engine/tools/adb_run.sh (CMAKE_CROSSCOMPILING_EMULATOR).
set -euo pipefail
ABI=${1:-x86_64}
D=$HOME/Android/fcdeps; P=$D/$ABI; S=$D/src
export ANDROID_HOME=$HOME/Android/sdk
NDK=$ANDROID_HOME/ndk/29.0.14206865; CM=$ANDROID_HOME/cmake/3.22.1/bin
TC="-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$P -DCMAKE_FIND_ROOT_PATH=$P -G Ninja -DCMAKE_MAKE_PROGRAM=$CM/ninja"
EMU=/home/lucas/Documents/freecadmobile/engine/tools/adb_run.sh

B=$D/build-hdf5-$ABI; rm -rf $B; mkdir -p $B; cd $B
$CM/cmake $S/hdf5/hdf5-1.10.11 $TC -DCMAKE_CROSSCOMPILING_EMULATOR=$EMU \
  -DBUILD_SHARED_LIBS=ON -DBUILD_STATIC_LIBS=OFF -DONLY_SHARED_LIBS=ON -DBUILD_TESTING=OFF -DHDF5_BUILD_TOOLS=OFF -DHDF5_BUILD_EXAMPLES=OFF \
  -DHDF5_BUILD_CPP_LIB=OFF -DHDF5_BUILD_FORTRAN=OFF -DHDF5_BUILD_JAVA=OFF -DHDF5_BUILD_HL_LIB=ON -DHDF5_ENABLE_Z_LIB_SUPPORT=OFF \
  -DHDF5_ENABLE_SZIP_SUPPORT=OFF -DHDF5_ENABLE_THREADSAFE=OFF -DHDF5_INSTALL_CMAKE_DIR=share/cmake/hdf5 > configure.log 2>&1 || { tail -30 configure.log; exit 1; }
$CM/cmake --build . -j24 > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -30; exit 1; }
$CM/cmake --install . > install.log 2>&1

B=$D/build-med-$ABI; rm -rf $B; mkdir -p $B; cd $B
$CM/cmake $S/med/med-fichier-4.1.0+repack $TC -DHDF5_ROOT_DIR=$P -DCMAKE_Fortran_COMPILER= -DMEDFILE_BUILD_TESTS=OFF -DMEDFILE_INSTALL_DOC=OFF \
  -DMEDFILE_BUILD_PYTHON=OFF -DMEDFILE_BUILD_SHARED_LIBS=ON -DMEDFILE_BUILD_STATIC_LIBS=OFF > configure.log 2>&1 || { tail -30 configure.log; exit 1; }
$CM/cmake --build . -j24 > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -30; exit 1; }
$CM/cmake --install . > install.log 2>&1
echo "MED DONE $ABI"
