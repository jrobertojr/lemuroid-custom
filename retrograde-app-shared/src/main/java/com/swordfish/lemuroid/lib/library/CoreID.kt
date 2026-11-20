package com.swordfish.lemuroid.lib.library

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.assetsmanager.NoAssetsManager
import com.swordfish.lemuroid.lib.core.assetsmanager.PPSSPPAssetsManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager

enum class CoreID(
    val coreName: String,
    val coreDisplayName: String,
    val libretroFileName: String,
) {
    STELLA(
        "stella",
        "Stella",
        "libstella_libretro_android.so",
    ),
    FCEUMM(
        "fceumm",
        "FCEUmm",
        "libfceumm_libretro_android.so",
    ),
    SNES9X(
        "snes9x",
        "Snes9x",
        "libsnes9x_libretro_android.so",
    ),
    GENESIS_PLUS_GX(
        "genesis_plus_gx",
        "Genesis Plus GX",
        "libgenesis_plus_gx_libretro_android.so",
    ),
    GAMBATTE(
        "gambatte",
        "Gambatte",
        "libgambatte_libretro_android.so",
    ),
    MGBA(
        "mgba",
        "mGBA",
        "libmgba_libretro_android.so",
    ),
    MUPEN64_PLUS_NEXT(
        "mupen64plus_next_gles3",
        "Mupen64Plus",
        "libmupen64plus_next_gles3_libretro_android.so",
    ),
    PCSX_REARMED(
        "pcsx_rearmed",
        "PCSXReARMed",
        "libpcsx_rearmed_libretro_android.so",
    ),
    PPSSPP(
        "ppsspp",
        "PPSSPP",
        "libppsspp_libretro_android.so",
    ),
    FBNEO(
        "fbneo",
        "FBNeo — Compatibilidade / ROMset FBNeo",
        "libfbneo_libretro_android.so",
    ),
    MAME2003PLUS(
        "mame2003_plus",
        "MAME2003 Plus — Equilibrado / ROMset 2003-Plus",
        "libmame2003_plus_libretro_android.so",
    ),
    DESMUME(
        "desmume",
        "DeSmuME (Deprecated)",
        "libdesmume_libretro_android.so",
    ),
    MELONDS(
        "melonds",
        "MelonDS",
        "libmelonds_libretro_android.so",
    ),
    NOODS(
        "noods",
        "NooDS",
        "libnoods_libretro_android.so",
    ),
    DSPERATE(
        "dsperate",
        "DSperate — Desempenho",
        "libdsperate_libretro_android.so",
    ),
    DESMUME_2015(
        "desmume2015",
        "DeSmuME 2015",
        "libdesmume2015_libretro_android.so",
    ),
    HANDY(
        "handy",
        "Handy",
        "libhandy_libretro_android.so",
    ),
    MEDNAFEN_PCE_FAST(
        "mednafen_pce_fast",
        "PCEFast",
        "libmednafen_pce_fast_libretro_android.so",
    ),
    PROSYSTEM(
        "prosystem",
        "ProSystem",
        "libprosystem_libretro_android.so",
    ),
    MEDNAFEN_NGP(
        "mednafen_ngp",
        "Mednafen NGP",
        "libmednafen_ngp_libretro_android.so",
    ),
    MEDNAFEN_WSWAN(
        "mednafen_wswan",
        "Beetle Cygne",
        "libmednafen_wswan_libretro_android.so",
    ),
    CITRA(
        "citra",
        "Citra",
        "libcitra_libretro_android.so",
    ),
    DOSBOX_PURE(
        "dosbox_pure",
        "DosBox Pure",
        "libdosbox_pure_libretro_android.so",
    ),
    STELLA_2014("stella2014", "Stella 2014 — Desempenho", "libstella2014_libretro_android.so"),
    QUICKNES("quicknes", "QuickNES — Desempenho", "libquicknes_libretro_android.so"),
    NESTOPIA("nestopia", "Nestopia UE — Qualidade", "libnestopia_libretro_android.so"),
    SNES9X_2005_PLUS("snes9x2005_plus", "Snes9x 2005 Plus — Desempenho", "libsnes9x2005_plus_libretro_android.so"),
    SNES9X_2010("snes9x2010", "Snes9x 2010 — Equilibrado", "libsnes9x2010_libretro_android.so"),
    PICODRIVE("picodrive", "PicoDrive — Desempenho", "libpicodrive_libretro_android.so"),
    GEARSYSTEM("gearsystem", "Gearsystem — Precisão", "libgearsystem_libretro_android.so"),
    SAMEBOY("sameboy", "SameBoy — Qualidade", "libsameboy_libretro_android.so"),
    GPSP("gpsp", "gpSP — Desempenho", "libgpsp_libretro_android.so"),
    VBA_NEXT("vba_next", "VBA Next — Desempenho", "libvba_next_libretro_android.so"),
    PARALLEL_N64("parallel_n64", "ParaLLEl N64 — Alternativo", "libparallel_n64_libretro_android.so"),
    SWANSTATION("swanstation", "SwanStation — Qualidade (BIOS PS1)", "libswanstation_libretro_android.so"),
    FBALPHA_2012("fbalpha2012", "FBA 2012 — Desempenho / ROMset 0.2.97.29", "libfbalpha2012_libretro_android.so"),
    MAME_2000("mame2000", "MAME 2000 — Desempenho / ROMset 0.37b5", "libmame2000_libretro_android.so"),
    MAME_2003("mame2003", "MAME 2003 — Desempenho / ROMset 0.78", "libmame2003_libretro_android.so"),
    MEDNAFEN_PCE("mednafen_pce", "Beetle PCE — Qualidade", "libmednafen_pce_libretro_android.so"),
    MEDNAFEN_LYNX("mednafen_lynx", "Beetle Lynx — Precisão", "libmednafen_lynx_libretro_android.so"),
    MESEN("mesen", "Mesen — Precisão / Compatibilidade", "libmesen_compat_libretro_android.so"),
    MESEN_S("mesen-s", "Mesen-S — Precisão / Compatibilidade", "libmesen-s_compat_libretro_android.so"),
    RACE("race", "RACE — Desempenho", "librace_libretro_android.so"),
    ;

    companion object {
        fun getAssetManager(coreID: CoreID): AssetsManager {
            return when (coreID) {
                PPSSPP -> PPSSPPAssetsManager()
                else -> NoAssetsManager()
            }
        }
    }

    interface AssetsManager {
        suspend fun retrieveAssetsIfNeeded(
            coreUpdaterApi: CoreUpdater.CoreManagerApi,
            directoriesManager: DirectoriesManager,
            sharedPreferences: SharedPreferences,
        )

        suspend fun clearAssets(directoriesManager: DirectoriesManager)
    }
}

fun findByName(query: String): CoreID? = CoreID.values().firstOrNull { it.coreName == query }
