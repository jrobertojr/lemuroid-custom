cmake_minimum_required(VERSION 3.22)
project(lemuroid_desmume2015 LANGUAGES C CXX)

# Preserve the upstream source list, with its architecture-specific JIT sections.
file(READ "${CMAKE_CURRENT_SOURCE_DIR}/upstream/Makefile.common" SOURCE_LIST)
string(REGEX MATCHALL "\\$\\(CORE_DIR\\)/[A-Za-z0-9_/.-]+\\.(cpp|c)" SOURCE_PATHS "${SOURCE_LIST}")
foreach(SOURCE_PATH IN LISTS SOURCE_PATHS)
    string(REPLACE "$(CORE_DIR)/" "upstream/src/" SOURCE_PATH "${SOURCE_PATH}")
    if(SOURCE_PATH MATCHES "utils/arm_arm/" OR SOURCE_PATH MATCHES "utils/AsmJit/" OR
       SOURCE_PATH MATCHES "/arm_jit.cpp$" OR SOURCE_PATH MATCHES "/debug.cpp$" OR
       SOURCE_PATH MATCHES "/Disassembler.cpp$")
        continue()
    endif()
    list(APPEND CORE_SOURCES "${SOURCE_PATH}")
endforeach()

add_library(desmume2015_libretro_android SHARED ${CORE_SOURCES})
target_include_directories(desmume2015_libretro_android PRIVATE
    upstream/src upstream/src/libretro upstream/src/libretro-common/include)
target_compile_features(desmume2015_libretro_android PRIVATE cxx_std_11)
target_compile_definitions(desmume2015_libretro_android PRIVATE
    GIT_VERSION="422b688-lemuroid1" NDEBUG)
target_compile_options(desmume2015_libretro_android PRIVATE
    -O3 -fvisibility=hidden -ffunction-sections -fdata-sections
    $<$<COMPILE_LANGUAGE:CXX>:-fpermissive>)

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    target_sources(desmume2015_libretro_android PRIVATE
        upstream/src/utils/arm_arm/arm_gen.cpp upstream/src/utils/arm_arm/arm_jit.cpp)
    target_compile_definitions(desmume2015_libretro_android PRIVATE HAVE_JIT __RETRO_ARM__ ARM USE_POSIX_MEMALIGN)
    target_compile_options(desmume2015_libretro_android PRIVATE -marm)
endif()

target_link_options(desmume2015_libretro_android PRIVATE "--driver-mode=g++"
    "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/upstream/src/libretro/link.T"
    "-Wl,--gc-sections")
target_link_libraries(desmume2015_libretro_android PRIVATE log z)
