package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    context: Context,
    private val settingsInteractor: SettingsInteractor,
    saveSyncManager: SaveSyncManager,
    sharedPreferences: FlowSharedPreferences,
) : ViewModel() {
    class Factory(
        private val context: Context,
        private val settingsInteractor: SettingsInteractor,
        private val saveSyncManager: SaveSyncManager,
        private val sharedPreferences: FlowSharedPreferences,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                context,
                settingsInteractor,
                saveSyncManager,
                sharedPreferences,
            ) as T
        }
    }

    data class State(
        val currentDirectory: String = "",
        val currentBiosDirectory: String = "",
        val currentCacheDirectory: String = "",
        val customCacheEnabled: Boolean = false,
        val isSaveSyncSupported: Boolean = false,
    )

    val indexingInProgress = PendingOperationsMonitor(context).anyLibraryOperationInProgress()

    val directoryScanInProgress = PendingOperationsMonitor(context).isDirectoryScanInProgress()

    private val romsDirectoryFlow =
        sharedPreferences.getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder))
            .asFlow()
            .flowOn(Dispatchers.IO)

    private val biosDirectoryFlow =
        sharedPreferences.getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_bios_folder))
            .asFlow()
            .flowOn(Dispatchers.IO)

    private val cacheDirectoryFlow =
        sharedPreferences.getString(context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_folder))
            .asFlow()
            .flowOn(Dispatchers.IO)

    private val customCacheEnabledFlow =
        sharedPreferences.getBoolean(
            context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_enabled),
            false,
        )
            .asFlow()
            .flowOn(Dispatchers.IO)

    val uiState =
        combine(
            romsDirectoryFlow,
            biosDirectoryFlow,
            cacheDirectoryFlow,
            customCacheEnabledFlow,
        ) { romsDir, biosDir, cacheDir, customCacheEnabled ->
            State(
                currentDirectory = romsDir,
                currentBiosDirectory = biosDir,
                currentCacheDirectory = cacheDir,
                customCacheEnabled = customCacheEnabled,
                isSaveSyncSupported = saveSyncManager.isSupported(),
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, State())

    fun changeLocalStorageFolder() {
        settingsInteractor.changeLocalStorageFolder()
    }

    fun changeBiosFolder() {
        settingsInteractor.changeBiosFolder()
    }

    fun changeCacheFolder() {
        settingsInteractor.changeCacheFolder()
    }

    fun setCustomCacheEnabled(enabled: Boolean) {
        settingsInteractor.setCustomCacheEnabled(enabled)
    }
}
