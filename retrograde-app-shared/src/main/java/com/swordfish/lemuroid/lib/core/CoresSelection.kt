package com.swordfish.lemuroid.lib.core

import android.content.SharedPreferences
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.GamePreferencesManager
import com.swordfish.lemuroid.lib.migration.DesmumeMigrationHandler
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CoresSelection(
    private val sharedPreferencesFactory: Lazy<SharedPreferences>,
    private val desmumeMigrationHandler: DesmumeMigrationHandler,
) {
    private val sharedPreferences by lazy { sharedPreferencesFactory.get() }
    private val flowSharedPreferences by lazy { FlowSharedPreferences(sharedPreferences) }

    data class SelectedCore(val system: GameSystem, val coreConfig: SystemCoreConfig)

    fun getSelectedCores(): Flow<List<SelectedCore>> {
        val flows = GameSystem.all().filter { it.systemCoreConfigs.size > 1 }.map { system ->
            getSelectedCoreConfigForSystem(system).map { SelectedCore(system, it) }
        }
        return combine(flows) { it.toList() }
    }

    suspend fun updateCoreConfigForSystem(system: GameSystem, coreID: CoreID) = withContext(Dispatchers.IO) {
        check(persistCoreConfigForSystem(system, coreID)) { "Could not persist selected core" }
    }

    // TV preferences use this before scheduling the worker, so another process sees the same choice.
    fun persistCoreConfigForSystem(system: GameSystem, coreID: CoreID): Boolean {
        require(system.systemCoreConfigs.any { it.coreID == coreID })
        return sharedPreferences.edit()
            .putString(computeSystemPreferenceKey(system.id), coreID.coreName)
            .commit()
    }

    suspend fun resetToDefaults() = withContext(Dispatchers.IO) {
        check(persistDefaults()) { "Could not persist default cores" }
    }

    fun persistDefaults(): Boolean {
        val editor = sharedPreferences.edit()
        GameSystem.all().forEach { system ->
            editor.putString(computeSystemPreferenceKey(system.id), getDefaultCoreForSystem(system).coreName)
        }
        return editor.commit()
    }

    suspend fun getCoreConfigForSystem(system: GameSystem): SystemCoreConfig =
        getSelectedCoreConfigForSystem(system).first()

    suspend fun getCoreConfigForGame(game: Game): SystemCoreConfig {
        val system = GameSystem.findById(game.systemId)
        val override = GamePreferencesManager(sharedPreferences).getCoreOverride(game)
        return system.systemCoreConfigs.firstOrNull { it.coreID == override } ?: getCoreConfigForSystem(system)
    }

    fun getCurrentCoreForSystem(system: GameSystem): CoreID = resolveCore(
        system,
        sharedPreferences.getString(computeSystemPreferenceKey(system.id), null),
    )

    fun getDefaultCoreForSystem(system: GameSystem): CoreID =
        if (system.id == SystemID.NDS && desmumeMigrationHandler.hasPendingDesmumeSaves()) {
            CoreID.DESMUME
        } else {
            CoreSelectionDefaults.coreFor(system.id)
        }

    private fun resolveCore(system: GameSystem, savedName: String?) = CoreSelectionDefaults.resolve(
        savedName,
        system.systemCoreConfigs.map { it.coreID },
        getDefaultCoreForSystem(system),
    )

    private fun getSelectedCoreConfigForSystem(system: GameSystem): Flow<SystemCoreConfig> =
        // Reading must not write a default: a concurrent reader could overwrite a manual selection.
        flowSharedPreferences.getString(computeSystemPreferenceKey(system.id), "").asFlow()
            .map { name -> system.systemCoreConfigs.first { it.coreID == resolveCore(system, name) } }
            .flowOn(Dispatchers.IO)

    companion object {
        private const val CORE_SELECTION_BINDING_PREFERENCE_BASE_KEY = "pref_key_core_selection"
        fun computeSystemPreferenceKey(systemID: SystemID) =
            "${CORE_SELECTION_BINDING_PREFERENCE_BASE_KEY}_${systemID.dbname}"
    }
}
