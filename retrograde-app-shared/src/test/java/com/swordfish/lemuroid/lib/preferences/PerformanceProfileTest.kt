package com.swordfish.lemuroid.lib.preferences

import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceProfileTest {
    @Test
    fun performanceKeepsItsExistingDesmumeSpeedTradeoff() {
        val options = options(PerformanceProfile.PERFORMANCE)

        assertEquals("2", options["desmume_frameskip"])
        assertEquals("256x192", options["desmume_internal_resolution"])
        assertEquals("jit", options["desmume_cpu_mode"])
        assertEquals("12", options["desmume_jit_block_size"])
        assertEquals("2", options["desmume_num_cores"])
        assertEquals("disabled", options["desmume_gfx_edgemark"])
    }

    @Test
    fun balancedKeepsItsExistingDesmumeTradeoff() {
        val options = options(PerformanceProfile.BALANCED)

        assertEquals("1", options["desmume_frameskip"])
        assertEquals("jit", options["desmume_cpu_mode"])
        assertTrue("desmume_gfx_edgemark" !in options)
        assertTrue("desmume_advanced_timing" !in options)
    }

    @Test
    fun qualityUsesJitAndSpendsWorkOnActualImageQuality() {
        fun quality(core: CoreID) =
            PerformanceProfileManager.getCoreOverridesForProfile(
                PerformanceProfile.QUALITY,
                SystemID.N64,
                core,
            ).associate { it.key to it.value }

        assertEquals("dynamic_recompiler", quality(CoreID.MUPEN64_PLUS_NEXT)["mupen64plus-cpucore"])
        assertEquals("640x480", quality(CoreID.MUPEN64_PLUS_NEXT)["mupen64plus-43screensize"])
        assertEquals("enabled", quality(CoreID.PCSX_REARMED)["pcsx_rearmed_drc"])
        assertEquals("enabled", quality(CoreID.PCSX_REARMED)["pcsx_rearmed_neon_enhancement_enable"])
        assertEquals("960x544", quality(CoreID.PPSSPP)["ppsspp_internal_resolution"])
    }

    @Test
    fun profilesHaveDistinctCoreWorkBudgetsWithoutArtificialFrameskip() {
        val cores =
            listOf(
                CoreID.PCSX_REARMED,
                CoreID.MUPEN64_PLUS_NEXT,
                CoreID.PPSSPP,
                CoreID.SNES9X,
                CoreID.GENESIS_PLUS_GX,
                CoreID.FBNEO,
                CoreID.MAME2003PLUS,
                CoreID.MAME_2003,
            )
        cores.forEach { core ->
            val settings =
                PerformanceProfile.entries.map {
                    PerformanceProfileManager.getCoreOverridesForProfile(it, SystemID.SNES, core)
                        .associate { option -> option.key to option.value }
                }
            assertEquals("All three budgets should differ for $core", 3, settings.toSet().size)
            assertTrue(settings.all { options -> options.keys.none { it.contains("frameskip") } })
        }
    }

    @Test
    fun arcadeInterpolationUsesExactSupportedValues() {
        fun settings(profile: PerformanceProfile) =
            PerformanceProfileManager.getCoreOverridesForProfile(
                profile,
                SystemID.FBNEO,
                CoreID.FBNEO,
            ).associate { it.key to it.value }
        assertEquals("4-point 3rd order", settings(PerformanceProfile.QUALITY)["fbneo-fm-interpolation"])
        assertEquals("2-point 1st order", settings(PerformanceProfile.BALANCED)["fbneo-sample-interpolation"])
        assertEquals("disabled", settings(PerformanceProfile.PERFORMANCE)["fbneo-fm-interpolation"])
    }

    @Test
    fun mupenUsesExistingHybridOptionAndAvoidsUnknownNoiseOption() {
        PerformanceProfile.entries.forEach { profile ->
            val settings =
                PerformanceProfileManager.getCoreOverridesForProfile(
                    profile,
                    SystemID.N64,
                    CoreID.MUPEN64_PLUS_NEXT,
                ).associate { it.key to it.value }
            assertTrue("mupen64plus-HybridFilter" in settings)
            assertTrue("mupen64plus-EnableHybridFilter" !in settings)
            assertTrue("mupen64plus-EnableNoiseEmulation" !in settings)
        }
    }

    @Test
    fun noodsProfilesKeepFullFrameOutputAndSeparateAccuracyFromSpeed() {
        val profiles =
            PerformanceProfile.entries.associateWith { profile ->
                PerformanceProfileManager.getCoreOverridesForProfile(profile, SystemID.NDS, CoreID.NOODS)
                    .associate { it.key to it.value }
            }
        profiles.values.forEach { options ->
            assertEquals("disabled", options["noods_frameskip"])
            assertEquals("disabled", options["noods_fpsLimiter"])
        }
        assertEquals("enabled", profiles.getValue(PerformanceProfile.QUALITY)["noods_highRes3D"])
        assertEquals("disabled", profiles.getValue(PerformanceProfile.BALANCED)["noods_highRes3D"])
        assertEquals("disabled", profiles.getValue(PerformanceProfile.BALANCED)["noods_arm7Hle"])
        assertEquals("disabled", profiles.getValue(PerformanceProfile.PERFORMANCE)["noods_arm7Hle"])
        assertEquals("disabled", profiles.getValue(PerformanceProfile.PERFORMANCE)["noods_highRes3D"])
    }

    private fun options(profile: PerformanceProfile): Map<String, String> =
        PerformanceProfileManager.getCoreOverridesForProfile(profile, SystemID.NDS, CoreID.DESMUME)
            .associate { it.key to it.value }

    @Test
    fun desmume2015ProfilesDoNotReuseLegacyPreferenceKeys() {
        val profiles =
            PerformanceProfile.entries.associateWith { profile ->
                PerformanceProfileManager.getCoreOverridesForProfile(profile, SystemID.NDS, CoreID.DESMUME_2015)
                    .associate { it.key to it.value }
            }
        profiles.values.forEach { settings ->
            assertTrue(settings.keys.all { it.startsWith("desmume2015_") })
            assertEquals("jit", settings["desmume2015_cpu_mode"])
            assertEquals("256x192", settings["desmume2015_internal_resolution"])
        }
        assertEquals("0", profiles.getValue(PerformanceProfile.QUALITY)["desmume2015_frameskip"])
        assertEquals("1", profiles.getValue(PerformanceProfile.BALANCED)["desmume2015_frameskip"])
        assertEquals("2", profiles.getValue(PerformanceProfile.PERFORMANCE)["desmume2015_frameskip"])
    }

    @Test
    fun dsperateProfilesKeepJitAndFullFrameOutput() {
        PerformanceProfile.entries.forEach { profile ->
            val settings =
                PerformanceProfileManager.getCoreOverridesForProfile(
                    profile,
                    SystemID.NDS,
                    CoreID.DSPERATE,
                ).associate { it.key to it.value }
            assertEquals("enabled", settings["dsperate_jit"])
            assertEquals("disabled", settings["dsperate_frameskip"])
            assertEquals("empty", settings["dsperate_slot2"])
            assertTrue(settings.keys.none { it.contains("bezel") })
        }
    }

    @Test
    fun alternativeProfilesKeepFullFramesAndPortableRenderers() {
        PerformanceProfile.entries.forEach { profile ->
            fun settings(
                core: CoreID,
                system: SystemID,
            ) = PerformanceProfileManager.getCoreOverridesForProfile(profile, system, core)
                .associate { it.key to it.value }

            val gba = settings(CoreID.GPSP, SystemID.GBA)
            assertEquals("enabled", gba["gpsp_drc"])
            assertEquals("disabled", gba["gpsp_frameskip"])
            assertEquals(
                if (profile == PerformanceProfile.QUALITY) "enabled" else "disabled",
                gba["gpsp_color_correction"],
            )

            val n64 = settings(CoreID.PARALLEL_N64, SystemID.N64)
            assertEquals("rice", n64["parallel-n64-gfxplugin"])
            assertEquals("dynamic_recompiler", n64["parallel-n64-cpucore"])
            assertEquals(
                if (profile == PerformanceProfile.QUALITY) "640x480" else "320x240",
                n64["parallel-n64-screensize"],
            )

            val psx = settings(CoreID.SWANSTATION, SystemID.PSX)
            assertEquals("Recompiler", psx["swanstation_CPU_ExecutionMode"])
            assertEquals("Software", psx["swanstation_GPU_Renderer"])
            assertEquals("1", psx["swanstation_GPU_ResolutionScale"])
            assertEquals("false", psx["swanstation_GPU_PGXPEnable"])

            val dos = settings(CoreID.DOSBOX_PURE, SystemID.DOS)
            assertEquals(mapOf("dosbox_pure_cycles" to "auto"), dos)
        }
    }

    @Test
    fun manualCoreChoiceWinsOverEveryProfileWithoutChangingUnrelatedDefaults() {
        PerformanceProfile.entries.forEach { profile ->
            val defaults = listOf(CoreVariable("pcsx_rearmed_drc", "disabled"), CoreVariable("input", "dualshock"))
            val budget =
                PerformanceProfileManager.getCoreOverridesForProfile(
                    profile,
                    SystemID.PSX,
                    CoreID.PCSX_REARMED,
                )
            val manual = listOf(CoreVariable("pcsx_rearmed_neon_enhancement_enable", "disabled"))
            val merged =
                CoreVariablesManager.mergeCoreOptions(
                    defaults,
                    budget,
                    manual,
                ).associate { it.key to it.value }
            assertEquals("enabled", merged["pcsx_rearmed_drc"])
            assertEquals("disabled", merged["pcsx_rearmed_neon_enhancement_enable"])
            assertEquals("dualshock", merged["input"])
        }
    }

    @Test
    fun profilesNeverOverrideConfigurableBezelForAnyCore() {
        PerformanceProfile.entries.forEach { profile ->
            CoreID.entries.forEach { core ->
                val settings = PerformanceProfileManager.getCoreOverridesForProfile(profile, SystemID.NDS, core)
                assertTrue(settings.none { it.key.contains("bezel", ignoreCase = true) })
                assertEquals(settings.size, settings.map { it.key }.toSet().size)
            }
        }
    }
}
