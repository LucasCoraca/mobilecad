cmake_minimum_required(VERSION 3.22)
project(fcbridge CXX)
set(CMAKE_CXX_STANDARD 17)
# CPython for Android, built by engine/build_python.sh into $FCDEPS/<abi>/python/prefix (FCDEPS as in engine/env.sh)
if(DEFINED ENV{FCDEPS})
  set(FCDEPS $ENV{FCDEPS})
else()
  set(FCDEPS $ENV{HOME}/Android/fcdeps)
endif()
set(PY ${FCDEPS}/${ANDROID_ABI}/python/prefix)
# Without a native build tree (only the staged runtime): compile against python.org's CPython 3.14.4 Android package
# (same version and soname as ours; only its headers and libpython link stub are used, the APK ships our libpython),
# unpacked to ~/Android/pyprefix/<abi>/prefix
if(NOT EXISTS ${PY}/include/python3.14/Python.h AND EXISTS $ENV{HOME}/Android/pyprefix/${ANDROID_ABI}/prefix/include/python3.14/Python.h)
  set(PY $ENV{HOME}/Android/pyprefix/${ANDROID_ABI}/prefix)
endif()
add_library(fcbridge SHARED fcbridge.cpp)
target_include_directories(fcbridge PRIVATE ${PY}/include/python3.14)
target_link_libraries(fcbridge ${PY}/lib/libpython3.14.so log dl)
