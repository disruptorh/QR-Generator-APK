cmake_minimum_required(VERSION 3.22.1)
project(qr_bridge LANGUAGES CXX)

# QR_CORE_DIR points at the shared core checkout (the core/qr-generator
# submodule). It is passed in from app/build.gradle.kts so the path has one owner
# instead of being spelled out twice here and there.
if(NOT DEFINED QR_CORE_DIR)
  message(FATAL_ERROR "QR_CORE_DIR is required: pass -DQR_CORE_DIR=<path to the qr core>")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(QR_CORE_SRC ${QR_CORE_DIR}/src)

# The same source list the desktop build compiles into its qr_core target, minus
# anything platform specific. The core is already free of windowing and I/O, so
# it needs no patching to run on Android.
add_library(qr_core STATIC
  ${QR_CORE_SRC}/qr/qr_spec.cpp
  ${QR_CORE_SRC}/qr/qr_encoder.cpp
  ${QR_CORE_SRC}/qr/qr_decoder.cpp
  ${QR_CORE_SRC}/render/raster.cpp
  ${QR_CORE_SRC}/render/png.cpp
  ${QR_CORE_SRC}/render/svg.cpp
  ${QR_CORE_SRC}/payload/builders.cpp
  ${QR_CORE_SRC}/app/app_state.cpp
)
target_include_directories(qr_core PUBLIC ${QR_CORE_SRC})
# The PNG writer uses zlib, which the NDK ships in its sysroot.
target_link_libraries(qr_core PUBLIC z)
target_compile_options(qr_core PRIVATE -Wall -Wextra -Wpedantic)

add_library(qr_codec STATIC inputs_codec.cpp)
target_include_directories(qr_codec PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(qr_codec PUBLIC qr_core)
target_compile_options(qr_codec PRIVATE -Wall -Wextra -Wpedantic)

add_library(qr_bridge SHARED qr_bridge.cpp)
target_link_libraries(qr_bridge PRIVATE qr_codec)
target_compile_options(qr_bridge PRIVATE -Wall -Wextra -Wpedantic -fvisibility=hidden
                                              -fvisibility-inlines-hidden)
