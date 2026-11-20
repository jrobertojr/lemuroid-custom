package com.swordfish.lemuroid.lib.preferences

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class GamePreferencesManagerTest {
    private val game = Game(1, "game.nds", "content://usb/DS/game.nds", "Old title", "nds", null, null, 0)

    @Test
    fun overridesRoundTripAcrossInstancesAndInheritWithoutChangingGlobalPreference() {
        val values = mutableMapOf<String, String>(PerformanceProfileManager.PREF_KEY_PERFORMANCE_PROFILE to "performance")
        val prefs = preferences(values)
        val manager = GamePreferencesManager(prefs)
        assertTrue(manager.setSystemProfileOverride(SystemID.NDS, PerformanceProfile.BALANCED))
        assertTrue(manager.setGameProfileOverride(game, PerformanceProfile.QUALITY))

        // Recreated manager and reindexed/renamed game still address the same persisted record.
        val reopened = GamePreferencesManager(prefs)
        val renamed = game.copy(id = 400, title = "New title", customName = "Custom")
        assertEquals(PerformanceProfile.QUALITY, reopened.getEffectiveProfile(renamed, PerformanceProfile.PERFORMANCE))
        assertTrue(reopened.setGameProfileOverride(renamed, null))
        assertEquals(PerformanceProfile.BALANCED, manager.getEffectiveProfile(game, PerformanceProfile.PERFORMANCE))
        assertTrue(manager.setSystemProfileOverride(SystemID.NDS, null))
        assertEquals(PerformanceProfile.PERFORMANCE, reopened.getEffectiveProfile(game, PerformanceProfile.PERFORMANCE))
        assertEquals(mapOf(PerformanceProfileManager.PREF_KEY_PERFORMANCE_PROFILE to "performance"), values)
    }

    @Test
    fun invalidOverridesInheritAndDoNotHideValidSystemSelection() {
        val values = mutableMapOf(
            GamePreferencesManager.gameProfileKey(game) to "removed-mode",
            GamePreferencesManager.systemProfileKey(SystemID.NDS) to "balanced",
        )
        val manager = GamePreferencesManager(preferences(values))
        assertNull(manager.getGameProfileOverride(game))
        assertEquals(PerformanceProfile.BALANCED, manager.getEffectiveProfile(game, PerformanceProfile.PERFORMANCE))
        assertNull(GamePreferencePolicy.resolveCore("missing-core", listOf(CoreID.DSPERATE)))
        assertNull(GamePreferencePolicy.resolveCore(CoreID.STELLA.coreName, listOf(CoreID.DSPERATE)))
        assertEquals(CoreID.DSPERATE, GamePreferencePolicy.resolveCore("dsperate", listOf(CoreID.DSPERATE)))
    }

    @Test
    fun gameKeysSeparateSameTitlesDifferentFilesAndSystems() {
        val key = GamePreferencesManager.gameProfileKey(game)
        assertEquals(key, GamePreferencesManager.gameProfileKey(game.copy(id = 1000, title = "Renamed")))
        assertNotEquals(key, GamePreferencesManager.gameProfileKey(game.copy(fileUri = "content://usb/Other/game.nds")))
        assertNotEquals(key, GamePreferencesManager.gameProfileKey(game.copy(systemId = "gba")))
    }

    private fun preferences(values: MutableMap<String, String>): SharedPreferences {
        val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args!![0] as String] = args[1] as String; proxy }
                "remove" -> { values.remove(args!![0] as String); proxy }
                "commit" -> true
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getString" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SharedPreferences
    }
}
