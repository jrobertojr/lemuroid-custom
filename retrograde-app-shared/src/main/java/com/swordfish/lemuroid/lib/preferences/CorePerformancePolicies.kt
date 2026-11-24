package com.swordfish.lemuroid.lib.preferences

import com.swordfish.lemuroid.lib.core.CoreVariable
import com.swordfish.lemuroid.lib.library.CoreID

/** Core-side work budgets; manual options are merged afterwards by CoreVariablesManager. */
internal object CorePerformancePolicies {
    fun options(
        profile: PerformanceProfile,
        core: CoreID,
    ): List<CoreVariable>? {
        val quality = profile == PerformanceProfile.QUALITY
        val performance = profile == PerformanceProfile.PERFORMANCE
        return when (core) {
            CoreID.PCSX_REARMED ->
                listOf(
                    // Interpreter mode costs CPU without improving the rendered image.
                    CoreVariable("pcsx_rearmed_drc", "enabled"),
                    CoreVariable("pcsx_rearmed_neon_enhancement_enable", if (quality) "enabled" else "disabled"),
                    CoreVariable("pcsx_rearmed_neon_enhancement_no_main", "disabled"),
                    CoreVariable("pcsx_rearmed_spu_interpolation", if (performance) "simple" else "gaussian"),
                    CoreVariable("pcsx_rearmed_spu_reverb", if (performance) "disabled" else "enabled"),
                    CoreVariable("pcsx_rearmed_dithering", if (performance) "disabled" else "enabled"),
                )
            CoreID.MUPEN64_PLUS_NEXT ->
                listOf(
                    CoreVariable("mupen64plus-cpucore", "dynamic_recompiler"),
                    CoreVariable("mupen64plus-43screensize", if (quality) "640x480" else "320x240"),
                    CoreVariable("mupen64plus-EnableShadersStorage", "True"),
                    CoreVariable("mupen64plus-BilinearMode", if (performance) "standard" else "3point"),
                    CoreVariable("mupen64plus-HybridFilter", if (quality) "True" else "False"),
                    CoreVariable("mupen64plus-EnableLODEmulation", if (performance) "False" else "True"),
                    CoreVariable("mupen64plus-MultiSampling", "0"),
                    CoreVariable("mupen64plus-txFilterMode", "None"),
                    CoreVariable("mupen64plus-txEnhancementMode", "None"),
                    // Keep framebuffer/copy settings from the device defaults. They affect compatibility.
                )
            CoreID.PPSSPP ->
                listOf(
                    CoreVariable("ppsspp_cpu_core", "JIT"),
                    CoreVariable("ppsspp_internal_resolution", if (quality) "960x544" else "480x272"),
                    CoreVariable("ppsspp_gpu_hardware_transform", "enabled"),
                    CoreVariable("ppsspp_lazy_texture_caching", if (quality) "disabled" else "enabled"),
                    CoreVariable("ppsspp_texture_scaling_level", "disabled"),
                    CoreVariable(
                        "ppsspp_spline_quality",
                        when (profile) {
                            PerformanceProfile.QUALITY -> "High"
                            PerformanceProfile.BALANCED -> "Medium"
                            PerformanceProfile.PERFORMANCE -> "Low"
                        },
                    ),
                    // Never skip framebuffer effects/readbacks or alter the emulated clock automatically.
                    CoreVariable("ppsspp_io_timing_method", "Fast"),
                )
            CoreID.GENESIS_PLUS_GX ->
                listOf(
                    CoreVariable("genesis_plus_gx_ym2612", if (quality) "nuked (ym2612)" else "mame (ym2612)"),
                    CoreVariable("genesis_plus_gx_ym2413_core", if (quality) "nuked" else "mame"),
                    CoreVariable("genesis_plus_gx_audio_filter", if (performance) "disabled" else "low-pass"),
                    CoreVariable("genesis_plus_gx_blargg_ntsc_filter", "disabled"),
                    CoreVariable("genesis_plus_gx_lcd_filter", "disabled"),
                )
            CoreID.SNES9X ->
                listOf(
                    CoreVariable(
                        "snes9x_audio_interpolation",
                        when (profile) {
                            PerformanceProfile.QUALITY -> "gaussian"
                            PerformanceProfile.BALANCED -> "linear"
                            PerformanceProfile.PERFORMANCE -> "none"
                        },
                    ),
                    // Keep the core's high-resolution output for readable text/transparency.
                    CoreVariable("snes9x_blargg", "disabled"),
                )
            CoreID.FBNEO ->
                listOf(
                    // FM supports disabled/4-point only; the former "2-point" value was ignored.
                    CoreVariable("fbneo-fm-interpolation", if (performance) "disabled" else "4-point 3rd order"),
                    CoreVariable(
                        "fbneo-sample-interpolation",
                        when (profile) {
                            PerformanceProfile.QUALITY -> "4-point 3rd order"
                            PerformanceProfile.BALANCED -> "2-point 1st order"
                            PerformanceProfile.PERFORMANCE -> "disabled"
                        },
                    ),
                )
            CoreID.MAME2003PLUS, CoreID.MAME_2003 -> {
                val prefix = if (core == CoreID.MAME2003PLUS) "mame2003-plus" else "mame2003"
                listOf(
                    CoreVariable(
                        "${prefix}_sample_rate",
                        when (profile) {
                            PerformanceProfile.QUALITY -> "48000"
                            PerformanceProfile.BALANCED -> "44100"
                            PerformanceProfile.PERFORMANCE -> "22050"
                        },
                    ),
                )
            }
            else -> null
        }
    }
}
