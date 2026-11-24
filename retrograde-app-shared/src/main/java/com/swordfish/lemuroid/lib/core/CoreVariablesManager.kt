package com.swordfish.lemuroid.lib.core

import android.content.SharedPreferences
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import com.swordfish.lemuroid.lib.preferences.PerformanceProfileManager
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.InvalidParameterException

class CoreVariablesManager(private val sharedPreferences: Lazy<SharedPreferences>) {
    suspend fun getOptionsForCore(
        systemID: SystemID,
        systemCoreConfig: SystemCoreConfig,
        performanceProfile: PerformanceProfile? = null,
    ): List<CoreVariable> {
        val profile = performanceProfile ?: PerformanceProfileManager.getProfileFromPreferences(sharedPreferences.get())
        val profileOverrides =
            PerformanceProfileManager.getCoreOverridesForProfile(
                profile,
                systemID,
                systemCoreConfig.coreID,
            )
        val coreVariables = retrieveCustomCoreVariables(systemID, systemCoreConfig)
        return mergeCoreOptions(systemCoreConfig.defaultSettings, profileOverrides, coreVariables)
    }

    private suspend fun retrieveCustomCoreVariables(
        systemID: SystemID,
        systemCoreConfig: SystemCoreConfig,
    ): List<CoreVariable> =
        withContext(Dispatchers.IO) {
            val exposedKeys = systemCoreConfig.exposedSettings
            val exposedAdvancedKeys = systemCoreConfig.exposedAdvancedSettings

            val requestedKeys =
                (exposedKeys + exposedAdvancedKeys).map { it.key }
                    .map { computeSharedPreferenceKey(it, systemID.dbname) }

            sharedPreferences.get().all.filter { it.key in requestedKeys }
                .map { (key, value) ->
                    val result =
                        when (value!!) {
                            is Boolean -> if (value as Boolean) "enabled" else "disabled"
                            is String -> value as String
                            else -> throw InvalidParameterException("Invalid setting in SharedPreferences")
                        }
                    CoreVariable(computeOriginalKey(key, systemID.dbname), result)
                }
        }

    companion object {
        private const val RETRO_OPTION_PREFIX = "cv"

        /** Later lists win: explicit player preferences always override profile budgets. */
        fun mergeCoreOptions(
            defaults: List<CoreVariable>,
            profile: List<CoreVariable>,
            manual: List<CoreVariable>,
        ): List<CoreVariable> =
            (defaults + profile + manual).associate { it.key to it.value }
                .map { (key, value) -> CoreVariable(key, value) }

        fun computeSharedPreferenceKey(
            retroVariableName: String,
            systemID: String,
        ): String {
            return "${computeSharedPreferencesPrefix(systemID)}$retroVariableName"
        }

        fun computeOriginalKey(
            sharedPreferencesKey: String,
            systemID: String,
        ): String {
            return sharedPreferencesKey.replace(computeSharedPreferencesPrefix(systemID), "")
        }

        private fun computeSharedPreferencesPrefix(systemID: String): String {
            return "${RETRO_OPTION_PREFIX}_${systemID}_"
        }
    }
}
