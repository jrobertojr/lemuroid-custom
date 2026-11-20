package com.swordfish.lemuroid.app.tv.settings

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.InputDevice
import android.view.View
import androidx.activity.addCallback
import androidx.documentfile.provider.DocumentFile
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.leanback.preference.LeanbackSettingsFragmentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceFragmentCompat
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SaveSyncPreferences
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.common.kotlin.NTuple2
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import com.swordfish.lemuroid.lib.preferences.PerformanceProfileManager
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class TVSettingsFragment : LeanbackPreferenceFragmentCompat() {
    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var biosPreferences: BiosPreferences

    @Inject
    lateinit var gamePadPreferencesHelper: GamePadPreferencesHelper

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var coresSelectionPreferences: CoresSelectionPreferences

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    lateinit var saveSyncPreferences: SaveSyncPreferences

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)

        saveSyncPreferences = SaveSyncPreferences(saveSyncManager)

        super.onAttach(context)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        // The Arcade child screen returns to core selection, keeping the TV remote flow local.
        val wrapper = parentFragment as? LeanbackSettingsFragmentCompat
        if (wrapper != null && arguments?.getString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT) ==
            getString(R.string.pref_key_arcade_core_selection)
        ) {
            val coreSelectionKey = getString(R.string.pref_key_open_cores_selection)
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                wrapper.startPreferenceFragment(TVSettingsFragment().apply {
                    arguments = Bundle().apply {
                        putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, coreSelectionKey)
                    }
                })
            }
        }

        launchOnState(Lifecycle.State.CREATED) {
            val gamePadStatus =
                combine(
                    inputDeviceManager.getGamePadsObservable(),
                    inputDeviceManager.getEnabledInputsObservable(),
                    ::NTuple2,
                )

            gamePadStatus
                .distinctUntilChanged()
                .collect { (pads, enabledPads) -> addGamePadBindingsScreen(pads, enabledPads) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            inputDeviceManager.getEnabledInputsObservable()
                .distinctUntilChanged()
                .collect { refreshGamePadBindingsScreen(it) }
        }

        launchOnState(Lifecycle.State.RESUMED) {
            getSaveSyncScreen()?.let { screen ->
                PendingOperationsMonitor(requireContext())
                    .anySaveOperationInProgress()
                    .safeCollect { syncInProgress ->
                        saveSyncPreferences.updatePreferences(screen, syncInProgress)
                    }
            }
        }
    }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        runCatching { PerformanceProfileManager.ensureInitialized(requireContext()) }
        preferenceManager.preferenceDataStore =
            SharedPreferencesHelper.getSharedPreferencesDataStore(requireContext())
        setPreferencesFromResource(R.xml.tv_settings, rootKey)

        findPreference<ListPreference>(PerformanceProfileManager.PREF_KEY_PERFORMANCE_PROFILE)?.let { pref ->
            if (pref.value.isNullOrEmpty()) {
                pref.value =
                    when (PerformanceProfileManager.getActiveProfile(requireContext())) {
                        PerformanceProfile.QUALITY -> "quality"
                        PerformanceProfile.BALANCED -> "balanced"
                        PerformanceProfile.PERFORMANCE -> "performance"
                    }
            }
        }

        getCoresSelectionScreen()?.let {
            coresSelectionPreferences.addCoresSelectionPreferences(it)
        }

        findPreference<PreferenceScreen>(getString(R.string.pref_key_arcade_core_selection))?.let {
            coresSelectionPreferences.addArcadeCoresSelectionPreferences(it)
        }

        findPreference<Preference>("system_performance_profiles")?.setOnPreferenceClickListener {
            startActivity(android.content.Intent(requireContext(), com.swordfish.lemuroid.app.shared.gamepreferences.SystemProfilesActivity::class.java))
            true
        }

        getBiosInfoPreferenceScreen()?.let {
            biosPreferences.addBiosPreferences(it)
        }

        getAdvancedSettingsPreferenceScreen()?.let {
            AdvancedSettingsPreferences.updateCachePreferences(it)
        }

        getSaveSyncScreen()?.let {
            if (saveSyncManager.isSupported()) {
                saveSyncPreferences.addSaveSyncPreferences(it)
            }
            it.isVisible = saveSyncManager.isSupported()
        }

        val customCacheEnabledKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_enabled)
        findPreference<Preference>(customCacheEnabledKey)?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as? Boolean ?: false
            settingsInteractor.setCustomCacheEnabled(enabled)
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSaveSyncScreen()
        refreshBiosFolderSummary()
        refreshCacheFolderSummary()
    }

    private fun refreshBiosFolderSummary() {
        val biosKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_bios_folder)
        val pref = findPreference<Preference>(biosKey) ?: return
        val folderUri =
            SharedPreferencesHelper
                .getLegacySharedPreferences(requireContext())
                .getString(biosKey, null)
        val folderName =
            folderUri?.let { uri ->
                runCatching { DocumentFile.fromTreeUri(requireContext(), Uri.parse(uri))?.name }.getOrNull()
            } ?: getString(R.string.none)
        pref.summary = folderName
    }

    private fun refreshCacheFolderSummary() {
        val cacheKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_folder)
        val pref = findPreference<Preference>(cacheKey) ?: return
        val folderUri =
            SharedPreferencesHelper
                .getLegacySharedPreferences(requireContext())
                .getString(cacheKey, null)
        val folderName =
            folderUri?.let { uri ->
                runCatching { DocumentFile.fromTreeUri(requireContext(), Uri.parse(uri))?.name }.getOrNull()
            } ?: getString(R.string.none)
        pref.summary = folderName
    }

    private fun getGamePadPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_gamepad_settings))
    }

    private fun getSaveSyncScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_save_sync_settings))
    }

    private fun getCoresSelectionScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_open_cores_selection))
    }

    private fun getBiosInfoPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_display_bios_info))
    }

    private fun getAdvancedSettingsPreferenceScreen(): PreferenceScreen? {
        return findPreference(resources.getString(R.string.pref_key_advanced_settings))
    }

    private fun addGamePadBindingsScreen(
        gamePads: List<InputDevice>,
        enabledGamePads: List<InputDevice>,
    ) {
        lifecycleScope.launch {
            getGamePadPreferenceScreen()?.let {
                it.removeAll()
                gamePadPreferencesHelper.addGamePadsPreferencesToScreen(
                    it.context,
                    it,
                    gamePads,
                    enabledGamePads,
                )
            }
        }
    }

    private fun refreshGamePadBindingsScreen(enabledGamePads: List<InputDevice>) {
        lifecycleScope.launch {
            getGamePadPreferenceScreen()?.let {
                gamePadPreferencesHelper.refreshGamePadsPreferencesToScreen(it, enabledGamePads)
            }
        }
    }

    private fun refreshSaveSyncScreen() {
        getSaveSyncScreen()?.let {
            saveSyncPreferences.updatePreferences(it, false)
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (saveSyncPreferences.onPreferenceTreeClick(activity, preference)) {
            return true
        }

        when (preference.key) {
            getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_bios_folder) -> {
                settingsInteractor.changeBiosFolder()
                return true
            }
            getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_folder) -> {
                settingsInteractor.changeCacheFolder()
                return true
            }
            getString(R.string.pref_key_reset_gamepad_bindings) ->
                lifecycleScope.launch {
                    handleResetGamePadBindings()
                }
            getString(R.string.pref_key_reset_settings) -> handleResetSettings()
        }
        return super.onPreferenceTreeClick(preference)
    }

    private suspend fun handleResetGamePadBindings() {
        inputDeviceManager.resetAllBindings()
        refreshGamePadBindingsScreen(inputDeviceManager.getGamePadsObservable().first())
    }

    private fun handleResetSettings() {
        settingsInteractor.resetAllSettings()
        activity?.finish()
    }

    @dagger.Module
    class Module
}
