#!/bin/bash
# Cross-compiles FreeCAD's App-layer dependencies for Android.  Usage: build_deps.sh <step> [abi]
#   steps: qt-host | qt | boost | xerces | fmt | yaml | eigen | icu | freetype | harfbuzz | vtk | occt   abi: x86_64 (default) | arm64-v8a
# Sources: engine/sources.lock (engine/fetch_sources.sh); settings: engine/env.sh
set -euo pipefail
STEP=$1; ABI=${2:-x86_64}
. "$(dirname "$(readlink -f "$0")")/env.sh"
PREFIX=$D/$ABI; mkdir -p $PREFIX
TC="-DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=$ABI -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_shared"
COMMON="-G Ninja -DCMAKE_MAKE_PROGRAM=$NINJA -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$PREFIX -DCMAKE_FIND_ROOT_PATH=$PREFIX -DCMAKE_PREFIX_PATH=$PREFIX"
B=$D/build-$STEP-$ABI; rm -rf $B; mkdir -p $B; cd $B
case $STEP in
qt-host)
  unpack qtbase.tar.xz qtbase; B=$D/build-qt-host; rm -rf $B; mkdir -p $B; cd $B
  $CMK -G Ninja -DCMAKE_MAKE_PROGRAM=$NINJA $SRC/qtbase -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=$D/qt-host \
    -DQT_BUILD_EXAMPLES=OFF -DQT_BUILD_TESTS=OFF -DFEATURE_gui=ON -DFEATURE_widgets=ON -DFEATURE_opengl=OFF -DINPUT_opengl=no -DFEATURE_xcb=OFF -DFEATURE_xlib=OFF -DFEATURE_wayland=OFF -DFEATURE_vulkan=OFF -DFEATURE_sql=OFF -DFEATURE_openssl=OFF \
    -DFEATURE_icu=OFF -DFEATURE_glib=OFF -DFEATURE_dbus=OFF -DFEATURE_zstd=ON -DFEATURE_testlib=ON -DFEATURE_fontconfig=OFF -DFEATURE_system_freetype=OFF -DFEATURE_system_harfbuzz=OFF -DFEATURE_system_png=OFF -DFEATURE_system_jpeg=OFF
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
qt)
  unpack qtbase.tar.xz qtbase
  $CMK $COMMON $TC $SRC/qtbase -DQT_HOST_PATH=$D/qt-host -DANDROID_SDK_ROOT=$ANDROID_HOME -DANDROID_NDK_ROOT=$NDK \
    -DQT_BUILD_EXAMPLES=OFF -DQT_BUILD_TESTS=OFF -DFEATURE_gui=ON -DFEATURE_widgets=ON -DFEATURE_opengles2=ON -DFEATURE_vulkan=OFF -DFEATURE_sql=OFF -DFEATURE_openssl=OFF \
    -DFEATURE_icu=OFF -DFEATURE_glib=OFF -DFEATURE_dbus=OFF -DFEATURE_testlib=OFF -DBUILD_SHARED_LIBS=ON -DFEATURE_system_freetype=OFF -DFEATURE_system_harfbuzz=OFF
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
boost)
  unpack boost.tar.gz boost
  $CMK $COMMON $TC $SRC/boost -DBUILD_SHARED_LIBS=ON -DBOOST_ENABLE_PYTHON=OFF \
    -DBOOST_INCLUDE_LIBRARIES="program_options;regex;thread;date_time;filesystem;system;graph;signals2;geometry;algorithm;tokenizer;bimap;iostreams;dynamic_bitset;uuid;lexical_cast;polygon;math;multi_index;spirit;interprocess;ptr_container;format;range;variant;optional;any;assign;logic;scope_exit;core;smart_ptr;function;bind;type_traits;container_hash;unordered;property_tree;tuple;exception;serialization;utility;numeric_conversion;foreach" \
    -DBOOST_IOSTREAMS_ENABLE_BZIP2=OFF -DBOOST_IOSTREAMS_ENABLE_LZMA=OFF -DBOOST_IOSTREAMS_ENABLE_ZSTD=OFF -DBOOST_LOCALE_ENABLE_ICU=OFF
  $CMK --build . -j$J && $CMK --install . || exit 1
  # every header-only library too (FreeCAD includes stacktrace, asio ... that the list above doesn't install)
  for d in $SRC/boost/libs/*/include/boost $SRC/boost/libs/*/*/include/boost; do [ -d $d ] && cp -rn $d $PREFIX/include/; done ;;
xerces)
  unpack xerces.tar.gz xerces
  $CMK $COMMON $TC $SRC/xerces -DBUILD_SHARED_LIBS=ON -Dtranscoder=iconv -Dnetwork=OFF -Dmessage-loader=inmemory -Dthreads=ON
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
fmt)
  unpack fmt.tar.gz fmt
  $CMK $COMMON $TC $SRC/fmt -DBUILD_SHARED_LIBS=ON -DFMT_TEST=OFF -DFMT_DOC=OFF
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
yaml)
  unpack yaml.tar.gz yaml
  $CMK $COMMON $TC $SRC/yaml -DBUILD_SHARED_LIBS=ON -DYAML_CPP_BUILD_TESTS=OFF -DYAML_CPP_BUILD_TOOLS=OFF
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
eigen)
  unpack eigen.tar.gz eigen
  $CMK $COMMON $TC $SRC/eigen -DBUILD_TESTING=OFF -DEIGEN_BUILD_DOC=OFF
  $CMK --install . ;;
