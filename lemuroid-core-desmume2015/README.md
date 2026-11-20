# DeSmuME 2015 for Lemuroid

Vendored from https://github.com/libretro/desmume2015 at commit
`422b688009ccb9f2917c086a8fe79c8cecb030ae`.
The upstream COPYING and individual source notices are retained; the ARM JIT
sources carry GPL-3.0-or-later notices. See also the repository's GPLv3 license.

This module builds from source and bundles the library in every app variant.
Supported build ABIs: armeabi-v7a, arm64-v8a, x86 and x86_64. ARMv7 uses the
upstream ARM-to-ARM JIT and ARM instruction mode; the other ABIs currently use
the interpreter. No Android core download is needed. Development builds may
restrict compilation with `-Pdesmume2015Abis=armeabi-v7a`.

Local changes:

- CMake/NDK build using the upstream Makefile.common source list, with -O3 in
  debug builds as well as release. No fast-math or altered CPU clock.
- Explicit signed register casts in the old ARM JIT for modern Clang.
- Rename core-option keys from `desmume_` to `desmume2015_`, so manual preferences
  do not leak between the two cores. Preserve the existing layouts/controllers.
- Clear serialization buffer padding for reproducible compressed states.
- Import a newer valid raw `.srm` at load using the upstream backup importer;
  otherwise use the existing native `.dsv` cartridge-save path. Battery-save
  format is shared with legacy DeSmuME; frontend SRAM remains unexposed, as in
  upstream. Save states are isolated under core name `desmume2015`.

Profiles retain native 256x192 resolution. Quality skips no frames; Balanced
uses the existing DeSmuME tradeoff of 1; Performance uses 2 with edgemarking and
line/TXT hacks disabled. Manual per-core options retain precedence. General
profile selection, shaders/HD and configurable bezels continue through the
existing application paths.

The shared headless comparison harness is
`../lemuroid-core-noods/tools/nds_benchmark.cpp`; use core name `desmume2015`.
The `reference-no-skip` profile measures full frame output independently of the
three application profiles. Results and the default-core decision are recorded
in `../docs/DESMUME2015-INTEGRATION.md`.
