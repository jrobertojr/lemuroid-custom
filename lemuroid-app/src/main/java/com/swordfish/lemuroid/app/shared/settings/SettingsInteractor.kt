package com.swordfish.lemuroid.app.shared.settings

import android.content.Context
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.storage.cache.CacheCleanerWork
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.local.CacheMigrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsInteractor(
    private val context: Context,
    private val directoriesManager: DirectoriesManager,
) {
    fun changeLocalStorageFolder() {
        StorageFrameworkPickerLauncher.pickFolder(context)
    }

    fun changeBiosFolder() {
        StorageFrameworkPickerLauncher.pickBiosFolder(context)
    }

    fun changeCacheFolder() {
        StorageFrameworkPickerLauncher.pickCacheFolder(context)
    }

    fun setCustomCacheEnabled(enabled: Boolean) {
        val key = context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_enabled)
        SharedPreferencesHelper.getLegacySharedPreferences(context)
            .edit()
            .putBoolean(key, enabled)
            .apply()
        SharedPreferencesHelper.getSharedPreferences(context)
            .edit()
            .putBoolean(key, enabled)
            .apply()

        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            CacheMigrationManager.migrateCache(appContext, toCustom = enabled)
        }
    }

    fun resetAllSettings() {
        SharedPreferencesHelper.getLegacySharedPreferences(context).edit().clear().apply()
        SharedPreferencesHelper.getSharedPreferences(context).edit().clear().apply()
        LibraryIndexScheduler.scheduleLibrarySync(context.applicationContext)
        CacheCleanerWork.enqueueCleanCacheAll(context.applicationContext)
        deleteDownloadedCores()
    }

    private fun deleteDownloadedCores() {
        directoriesManager.getCoresDirectory()
            .listFiles()
            ?.forEach { runCatching { it.deleteRecursively() } }
    }
}
