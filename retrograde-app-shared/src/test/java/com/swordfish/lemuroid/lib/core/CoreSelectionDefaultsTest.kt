package com.swordfish.lemuroid.lib.core

import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreSelectionDefaultsTest {
    @Test
    fun manualSelectionSurvivesCatalogueReorderingAndDefaults() {
        val saved = CoreID.DSPERATE.coreName
        assertEquals(
            CoreID.DSPERATE,
            CoreSelectionDefaults.resolve(saved, listOf(CoreID.MELONDS, CoreID.DSPERATE, CoreID.DESMUME), CoreID.MELONDS),
        )
        assertEquals(
            CoreID.DSPERATE,
            CoreSelectionDefaults.resolve(saved, listOf(CoreID.DESMUME, CoreID.DSPERATE, CoreID.MELONDS), CoreID.DESMUME),
        )
    }

    @Test
    fun missingAndObsoleteSelectionsFallBackWithoutCrashing() {
        listOf(null, "", "removed_core").forEach { saved ->
            assertEquals(
                CoreID.MELONDS,
                CoreSelectionDefaults.resolve(saved, listOf(CoreID.DSPERATE, CoreID.MELONDS), CoreID.MELONDS),
            )
        }
        assertEquals(
            CoreID.DSPERATE,
            CoreSelectionDefaults.resolve(null, listOf(CoreID.DSPERATE), CoreID.MELONDS),
        )
    }

    @Test
    fun resetKeepsOriginalDefaultsForEverySupportedSystem() {
        val expected = mapOf(
            SystemID.NES to CoreID.FCEUMM,
            SystemID.SNES to CoreID.SNES9X,
            SystemID.GENESIS to CoreID.GENESIS_PLUS_GX,
            SystemID.GB to CoreID.GAMBATTE,
            SystemID.GBC to CoreID.GAMBATTE,
            SystemID.GBA to CoreID.MGBA,
            SystemID.N64 to CoreID.MUPEN64_PLUS_NEXT,
            SystemID.SMS to CoreID.GENESIS_PLUS_GX,
            SystemID.PSP to CoreID.PPSSPP,
            SystemID.NDS to CoreID.MELONDS,
            SystemID.GG to CoreID.GENESIS_PLUS_GX,
            SystemID.ATARI2600 to CoreID.STELLA,
            SystemID.PSX to CoreID.PCSX_REARMED,
            SystemID.FBNEO to CoreID.FBNEO,
            SystemID.MAME2003PLUS to CoreID.MAME2003PLUS,
            SystemID.PC_ENGINE to CoreID.MEDNAFEN_PCE_FAST,
            SystemID.LYNX to CoreID.HANDY,
            SystemID.ATARI7800 to CoreID.PROSYSTEM,
            SystemID.SEGACD to CoreID.GENESIS_PLUS_GX,
            SystemID.NGP to CoreID.MEDNAFEN_NGP,
            SystemID.NGC to CoreID.MEDNAFEN_NGP,
            SystemID.WS to CoreID.MEDNAFEN_WSWAN,
            SystemID.WSC to CoreID.MEDNAFEN_WSWAN,
            SystemID.DOS to CoreID.DOSBOX_PURE,
            SystemID.NINTENDO_3DS to CoreID.CITRA,
        )
        assertEquals(SystemID.entries.toSet(), expected.keys)
        expected.forEach { (system, core) -> assertEquals(core, CoreSelectionDefaults.coreFor(system)) }
    }
}
