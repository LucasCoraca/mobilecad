# Desktop (Linux) build of MobileCAD XR, for testing against Monado without a headset (see sim/).
cmake_minimum_required(VERSION 3.16)
project(mobilecad_xr_desktop CXX)
set(CMAKE_CXX_STANDARD 17)
find_package(OpenXR REQUIRED)
find_package(OpenGL REQUIRED)
find_package(X11 REQUIRED)
add_executable(mobilecad-xr-desktop main.cpp ../core/scene.cpp ../core/renderer.cpp ../core/interaction.cpp ../core/hands.cpp ../core/gltf_model.cpp ../core/xr_session.cpp)
target_link_libraries(mobilecad-xr-desktop OpenXR::openxr_loader OpenGL::GL ${X11_LIBRARIES})
