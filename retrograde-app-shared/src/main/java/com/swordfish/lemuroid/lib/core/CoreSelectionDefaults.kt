package com.swordfish.lemuroid.lib.core

import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID

/** Built-in defaults before the expanded catalogue. Never derive these from list ordering. */
object CoreSelectionDefaults {
    fun coreFor(system: SystemID): CoreID = when (system) {
        SystemID.ATARI2600 -> CoreID.STELLA
        SystemID.NES -> CoreID.FCEUMM
        SystemID.SNES -> CoreID.SNES9X
        SystemID.SMS, SystemID.GENESIS, SystemID.SEGACD, SystemID.GG -> CoreID.GENESIS_PLUS_GX
        SystemID.GB, SystemID.GBC -> CoreID.GAMBATTE
        SystemID.GBA -> CoreID.MGBA
        SystemID.N64 -> CoreID.MUPEN64_PLUS_NEXT
        SystemID.PSX -> CoreID.PCSX_REARMED
        SystemID.PSP -> CoreID.PPSSPP
        SystemID.FBNEO -> CoreID.FBNEO
        SystemID.MAME2003PLUS -> CoreID.MAME2003PLUS
        SystemID.NDS -> CoreID.MELONDS
        SystemID.ATARI7800 -> CoreID.PROSYSTEM
        SystemID.LYNX -> CoreID.HANDY
        SystemID.PC_ENGINE -> CoreID.MEDNAFEN_PCE_FAST
        SystemID.NGP, SystemID.NGC -> CoreID.MEDNAFEN_NGP
        SystemID.WS, SystemID.WSC -> CoreID.MEDNAFEN_WSWAN
        SystemID.DOS -> CoreID.DOSBOX_PURE
        SystemID.NINTENDO_3DS -> CoreID.CITRA
    }

    fun resolve(savedName: String?, available: List<CoreID>, default: CoreID): CoreID {
        require(available.isNotEmpty())
        return available.firstOrNull { it.coreName == savedName }
            ?: default.takeIf { it in available }
            ?: available.first()
    }
}
