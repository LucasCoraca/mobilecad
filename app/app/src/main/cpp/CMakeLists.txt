cmake_minimum_required(VERSION 3.22)
project(fcbridge CXX)
set(CMAKE_CXX_STANDARD 17)
# CPython for Android (python.org release package), staged by engine/build_deps.sh
set(PY $ENV{HOME}/Android/fcdeps/${ANDROID_ABI}/python/prefix)
add_library(fcbridge SHARED fcbridge.cpp)
target_include_directories(fcbridge PRIVATE ${PY}/include/python3.14)
target_link_libraries(fcbridge ${PY}/lib/libpython3.14.so log dl)
