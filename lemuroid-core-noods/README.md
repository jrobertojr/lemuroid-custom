# NooDS Libretro for Lemuroid

Vendored from https://github.com/jonian/libretro-noods, branch `libretro`,
commit `9a2665306927ede64f4b5c109df79f4015607dbc`.
Upstream emulator: https://github.com/Hydr8gon/NooDS.
License: GPL-3.0-or-later; see LICENSE and the retained source headers.
The Libretro game database is derived from https://github.com/kelpsyberry/dust
as documented in upstream/libretro/gamedb.cpp. The complete pinned upstream
revision and its attribution remain available at the source URL above.

This module builds and bundles the core from source for armeabi-v7a, arm64-v8a,
x86 and x86_64. It is available in free/play and bundled/dynamic app variants;
no download from the LemuroidCores repository is required for NooDS.
For development on a single device, pass `-PnoodsAbis=armeabi-v7a` to Gradle.
Do not use that restriction for a universal release.

Local adapter changes are documented below; preserve them when updating:

- Expose the cartridge's raw save memory through RETRO_MEMORY_SAVE_RAM so the
  existing Lemuroid save/sync pipeline can import and export battery saves.
- Initialize known save sizes from the port's existing game database before
  frontend SRAM import; unknown games retain the emulator's autodetection.
- Add a screen-layout variable for the existing single-screen/peek controls.
- Build with CMake/NDK and optimization in every build type; no fast-math.
- Bound-check save-state headers/output and reserve 32 MiB for high-resolution
  renderer state instead of the upstream 8 MiB estimate.
- Clear the core pointer on unload so a subsequent load cannot delete it twice.

Save states use upstream's version 3 format and are isolated by core name in
Lemuroid. They are not interchangeable with DeSmuME or melonDS save states.

The reproducible Android headless harness is `tools/nds_benchmark.cpp`. Compile
it with the NDK clang++ for the target ABI, `-std=c++17 -O2 -fPIE -pie
-static-libstdc++ -ldl`. Arguments: core library, ROM file, system directory,
isolated save directory, core name (`noods` or `desmume`), profile (`quality`,
`balanced`, `performance`), optional measured frame count (default 600).
It warms up for 720 emulated frames and reports unpaced core throughput, audio
activity, save-RAM exposure and state round-trip checks. This is not an Android
presentation or audio-quality benchmark. See `docs/NOODS-INTEGRATION.md` in the
repository root for the measured results and the decision to retain the default.
