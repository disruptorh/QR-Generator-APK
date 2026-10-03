cmake_minimum_required(VERSION 3.20)
project(qr_codec_tests LANGUAGES CXX)

# Host build of the wire-format layer. The Android build compiles the same
# sources against the NDK; running them here proves the format and the core
# integration without a device.
#
#   cmake -S app/src/main/cpp/tests -B build-codec-tests -DQR_CORE_DIR=../../../core/qr-generator
#   cmake --build build-codec-tests && ctest --test-dir build-codec-tests --output-on-failure

if(NOT DEFINED QR_CORE_DIR)
  message(FATAL_ERROR "QR_CORE_DIR is required: pass -DQR_CORE_DIR=<path to the qr core>")
endif()

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(ZLIB REQUIRED)

set(QR_CORE_SRC ${QR_CORE_DIR}/src)

add_executable(codec_tests
  codec_tests.cpp
  ../inputs_codec.cpp
  ${QR_CORE_SRC}/qr/qr_spec.cpp
  ${QR_CORE_SRC}/qr/qr_encoder.cpp
  ${QR_CORE_SRC}/qr/qr_decoder.cpp
  ${QR_CORE_SRC}/render/raster.cpp
  ${QR_CORE_SRC}/render/png.cpp
  ${QR_CORE_SRC}/render/svg.cpp
  ${QR_CORE_SRC}/payload/builders.cpp
  ${QR_CORE_SRC}/app/app_state.cpp
)
target_include_directories(codec_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/.. ${QR_CORE_SRC})
target_link_libraries(codec_tests PRIVATE ZLIB::ZLIB)
target_compile_options(codec_tests PRIVATE -Wall -Wextra -Wpedantic)

enable_testing()
add_test(NAME codec_tests COMMAND codec_tests)
