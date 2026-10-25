#!/bin/bash
# Gmsh 4.13.1 (with the Open CASCADE kernel) as an Android executable for FEM meshing (FEM_MeshGmshFromShape,
# Mesh_RemeshGmsh).  Android only lets apps execute files from their native library directory, so the binary is
# shipped as jniLibs/<abi>/libgmsh.so and FreeCAD's gmsh path preference points there.  Usage: build_gmsh.sh <abi>
set -euo pipefail
ABI=${1:-x86_64}
. "$(dirname "$(readlink -f "$0")")/env.sh"
P=$D/$ABI; unpack gmsh/gmsh.tgz gmsh/gmsh-4.13.1-source; S=$SRC/gmsh/gmsh-4.13.1-source
B=$D/build-gmsh-$ABI; mkdir -p $B; cd $B; rm -f CMakeCache.txt
$CMK $S -G Ninja -DCMAKE_MAKE_PROGRAM=$NINJA -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI \
  -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DCMAKE_FIND_ROOT_PATH=$P \
  -DCMAKE_CXX_FLAGS="-D_LIBCPP_TYPEINFO_COMPARISON_IMPLEMENTATION=2 -Wno-everything" -DCMAKE_C_FLAGS="-Wno-everything" \
  -DCMAKE_EXE_LINKER_FLAGS="-L$P/lib" \
  -DENABLE_FLTK=OFF -DENABLE_OCC=ON -DOCC_INC=$P/include/opencascade -DENABLE_OCC_CAF=OFF -DENABLE_BLAS_LAPACK=OFF -DENABLE_PETSC=OFF \
  -DENABLE_SLEPC=OFF -DENABLE_MED=OFF -DENABLE_CGNS=OFF -DENABLE_OPENMP=OFF -DENABLE_MPI=OFF -DENABLE_CAIRO=OFF -DENABLE_OS_SPECIFIC_INSTALL=OFF \
  -DENABLE_BUILD_LIB=OFF -DENABLE_BUILD_SHARED=OFF -DENABLE_BUILD_DYNAMIC=OFF -DENABLE_TOUCHBAR=OFF -DENABLE_GRAPHICS=OFF -DENABLE_POPPLER=OFF \
  -DENABLE_EIGEN=ON -DEIGEN_INC=$P/include/eigen3 -DENABLE_MMG=OFF -DENABLE_ZIPPER=OFF -DENABLE_WRAP_PYTHON=OFF -DENABLE_PRIVATE_API=OFF \
  -DCMAKE_PREFIX_PATH=$P > configure.log 2>&1 || { tail -40 configure.log; exit 1; }
grep -E "OpenCASCADE|OCC" configure.log | head -5
$CMK --build . -j$J > build.log 2>&1 || { grep -E "error|FAILED" build.log | head -30; exit 1; }
mkdir -p $P/gmsh && cp gmsh $P/gmsh/gmsh
echo "GMSH DONE $ABI"
