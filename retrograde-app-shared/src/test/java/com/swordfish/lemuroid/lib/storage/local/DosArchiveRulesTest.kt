package com.swordfish.lemuroid.lib.storage.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DosArchiveRulesTest {
    @Test
    fun doszIsAnExplicitWholeDiskPackageAnywhere() {
        assertTrue(DosArchiveRules.isDosArchive("System Shock.DOSZ", null))
    }

    @Test
    fun dosFolderKeepsZipCompanionFilesTogetherForLocalAndSafPaths() {
        assertTrue(DosArchiveRules.isDosArchive("Dave.zip", "/storage/620F-B493/DOS/Dave.zip"))
        assertTrue(DosArchiveRules.isDosArchive("Dave.ZIP", "/tree/620F-B493%3ADOS/document/620F-B493%3ADOS%2FDave.ZIP"))
        assertTrue(DosArchiveRules.isDosArchive("Dave.zip", "/storage/620F-B493/MS-DOS/Dave.zip"))
    }

    @Test
    fun titleSubstringDoesNotTurnConsoleAndArcadeArchivesIntoDosPackages() {
        assertFalse(DosArchiveRules.isDosArchive("dosgames.zip", "/storage/roms/arcade/dosgames.zip"))
        assertFalse(DosArchiveRules.isDosArchive("Dave.exe", "/storage/DOS/Dave.exe"))
        assertFalse(DosArchiveRules.isDosArchive("game.zip", "/storage/roms/dosgames/game.zip"))
    }
}
