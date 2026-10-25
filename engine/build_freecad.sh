#!/bin/bash
# Cross-compiles FreeCAD's App layer (no Gui) for Android.  Usage: build_freecad.sh [abi] [configure|build|install]
# Source: $FREECAD_SRC (default ~/Android/freecad-src, a checkout with engine/patches/freecad-android.patch applied); if
# that doesn't exist, the commit in engine/sources.lock is unpacked into $FCDEPS/src/freecad and patched.
set -euo pipefail
ABI=${1:-x86_64}; WHAT=${2:-all}
. "$(dirname "$(readlink -f "$0")")/env.sh"
P=$D/$ABI; B=$D/build-freecad-$ABI
FCSRC=$(freecad_src)
PY=$P/python/prefix; export PATH=$D/hostpy/bin:$PATH
# Android's libc has pthread / rt built in; FreeCAD's CMake still links -lpthread -lrt: empty archives satisfy it
mkdir -p $P/stub; for l in pthread rt; do [ -f $P/stub/lib$l.a ] || printf '!<arch>\n' > $P/stub/lib$l.a; done
SWIG=$(command -v swig || echo $D/hostpy/bin/swig)
PYBIND=$($D/hostpy/bin/python -c "import pybind11;print(pybind11.get_cmake_dir())")
mkdir -p $B; cd $B
if [ "$WHAT" = all ] || [ "$WHAT" = configure ]; then
$CMK -G Ninja -DCMAKE_MAKE_PROGRAM=$NINJA $FCSRC \
  -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE=Release -DCMAKE_CXX_FLAGS=-D_LIBCPP_TYPEINFO_COMPARISON_IMPLEMENTATION=2 -DCMAKE_INSTALL_PREFIX=$P/freecad -DCMAKE_SHARED_LINKER_FLAGS=-L$P/stub -DCMAKE_MODULE_LINKER_FLAGS=-L$P/stub -DCMAKE_EXE_LINKER_FLAGS=-L$P/stub \
  -DCMAKE_FIND_ROOT_PATH="$P;$PY" -DCMAKE_PREFIX_PATH="$P" -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH \
  -DQT_HOST_PATH=$D/qt-host -DQt6_DIR=$P/lib/cmake/Qt6 -DFREECAD_QT_VERSION=6 \
  -DICU_ROOT=$P -DICU_UC_LIBRARY_RELEASE=$P/lib/libicuuc.so -DICU_I18N_LIBRARY_RELEASE=$P/lib/libicui18n.so -DICU_DATA_LIBRARY_RELEASE=$P/lib/libicudata.so -DICU_INCLUDE_DIR=$P/include -DSWIG_EXECUTABLE=$SWIG -DPython3_EXECUTABLE=$D/hostpy/bin/python -DPython3_INCLUDE_DIR=$PY/include/python3.14 -DPython3_LIBRARY=$PY/lib/libpython3.14.so \
  -DOCC_INCLUDE_DIR=$P/include/opencascade -DOCC_LIBRARY_DIR=$P/lib -DOpenCASCADE_DIR=$P/lib/cmake/opencascade \
  -DXercesC_DIR=$P/lib/cmake/XercesC -DEigen3_DIR=$P/share/eigen3/cmake -DBoost_DIR=$P/lib/cmake/Boost-1.87.0 \
  -DCMAKE_PROJECT_INCLUDE=$ENGINE/cmake/android_stubs.cmake -DBUILD_GUI=OFF -DENABLE_DEVELOPER_TESTS=OFF -DFREECAD_USE_SMESH=ON -DBUILD_SMESH=ON -DVTK_DIR=$P/lib/cmake/vtk-9.5 -DINSTALL_TO_SITEPACKAGES=OFF -Dpybind11_DIR=$PYBIND -DFREECAD_USE_PCH=OFF -DFREECAD_RCC_COMPRESSION_ALGO=zlib -DFREECAD_RCC_COMPRESSION_LEVEL=9 -DFREECAD_USE_FREETYPE=ON -DFREECAD_VERSION_INCLUDE_GIT=OFF -DBUILD_DYNAMIC_LINK_PYTHON=ON \
  -DBUILD_FEM=ON -DBUILD_CAM=ON -DBUILD_ROBOT=ON -DBUILD_ADDONMGR=OFF -DBUILD_WEB=OFF -DBUILD_HELP=OFF -DBUILD_TUX=OFF \
  -DBUILD_START=OFF -DBUILD_TEST=OFF -DBUILD_FLAT_MESH=ON -DBUILD_JTREADER=OFF -DBUILD_PLOT=OFF -DBUILD_DESIGNER_PLUGIN=OFF \
  -DBUILD_PART=ON -DBUILD_PART_DESIGN=ON -DBUILD_SKETCHER=ON -DBUILD_SPREADSHEET=ON -DBUILD_MATERIAL=ON -DBUILD_MEASURE=ON \
  -DBUILD_IMPORT=ON -DBUILD_MESH=ON -DBUILD_MESH_PART=ON -DBUILD_SURFACE=ON -DBUILD_POINTS=ON -DBUILD_ASSEMBLY=ON \
  -DBUILD_TECHDRAW=ON -DBUILD_DRAFT=ON -DBUILD_BIM=ON -DBUILD_OPENSCAD=ON -DBUILD_SHOW=ON -DBUILD_INSPECTION=ON -DBUILD_REVERSEENGINEERING=ON \
  > configure.log 2>&1 || { tail -n 40 configure.log; exit 1; }
tail -n 3 configure.log
fi
if [ "$WHAT" = all ] || [ "$WHAT" = build ]; then $CMK --build . -j$J > build.log 2>&1 || { grep -E "error:|FAILED" build.log | head -n 60; exit 1; }; fi
if [ "$WHAT" = all ] || [ "$WHAT" = install ]; then $CMK --install . > install.log 2>&1; echo installed; fi
