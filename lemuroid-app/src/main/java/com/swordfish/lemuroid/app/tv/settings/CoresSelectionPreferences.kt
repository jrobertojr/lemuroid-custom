package com.swordfish.lemuroid.app.tv.settings

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID

class CoresSelectionPreferences(private val coresSelection: CoresSelection) {
    fun addCoresSelectionPreferences(preferenceScreen: PreferenceScreen) {
        val context = preferenceScreen.context
        val systems = GameSystem.all().filter { it.systemCoreConfigs.size > 1 && !isArcade(it) }
        val preferences = systems.associateWith { createPreference(context, it) }
        preferenceScreen.addPreference(Preference(context).apply {
            order = -1
            title = context.getString(R.string.settings_core_selection_reset)
            summary = context.getString(R.string.settings_core_selection_reset_summary)
            isIconSpaceReserved = false
            setOnPreferenceClickListener {
                if (coresSelection.persistDefaults()) {
                    preferences.forEach { (system, preference) ->
                        preference.value = coresSelection.getCurrentCoreForSystem(system).coreName
                    }
                    LibraryIndexScheduler.scheduleCoreUpdate(context.applicationContext)
                }
                true
            }
        })
        preferences.forEach { (system, preference) ->
            preferenceScreen.addPreference(preference)
            // Attachment calls onSetInitialValue; restore the persisted value afterwards.
            preference.value = coresSelection.getCurrentCoreForSystem(system).coreName
        }
    }

    fun addArcadeCoresSelectionPreferences(preferenceScreen: PreferenceScreen) {
        GameSystem.all().filter { isArcade(it) }.forEach { system ->
            val preference = createPreference(preferenceScreen.context, system)
            preferenceScreen.addPreference(preference)
            preference.value = coresSelection.getCurrentCoreForSystem(system).coreName
        }
    }

    private fun isArcade(system: GameSystem) =
        system.id == SystemID.FBNEO || system.id == SystemID.MAME2003PLUS

    private fun createPreference(context: Context, system: GameSystem): ListPreference =
        ListPreference(context).apply {
            key = CoresSelection.computeSystemPreferenceKey(system.id)
            title = context.getString(system.titleResId)
            isIconSpaceReserved = false
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            // Persist explicitly below before scheduling the updater, instead of async DataStore.apply().
            isPersistent = false
            entries = system.systemCoreConfigs.map { it.coreID.coreDisplayName }.toTypedArray()
            entryValues = system.systemCoreConfigs.map { it.coreID.coreName }.toTypedArray()
            setOnPreferenceChangeListener { _, selected ->
                val core = system.systemCoreConfigs.firstOrNull { it.coreID.coreName == selected }?.coreID
                if (core != null && coresSelection.persistCoreConfigForSystem(system, core)) {
                    LibraryIndexScheduler.scheduleCoreUpdate(context.applicationContext)
                    true
                } else {
                    false
                }
            }
        }
}
