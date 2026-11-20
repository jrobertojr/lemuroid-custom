package com.swordfish.lemuroid.lib.preferences

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game

enum class PerformanceProfile(val prefValue: String) {
    QUALITY("quality"),
    BALANCED("balanced"),
    PERFORMANCE("performance"),
    ;

    companion object {
        fun fromPrefValue(value: String?): PerformanceProfile {
            return values().firstOrNull { it.prefValue == value } ?: BALANCED
        }
    }
}

object PerformanceProfileManager {
    const val PREF_KEY_PERFORMANCE_PROFILE = "pref_key_performance_profile"

    fun detectBestProfile(context: Context): PerformanceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamBytes = memoryInfo.totalMem

        val is32Bit = Build.SUPPORTED_64_BIT_ABIS.isEmpty()

        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTv = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        val cpuCores = Runtime.getRuntime().availableProcessors()

        return when {
            is32Bit || totalRamBytes <= 2_600_000_000L || (isTv && totalRamBytes <= 3_400_000_000L) ->
                PerformanceProfile.PERFORMANCE
            isTv || totalRamBytes <= 4_600_000_000L || cpuCores <= 4 ->
                PerformanceProfile.BALANCED
            else ->
                PerformanceProfile.QUALITY
        }
    }

    fun ensureInitialized(context: Context): PerformanceProfile {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        if (!prefs.contains(PREF_KEY_PERFORMANCE_PROFILE)) {
            val detected = detectBestProfile(context)
            prefs.edit().putString(PREF_KEY_PERFORMANCE_PROFILE, detected.prefValue).apply()
        }
        return getActiveProfile(context)
    }

    fun getActiveProfile(context: Context): PerformanceProfile {
        val prefs = SharedPreferencesHelper.getSharedPreferences(context)
        val value = prefs.getString(PREF_KEY_PERFORMANCE_PROFILE, null)
        return if (value == null) {
            detectBestProfile(context)
        } else {
            PerformanceProfile.fromPrefValue(value)
        }
    }

    fun getActiveProfile(context: Context, game: Game): PerformanceProfile =
        GamePreferencesManager(SharedPreferencesHelper.getSharedPreferences(context))
            .getEffectiveProfile(game, getActiveProfile(context))

    fun getProfileFromPreferences(prefs: SharedPreferences): PerformanceProfile {
        val value = prefs.getString(PREF_KEY_PERFORMANCE_PROFILE, null)
        return if (value == null) {
            if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) {
                PerformanceProfile.PERFORMANCE
            } else {
                PerformanceProfile.QUALITY
            }
        } else {
            PerformanceProfile.fromPrefValue(value)
        }
    }

    fun getCoreOverridesForProfile(
        profile: PerformanceProfile,
        systemID: SystemID,
        coreID: CoreID,
    ): List<CoreVariable> {
        // These new cores need explicit, portable defaults in all three modes.
        // Manual core preferences are still merged after profile values by CoreVariablesManager.
        when (coreID) {
            CoreID.GPSP -> return listOf(
                CoreVariable("gpsp_drc", "enabled"),
                CoreVariable("gpsp_frameskip", "disabled"),
                CoreVariable("gpsp_color_correction", if (profile == PerformanceProfile.QUALITY) "enabled" else "disabled"),
            )
            CoreID.PARALLEL_N64 -> return listOf(
                // Rice works with the GLES frontend; never silently select Vulkan/ParaLLEl-RDP.
                CoreVariable("parallel-n64-gfxplugin", "rice"),
                CoreVariable("parallel-n64-cpucore", "dynamic_recompiler"),
                CoreVariable("parallel-n64-screensize", if (profile == PerformanceProfile.QUALITY) "640x480" else "320x240"),
            )
            CoreID.SWANSTATION -> return listOf(
                CoreVariable("swanstation_CPU_ExecutionMode", "Recompiler"),
                // Software output at native resolution avoids unsupported graphics contexts.
                // Resolution scaling and PGXP require a validated hardware renderer.
                CoreVariable("swanstation_GPU_Renderer", "Software"),
                CoreVariable("swanstation_GPU_ResolutionScale", "1"),
                CoreVariable("swanstation_GPU_PGXPEnable", "false"),
            )
            CoreID.DOSBOX_PURE -> return listOf(
                // DOS games depend on different CPU speeds. Arbitrary fixed cycles can break timing.
                CoreVariable("dosbox_pure_cycles", "auto"),
            )
            else -> Unit
        }
        if (coreID == CoreID.DSPERATE) {
            // Keep accurate CPU/audio and full frame output in every mode.
            // HD/filter differences remain owned by the frontend's three profiles.
            return listOf(
                CoreVariable("dsperate_jit", "enabled"),
                CoreVariable("dsperate_frameskip", "disabled"),
                CoreVariable("dsperate_slot2", "empty"),
            )
        }
        if (coreID == CoreID.DESMUME_2015) {
            val settings = if (profile == PerformanceProfile.QUALITY) {
                listOf(
                    CoreVariable("desmume_cpu_mode", "jit"),
                    CoreVariable("desmume_jit_block_size", "12"),
                    CoreVariable("desmume_num_cores", "2"),
                    CoreVariable("desmume_internal_resolution", "256x192"),
                    CoreVariable("desmume_frameskip", "0"),
                )
            } else {
                getCoreOverridesForProfile(profile, systemID, CoreID.DESMUME)
            }
            return settings.filterNot {
                // This older renderer is already RGB565 and has no high-res interpolation option.
                it.key == "desmume_color_depth" || it.key == "desmume_gfx_highres_interpolate_color"
            }.map {
                CoreVariable(it.key.replaceFirst("desmume_", "desmume2015_"), it.value)
            }
        }
        if (coreID == CoreID.NOODS) {
            return listOf(
                // LibretroDroid owns pacing. Never add a second limiter or a fixed frameskip.
                CoreVariable("noods_fpsLimiter", "disabled"),
                CoreVariable("noods_frameskip", "disabled"),
                // Two renderer workers outperform one on the reference four-core ARM TV.
                CoreVariable("noods_threaded2D", "enabled"),
                CoreVariable("noods_threaded3D", "2 Threads"),
                CoreVariable("noods_highRes3D", if (profile == PerformanceProfile.QUALITY) "enabled" else "disabled"),
                // Upstream HLE stubs audio commands and can black-screen commercial games.
                CoreVariable("noods_arm7Hle", "disabled"),
                CoreVariable("noods_screenFilter", if (profile == PerformanceProfile.QUALITY) "Linear" else "Nearest"),
                CoreVariable("noods_screenGhost", "disabled"),
            )
        }
        return when (profile) {
            PerformanceProfile.QUALITY -> emptyList()
            PerformanceProfile.BALANCED -> getBalancedOverrides(coreID)
            PerformanceProfile.PERFORMANCE -> {
                val merged = linkedMapOf<String, String>()
                getBalancedOverrides(coreID).forEach { merged[it.key] = it.value }
                getPerformanceExtraOverrides(coreID).forEach { merged[it.key] = it.value }
                merged.entries.map { CoreVariable(it.key, it.value) }
            }
        }
    }

    private fun getBalancedOverrides(coreID: CoreID): List<CoreVariable> {
        return when (coreID) {
            CoreID.PCSX_REARMED ->
                listOf(
                    CoreVariable("pcsx_rearmed_drc", "enabled"),
                    CoreVariable("pcsx_rearmed_neon_enhancement_enable", "disabled"),
                    CoreVariable("pcsx_rearmed_spu_interpolation", "simple"),
                )
            CoreID.MUPEN64_PLUS_NEXT ->
                listOf(
                    CoreVariable("mupen64plus-cpucore", "dynamic_recompiler"),
                    CoreVariable("mupen64plus-43screensize", "320x240"),
                    CoreVariable("mupen64plus-EnableShadersStorage", "True"),
                    CoreVariable("mupen64plus-BilinearMode", "standard"),
                    CoreVariable("mupen64plus-MultiSampling", "0"),
                )
            CoreID.MELONDS ->
                listOf(
                    CoreVariable("melonds_threaded_renderer", "enabled"),
                    CoreVariable("melonds_jit_enable", "enabled"),
                )
            CoreID.DESMUME ->
                listOf(
                    CoreVariable("desmume_cpu_mode", "jit"),
                    CoreVariable("desmume_jit_block_size", "12"),
                    CoreVariable("desmume_num_cores", "2"),
                    CoreVariable("desmume_internal_resolution", "256x192"),
                    CoreVariable("desmume_frameskip", "1"),
                )
            CoreID.PPSSPP ->
                listOf(
                    CoreVariable("ppsspp_cpu_core", "JIT"),
                    CoreVariable("ppsspp_internal_resolution", "480x272"),
                    CoreVariable("ppsspp_gpu_hardware_transform", "enabled"),
                    CoreVariable("ppsspp_vertex_cache", "enabled"),
                    CoreVariable("ppsspp_lazy_texture_caching", "enabled"),
                )
            CoreID.FBNEO ->
                listOf(
                    CoreVariable("fbneo-fm-interpolation", "2-point"),
                )
            CoreID.MAME2003PLUS ->
                listOf(
                    CoreVariable("mame2003-plus_skip_disclaimer", "enabled"),
                    CoreVariable("mame2003-plus_skip_warnings", "enabled"),
                )
            CoreID.MGBA ->
                listOf(
                    CoreVariable("mgba_idle_optimization", "Remove Known"),
                    CoreVariable("mgba_interframe_blending", "OFF"),
                )
            else -> emptyList()
        }
    }

    private fun getPerformanceExtraOverrides(coreID: CoreID): List<CoreVariable> {
        return when (coreID) {
            CoreID.PCSX_REARMED ->
                listOf(
                    CoreVariable("pcsx_rearmed_dithering", "disabled"),
                    CoreVariable("pcsx_rearmed_spu_reverb", "disabled"),
                )
            CoreID.MUPEN64_PLUS_NEXT ->
                listOf(
                    CoreVariable("mupen64plus-EnableHybridFilter", "False"),
                    CoreVariable("mupen64plus-txFilterMode", "None"),
                    CoreVariable("mupen64plus-txEnhancementMode", "None"),
                    CoreVariable("mupen64plus-EnableLODEmulation", "False"),
                    CoreVariable("mupen64plus-EnableNoiseEmulation", "False"),
                )
            CoreID.DESMUME ->
                listOf(
                    CoreVariable("desmume_frameskip", "2"),
                    CoreVariable("desmume_color_depth", "16-bit"),
                    CoreVariable("desmume_gfx_edgemark", "disabled"),
                    CoreVariable("desmume_gfx_linehack", "disabled"),
                    CoreVariable("desmume_gfx_txthack", "disabled"),
                    CoreVariable("desmume_gfx_highres_interpolate_color", "disabled"),
                )
            CoreID.PPSSPP ->
                listOf(
                    CoreVariable("ppsspp_texture_scaling_level", "1"),
                    CoreVariable("ppsspp_spline_quality", "Low"),
                    CoreVariable("ppsspp_io_timing_method", "Fast"),
                )
            CoreID.FBNEO ->
                listOf(
                    CoreVariable("fbneo-fm-interpolation", "disabled"),
                    CoreVariable("fbneo-sample-interpolation", "disabled"),
                )
            CoreID.MAME2003PLUS ->
                listOf(
                    CoreVariable("mame2003-plus_sample_rate", "22050"),
                )
            CoreID.MGBA ->
                listOf(
                    CoreVariable("mgba_color_correction", "OFF"),
                )
            CoreID.SNES9X ->
                listOf(
                    CoreVariable("snes9x_blargg", "disabled"),
                )
            CoreID.GENESIS_PLUS_GX ->
                listOf(
                    CoreVariable("genesis_plus_gx_blargg_ntsc_filter", "disabled"),
                    CoreVariable("genesis_plus_gx_lcd_filter", "disabled"),
                    CoreVariable("genesis_plus_gx_audio_filter", "disabled"),
                )
            else -> emptyList()
        }
    }
}
