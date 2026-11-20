# Alternative Libretro cores

Official Android Libretro buildbot snapshot retrieved on 2026-11-19. This library packages 20 additional cores for `armeabi-v7a`, `arm64-v8a`, `x86` and `x86_64`; 80 ELF files, 390,492,344 bytes before APK compression. Existing first-choice cores and saved selections remain unchanged. Both Free and Play builds receive these packaged libraries; existing bundled-library discovery avoids nonexistent download URLs or Play feature modules.

| Systems | Added choices | Intended tradeoff |
| --- | --- | --- |
| Atari 2600 | Stella 2014 | Faster older implementation, reduced accuracy |
| NES | QuickNES, Nestopia UE, Mesen | Speed versus accuracy/compatibility |
| SNES | Snes9x 2005 Plus, Snes9x 2010, Mesen-S | Lighter implementations plus independent accuracy-focused option |
| Master System | PicoDrive, Gearsystem | Speed and another accurate implementation |
| Mega Drive, Sega CD | PicoDrive | Low-power ARM alternative |
| Game Gear | Gearsystem | Independent accurate implementation |
| Game Boy / Color | SameBoy | Accuracy; higher CPU cost is possible |
| GBA | gpSP, VBA Next | Speed-oriented alternatives; gpSP uses dynarec |
| N64 | ParaLLEl N64 | Alternate Rice renderer/dynarec; game dependent |
| PlayStation | SwanStation | Accuracy; conservative software rendering at native resolution |
| Arcade, both categories | FBA 2012, MAME 2000, MAME 2003; existing FBNeo/2003 Plus also selectable | Different hardware coverage and ROMsets |
| Lynx | Beetle Lynx | Independent implementation; requires lynxboot.img |
| PC Engine | Beetle PCE | More accurate than PCE Fast, potentially slower |
| Neo Geo Pocket / Color | RACE | Lightweight implementation |

Descriptions indicate emulator design goals, not measured FPS guarantees on this TV. The existing three performance profiles and configurable bezel remain authoritative. NDS already has five implementations including DSperate and receives no extra untested variant here.

Arcade selection is always manual. FBA 2012 requires its 0.2.97.29 set; MAME 2000 requires 0.37b5; MAME 2003 requires 0.78. FBNeo and MAME 2003-Plus each require their own matching sets. A ZIP filename alone cannot establish compatibility. No archive conversion, silent switching or ROM download is performed. Save states remain isolated by core; do not assume states or emulator-specific saves can migrate between implementations.

SwanStation controller choices use its actual Libretro device IDs 1 (digital) and 261 (DualShock), not the PCSX descriptors. PicoDrive selects six-button pads explicitly for Mega Drive/Sega CD; its set_controller_port_device callback does not implement pad selection.

SwanStation requires a user-provided `scph5501.bin` through existing BIOS management. No BIOS is distributed. PicoDrive reuses the existing regional Sega CD BIOS filenames and game-region detection.

## Coverage limits

The remaining current systems keep their validated core: PSP (PPSSPP), Atari 7800 (ProSystem), WonderSwan/Color (Beetle Cygne), DOS (DOSBox Pure), and 3DS (Citra). No second implementation with verified binary, frontend integration and useful compatibility was admitted for these systems. In particular Citra remains ARM64-only and cannot run on this ARM32 TV. This is not a claim that no other emulator exists. MesenCE (mesen2) was specifically investigated as a WonderSwan alternative and rejected: its Libretro port initializes only the horizontal controller mapping, omits U/D for the second directional pad and leaves vertical mapping uninitialized. Shipping it as a complete WonderSwan alternative would misrepresent control support. The investigation is in `build/core-options-research/mesen2-libretro.cpp` and `mesen2-ws-controller.h`; no MesenCE binary is packaged.

## Evidence and reproducibility

`manifest.json` records original URLs, archive and library SHA256, ABI and byte size. SHA256 pins this snapshot; it is not a publisher signature. All 72 files passed ELF class/machine checks. All 18 ARMv7 files passed dlopen, required Libretro symbol checks and retro_get_system_info on the real TV in isolated processes (`build/core-expansion/tv-core-probes.json`). This is load compatibility, not a gameplay/FPS certification. Dependencies are Android system libraries only; no missing libc++_shared dependency was found.

Run `python lemuroid-core-alternatives/tools/restore_snapshot.py` from the project to verify every stored library. `--restore` restores a missing file only while the official archive still matches the pinned hash; a changed moving endpoint fails closed. `fetch_snapshot.py` refuses to overwrite an existing manifest. Updates require an explicitly reviewed new snapshot and renewed TV checks.

`SOURCES.md` and `source-versions.json` map actual core-reported commit suffixes to corresponding upstream source. License notices and Libretro info files are also packaged as Android assets in `core-licenses`; upstream submodule/component conditions apply. No native binary was patched.

Primary references: [official Android buildbot](https://buildbot.libretro.com/nightly/android/latest/armeabi-v7a/), [Libretro arcade guide](https://docs.libretro.com/guides/arcade-getting-started/), [Stella 2014](https://docs.libretro.com/library/stella2014/), [QuickNES](https://docs.libretro.com/library/quicknes/), [Snes9x 2005 Plus](https://docs.libretro.com/library/snes9x_2005_plus/), [PicoDrive](https://docs.libretro.com/library/picodrive/), [SameBoy](https://docs.libretro.com/library/sameboy/), [gpSP](https://docs.libretro.com/library/gpsp/), [Beetle PCE](https://docs.libretro.com/library/mednafen_pce/), [RACE](https://docs.libretro.com/library/race/).

## Mesen callback-order adapters

The pinned older Mesen and Mesen-S ports dereference renderer/audio/input objects in callback setters before those objects exist. LibretroDroid registers callbacks before retro_init, which crashed Mesen on the ARM32 TV. `compat/mesen_callbacks.cpp` delays the five setters until after original initialization and forwards the remaining Libretro API. The original libraries remain unmodified beside the adapters. CoreID uses the `*_compat_libretro_android.so` entry points.

`python tools/build_mesen_compat.py` builds all eight adapters with NDK 27.2.12479018 and records their SHA256 in `compat/manifest.json`. The module now packages 80 pinned upstream ELF files plus eight adapters. `compat/callback_order_probe.cpp` is an independent lifecycle regression probe: register callbacks before init, initialize, retrieve metadata, deinitialize. Both adapters passed it on the actual ARM32 TV. Game execution is validated separately.
