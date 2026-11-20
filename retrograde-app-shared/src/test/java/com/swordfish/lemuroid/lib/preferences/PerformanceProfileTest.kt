package com.swordfish.lemuroid.lib.preferences

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
    fun qualityLeavesCoreSettingsUntouched() {
        val explicitlyConfigured = setOf(
            CoreID.NOODS, CoreID.DESMUME_2015, CoreID.DSPERATE,
            CoreID.GPSP, CoreID.PARALLEL_N64, CoreID.SWANSTATION, CoreID.DOSBOX_PURE,
        )
        CoreID.entries.filter { it !in explicitlyConfigured }.forEach { core ->
            assertTrue(
                PerformanceProfileManager.getCoreOverridesForProfile(
                    PerformanceProfile.QUALITY,
                    SystemID.NDS,
                    core,
                ).isEmpty(),
            )
        }
    }

    @Test
    fun noodsProfilesKeepFullFrameOutputAndSeparateAccuracyFromSpeed() {
        val profiles = PerformanceProfile.entries.associateWith { profile ->
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
        val profiles = PerformanceProfile.entries.associateWith { profile ->
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
            val settings = PerformanceProfileManager.getCoreOverridesForProfile(
                profile, SystemID.NDS, CoreID.DSPERATE,
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
            fun settings(core: CoreID, system: SystemID) =
                PerformanceProfileManager.getCoreOverridesForProfile(profile, system, core)
                    .associate { it.key to it.value }

            val gba = settings(CoreID.GPSP, SystemID.GBA)
            assertEquals("enabled", gba["gpsp_drc"])
            assertEquals("disabled", gba["gpsp_frameskip"])
            assertEquals(if (profile == PerformanceProfile.QUALITY) "enabled" else "disabled", gba["gpsp_color_correction"])

            val n64 = settings(CoreID.PARALLEL_N64, SystemID.N64)
            assertEquals("rice", n64["parallel-n64-gfxplugin"])
            assertEquals("dynamic_recompiler", n64["parallel-n64-cpucore"])
            assertEquals(if (profile == PerformanceProfile.QUALITY) "640x480" else "320x240", n64["parallel-n64-screensize"])

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
