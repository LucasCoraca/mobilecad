#!/bin/bash
# IfcOpenShell 0.8.4 (IfcParse + IfcGeom on Open CASCADE + the SWIG Python wrapper) for Android: FreeCAD's BIM / NativeIFC
# (IFC import / export, IFC projects, IFC explorer, IFC diff ...) imports `ifcopenshell`.  Usage: build_ifcopenshell.sh <abi>
# CGAL, COLLADA, HDF5 and ifcXML backends are left out; IfcConvert / examples / geometry server are not built.
set -euo pipefail
ABI=${1:-x86_64}
. "$(dirname "$(readlink -f "$0")")/env.sh"
P=$D/$ABI; S=$SRC/ifc/ios; PY=$P/python/prefix
# the locked commit + its svgfill submodule + nlohmann/json, patched (a developer checkout at $S is used as it is)
if [ ! -d $S ]; then
  unpack ifc/$(cd $SRC/ifc && ls ifcopenshell-*.tar.gz | head -1) ifc/ios.unpack
  rmdir $SRC/ifc/ios.unpack/src/svgfill 2>/dev/null || true; unpack ifc/$(cd $SRC/ifc && ls svgfill-*.tar.gz | head -1) ifc/ios.unpack/src/svgfill
  patch -d $SRC/ifc/ios.unpack -p1 < $ENGINE/patches/ifcopenshell-android.patch && mv $SRC/ifc/ios.unpack $S
fi
[ -f $SRC/ifc/json/nlohmann/json.hpp ] || { mkdir -p $SRC/ifc/json/nlohmann; cp $SRC/ifc/json-3.11.3.hpp $SRC/ifc/json/nlohmann/json.hpp; }
# SWIG 4.1 (engine/build_swig.sh): 4.2+ dropped the PyInt_* compatibility IfcOpenShell 0.8 uses
SWIG41=$D/swig41; [ -x $SWIG41/bin/swig ] || SWIG41=$D/swig41/swig/data          # (older developer trees: the pip package)
export SWIG_LIB=$SWIG41/share/swig/4.1.1
TC="-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$P/ifcopenshell -DCMAKE_FIND_ROOT_PATH=$P;$PY -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH -G Ninja -DCMAKE_MAKE_PROGRAM=$NINJA"
ARCH=$([ $ABI = arm64-v8a ] && echo aarch64 || echo x86_64); SOABI=cpython-314-$ARCH-linux-android
B=$D/build-ifc-$ABI; mkdir -p $B; cd $B
rm -f $B/CMakeCache.txt
$CMK $S/cmake $TC -DCMAKE_CXX_FLAGS="-D_LIBCPP_TYPEINFO_COMPARISON_IMPLEMENTATION=2 -Wno-everything" \
  -DBoost_DIR=$P/lib/cmake/Boost-1.87.0 -DBoost_USE_STATIC_LIBS=OFF -DEigen3_DIR=$P/share/eigen3/cmake -DEIGEN_DIR=$P/include/eigen3 \
  -DOCC_INCLUDE_DIR=$P/include/opencascade -DOCC_LIBRARY_DIR=$P/lib \
  -DWITH_CGAL=OFF -DCOLLADA_SUPPORT=OFF -DHDF5_SUPPORT=OFF -DIFCXML_SUPPORT=OFF -DGLTF_SUPPORT=ON -DJSON_INCLUDE_DIR=$SRC/ifc/json -DUSD_SUPPORT=OFF \
  -DBUILD_CONVERT=OFF -DBUILD_EXAMPLES=OFF -DBUILD_GEOMSERVER=OFF -DBUILD_IFCPYTHON=ON -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DSWIG_EXECUTABLE=$SWIG41/bin/swig -DSWIG_DIR=$SWIG_LIB -DPYTHON_INCLUDE_DIR=$PY/include/python3.14 -DPYTHON_LIBRARY=$PY/lib/libpython3.14.so \
  -DPython_INCLUDE_DIR=$PY/include/python3.14 -DPython_LIBRARY=$PY/lib/libpython3.14.so -DPython_EXECUTABLE=$D/hostpy/bin/python \
  -DPython_SOABI=$SOABI -DCMAKE_SHARED_LINKER_FLAGS="-L$P/lib $PY/lib/libpython3.14.so" -DCMAKE_MODULE_LINKER_FLAGS="-L$P/lib $PY/lib/libpython3.14.so" \
  -DPYTHON_MODULE_INSTALL_DIR=$P/ifcopenshell/python > configure.log 2>&1 || { tail -40 configure.log; exit 1; }
$CMK --build . -j$J > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -40; exit 1; }
$CMK --install . > install.log 2>&1 || { tail -20 install.log; exit 1; }
echo "IFC DONE $ABI"