icu)
  # ICU: host build (tools + data), then Android cross build. Locale data trimmed to root/en.
  unpack icu.tgz icu
  echo '{"localeFilter":{"filterType":"language","includelist":["en","root"]}}' > $D/icu-filter.json
  export ICU_DATA_FILTER_FILE=$D/icu-filter.json
  if [ ! -d $D/icu-host ]; then mkdir -p $D/icu-host && cd $D/icu-host && $SRC/icu/source/runConfigureICU Linux --disable-tests --disable-samples > /dev/null && make -j$J > /dev/null; cd $B; fi
  TRIPLE=$(abi_triple $ABI)
  CC=$TB/${TRIPLE}26-clang CXX=$TB/${TRIPLE}26-clang++ AR=$TB/llvm-ar RANLIB=$TB/llvm-ranlib \
    $SRC/icu/source/configure --host=$TRIPLE --with-cross-build=$D/icu-host --prefix=$PREFIX --enable-shared --disable-static \
    --disable-tests --disable-samples --disable-extras --disable-tools --with-data-packaging=library > /dev/null
  make -j$J > /dev/null && make install > /dev/null || exit 1 ;;
freetype)
  unpack freetype.tar.xz freetype
  $CMK $COMMON $TC $SRC/freetype -DBUILD_SHARED_LIBS=ON -DFT_DISABLE_BZIP2=ON -DFT_DISABLE_PNG=ON -DFT_DISABLE_HARFBUZZ=ON -DFT_DISABLE_BROTLI=ON -DFT_REQUIRE_ZLIB=ON
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
harfbuzz)
  unpack harfbuzz.tar.xz harfbuzz
  $CMK $COMMON $TC $SRC/harfbuzz -DBUILD_SHARED_LIBS=ON -DHB_HAVE_FREETYPE=ON -DHB_BUILD_SUBSET=OFF -DHB_HAVE_GLIB=OFF -DHB_HAVE_ICU=OFF
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
vtk)
  # VTK subset required by FreeCAD's SMESH (FEM / MeshPart / BIM / OpenSCAD); no rendering, no Python wrapping
  unpack vtk.tar.gz vtk
  MODS=""; for m in CommonCore CommonDataModel FiltersVerdict IOXML FiltersCore FiltersGeneral IOLegacy FiltersExtraction FiltersSources FiltersGeometry; do MODS="$MODS -DVTK_MODULE_ENABLE_VTK_$m=YES"; done
  $CMK $COMMON $TC $SRC/vtk -DBUILD_SHARED_LIBS=ON -DVTK_BUILD_ALL_MODULES=OFF -DVTK_ENABLE_WRAPPING=OFF -DVTK_WRAP_PYTHON=OFF \
    -DVTK_GROUP_ENABLE_Imaging=DONT_WANT -DVTK_GROUP_ENABLE_Rendering=DONT_WANT -DVTK_GROUP_ENABLE_StandAlone=DONT_WANT -DVTK_GROUP_ENABLE_Views=DONT_WANT \
    -DVTK_GROUP_ENABLE_Web=DONT_WANT -DVTK_GROUP_ENABLE_Qt=NO -DVTK_GROUP_ENABLE_MPI=NO -DVTK_ENABLE_REMOTE_MODULES=OFF -DVTK_USE_MPI=OFF \
    -DVTK_BUILD_TESTING=OFF -DVTK_BUILD_EXAMPLES=OFF -DVTK_SMP_IMPLEMENTATION_TYPE=Sequential -DVTK_ANDROID_BUILD=OFF $MODS
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
occt)
  OCC=${OCCT_SRC:-$HOME/Android/occt-src}; [ -d $OCC ] || { unpack occt-7.8.1.tar.gz occt; OCC=$SRC/occt; }
  $CMK $COMMON $TC $OCC -DBUILD_LIBRARY_TYPE=Shared -DINSTALL_DIR=$PREFIX \
    -DBUILD_MODULE_Draw=OFF -DBUILD_MODULE_DETools=OFF -DBUILD_DOC_Overview=OFF -DBUILD_Inspector=OFF \
    -DBUILD_MODULE_ApplicationFramework=ON -DBUILD_MODULE_DataExchange=ON -DBUILD_MODULE_Visualization=ON \
    -DUSE_FREETYPE=OFF -DUSE_TBB=OFF -DUSE_VTK=OFF -DUSE_FREEIMAGE=OFF -DUSE_RAPIDJSON=OFF -DUSE_OPENGL=OFF -DUSE_GLES2=OFF -DUSE_DRACO=OFF
  $CMK --build . -j$J && $CMK --install . || exit 1 ;;
esac
cd $D && rm -rf $B
echo "DONE $STEP $ABI"
