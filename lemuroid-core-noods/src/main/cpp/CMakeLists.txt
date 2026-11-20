cmake_minimum_required(VERSION 3.22)
project(lemuroid_noods LANGUAGES CXX)

file(GLOB NOODS_SOURCES CONFIGURE_DEPENDS
    upstream/*.cpp upstream/common/*.cpp upstream/libretro/*.cpp)
add_library(noods_libretro_android SHARED ${NOODS_SOURCES})
target_compile_features(noods_libretro_android PRIVATE cxx_std_17)
target_compile_definitions(noods_libretro_android PRIVATE
    __LIBRETRO__ VERSION="9a26653-lemuroid1" LOG_LEVEL=0 NDEBUG)
# Native emulation must be optimized even when the Android UI is a debug build.
target_compile_options(noods_libretro_android PRIVATE -O3 -fvisibility=hidden -ffunction-sections -fdata-sections)
target_link_options(noods_libretro_android PRIVATE
    "--driver-mode=g++"
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/link.T" "-Wl,--gc-sections")
