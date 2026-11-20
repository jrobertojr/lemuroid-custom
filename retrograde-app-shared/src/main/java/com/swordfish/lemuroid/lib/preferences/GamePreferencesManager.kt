package com.swordfish.lemuroid.lib.preferences

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.security.MessageDigest

/** Overrides are launch settings: changing them never mutates an active emulation session. */
class GamePreferencesManager(private val preferences: SharedPreferences) {
    fun getCoreOverride(game: Game): CoreID? {
        val name = preferences.getString(coreKey(game), null)
        return GamePreferencePolicy.resolveCore(name, GameSystem.findById(game.systemId).systemCoreConfigs.map { it.coreID })
    }

    fun setCoreOverride(game: Game, coreID: CoreID?): Boolean {
        require(coreID == null || GameSystem.findById(game.systemId).systemCoreConfigs.any { it.coreID == coreID })
        return putOverride(coreKey(game), coreID?.coreName)
    }

    fun getSystemProfileOverride(systemID: SystemID): PerformanceProfile? =
        GamePreferencePolicy.parseProfile(preferences.getString(systemProfileKey(systemID), null))

    fun setSystemProfileOverride(systemID: SystemID, profile: PerformanceProfile?): Boolean =
        putOverride(systemProfileKey(systemID), profile?.prefValue)

    fun getGameProfileOverride(game: Game): PerformanceProfile? =
        GamePreferencePolicy.parseProfile(preferences.getString(gameProfileKey(game), null))

    fun setGameProfileOverride(game: Game, profile: PerformanceProfile?): Boolean =
        putOverride(gameProfileKey(game), profile?.prefValue)

    fun getEffectiveProfile(game: Game, globalProfile: PerformanceProfile): PerformanceProfile =
        GamePreferencePolicy.resolveProfile(
            getGameProfileOverride(game),
            getSystemProfileOverride(SystemID.entries.first { it.dbname == game.systemId }),
            globalProfile,
        )

    private fun putOverride(key: String, value: String?): Boolean {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        return editor.commit()
    }

    companion object {
        fun coreKey(game: Game) = "pref_game_core_${GamePreferencePolicy.gameKey(game.systemId, game.fileUri)}"
        fun gameProfileKey(game: Game) = "pref_game_profile_${GamePreferencePolicy.gameKey(game.systemId, game.fileUri)}"
        fun systemProfileKey(systemID: SystemID) = "pref_system_profile_${systemID.dbname}"
    }
}

object GamePreferencePolicy {
    fun resolveCore(savedName: String?, available: List<CoreID>): CoreID? =
        available.firstOrNull { it.coreName == savedName }

    fun gameKey(systemId: String, fileUri: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(fileUri.toByteArray(Charsets.UTF_8))
        return systemId + "_" + hash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun parseProfile(value: String?): PerformanceProfile? = PerformanceProfile.entries.firstOrNull { it.prefValue == value }

    fun resolveProfile(game: PerformanceProfile?, system: PerformanceProfile?, global: PerformanceProfile): PerformanceProfile =
        game ?: system ?: global
}
