#!/bin/bash
# IfcOpenShell 0.8.4 (IfcParse + IfcGeom on Open CASCADE + the SWIG Python wrapper) for Android: FreeCAD's BIM / NativeIFC
# (IFC import / export, IFC projects, IFC explorer, IFC diff ...) imports `ifcopenshell`.  Usage: build_ifcopenshell.sh <abi>
# CGAL, COLLADA, HDF5 and ifcXML backends are left out; IfcConvert / examples / geometry server are not built.
set -euo pipefail
ABI=${1:-x86_64}
D=$HOME/Android/fcdeps; P=$D/$ABI; S=$D/src/ifc/ios; PY=$P/python/prefix
export ANDROID_HOME=$HOME/Android/sdk
export SWIG_LIB=$D/swig41/swig/data/share/swig/4.1.1     # SWIG 4.1: 4.2+ dropped the PyInt_* compatibility IfcOpenShell 0.8 uses
NDK=$ANDROID_HOME/ndk/29.0.14206865; CM=$ANDROID_HOME/cmake/3.22.1/bin
CMK=$D/hostpy/bin/cmake        # FindPython in CMake 3.22 predates Python 3.14
TC="-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$P/ifcopenshell -DCMAKE_FIND_ROOT_PATH=$P;$PY -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH -G Ninja -DCMAKE_MAKE_PROGRAM=$CM/ninja"
ARCH=$([ $ABI = arm64-v8a ] && echo aarch64 || echo x86_64); SOABI=cpython-314-$ARCH-linux-android
B=$D/build-ifc-$ABI; mkdir -p $B; cd $B
rm -f $B/CMakeCache.txt
$CMK $S/cmake $TC -DCMAKE_CXX_FLAGS="-D_LIBCPP_TYPEINFO_COMPARISON_IMPLEMENTATION=2 -Wno-everything" \
  -DBoost_DIR=$P/lib/cmake/Boost-1.87.0 -DBoost_USE_STATIC_LIBS=OFF -DEigen3_DIR=$P/share/eigen3/cmake -DEIGEN_DIR=$P/include/eigen3 \
  -DOCC_INCLUDE_DIR=$P/include/opencascade -DOCC_LIBRARY_DIR=$P/lib \
  -DWITH_CGAL=OFF -DCOLLADA_SUPPORT=OFF -DHDF5_SUPPORT=OFF -DIFCXML_SUPPORT=OFF -DGLTF_SUPPORT=ON -DJSON_INCLUDE_DIR=$D/src/ifc/json -DUSD_SUPPORT=OFF \
  -DBUILD_CONVERT=OFF -DBUILD_EXAMPLES=OFF -DBUILD_GEOMSERVER=OFF -DBUILD_IFCPYTHON=ON -DBUILD_SHARED_LIBS=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
  -DSWIG_EXECUTABLE=$D/swig41/swig/data/bin/swig -DSWIG_DIR=$D/swig41/swig/data/share/swig/4.1.1 -DPYTHON_INCLUDE_DIR=$PY/include/python3.14 -DPYTHON_LIBRARY=$PY/lib/libpython3.14.so \
  -DPython_INCLUDE_DIR=$PY/include/python3.14 -DPython_LIBRARY=$PY/lib/libpython3.14.so -DPython_EXECUTABLE=$D/hostpy/bin/python \
  -DPython_SOABI=$SOABI -DCMAKE_SHARED_LINKER_FLAGS="-L$P/lib $PY/lib/libpython3.14.so" -DCMAKE_MODULE_LINKER_FLAGS="-L$P/lib $PY/lib/libpython3.14.so" \
  -DPYTHON_MODULE_INSTALL_DIR=$P/ifcopenshell/python > configure.log 2>&1 || { tail -40 configure.log; exit 1; }
$CMK --build . -j10 > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -40; exit 1; }
$CMK --install . > install.log 2>&1 || { tail -20 install.log; exit 1; }
echo "IFC DONE $ABI"
