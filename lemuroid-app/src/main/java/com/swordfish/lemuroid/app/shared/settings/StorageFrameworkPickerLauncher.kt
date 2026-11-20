package com.swordfish.lemuroid.app.shared.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.utils.android.displayErrorDialog
import com.swordfish.lemuroid.lib.android.RetrogradeActivity
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.local.CacheMigrationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class StorageFrameworkPickerLauncher : RetrogradeActivity() {
    @Inject
    lateinit var directoriesManager: DirectoriesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val cacheKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_folder)
            val preferenceKey = intent.getStringExtra(EXTRA_PREFERENCE_KEY)
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    this.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (preferenceKey == cacheKey) {
                        this.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    this.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    this.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    this.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                }
            try {
                startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
            } catch (e: Exception) {
                showStorageAccessFrameworkNotSupportedDialog()
            }
        }
    }

    private fun showStorageAccessFrameworkNotSupportedDialog() {
        val message = getString(R.string.dialog_saf_not_found, directoriesManager.getInternalRomsDirectory())
        val actionLabel = getString(R.string.ok)
        displayErrorDialog(message, actionLabel) { finish() }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        resultData: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == Activity.RESULT_OK) {
            val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
            val romsKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
            val cacheKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_folder)
            val cacheEnabledKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_enabled)
            val preferenceKey = intent.getStringExtra(EXTRA_PREFERENCE_KEY) ?: romsKey

            val currentValue: String? = sharedPreferences.getString(preferenceKey, null)
            val newValue = resultData?.data

            if (newValue != null && newValue.toString() != currentValue) {
                updatePersistableUris(newValue, preferenceKey)

                sharedPreferences.edit().apply {
                    this.putString(preferenceKey, newValue.toString())
                    this.apply()
                }

                if (preferenceKey == cacheKey && sharedPreferences.getBoolean(cacheEnabledKey, false)) {
                    val appContext = applicationContext
                    CoroutineScope(Dispatchers.IO).launch {
                        CacheMigrationManager.migrateCache(appContext, toCustom = true)
                    }
                }
            }

            if (preferenceKey == romsKey) {
                startLibraryIndexWork()
            }
        }
        finish()
    }

    private fun updatePersistableUris(
        uri: Uri,
        updatedPreferenceKey: String,
    ) {
        val sharedPreferences = SharedPreferencesHelper.getLegacySharedPreferences(this)
        val romsKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_extenral_folder)
        val biosKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_bios_folder)
        val cacheKey = getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_folder)
        val allFolderKeys = listOf(romsKey, biosKey, cacheKey)

        val preservedUris =
            buildSet {
                add(uri)
                allFolderKeys
                    .filter { it != updatedPreferenceKey }
                    .mapNotNull { key ->
                        sharedPreferences.getString(key, null)?.let {
                            runCatching { Uri.parse(it) }.getOrNull()
                        }
                    }
                    .forEach { add(it) }
            }

        contentResolver.persistedUriPermissions
            .filter { it.uri !in preservedUris }
            .forEach { permission ->
                var releaseFlags = 0
                if (permission.isReadPermission) {
                    releaseFlags = releaseFlags or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                if (permission.isWritePermission) {
                    releaseFlags = releaseFlags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                }
                if (releaseFlags != 0) {
                    runCatching {
                        contentResolver.releasePersistableUriPermission(permission.uri, releaseFlags)
                    }
                }
            }

        val takeFlags =
            if (updatedPreferenceKey == cacheKey) {
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            } else {
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        runCatching {
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        }.onFailure {
            if (updatedPreferenceKey == cacheKey) {
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        }
    }

    private fun startLibraryIndexWork() {
        LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
    }

    companion object {
        private const val REQUEST_CODE_PICK_FOLDER = 1
        private const val EXTRA_PREFERENCE_KEY = "extra_preference_key"

        fun pickFolder(context: Context) {
            context.startActivity(Intent(context, StorageFrameworkPickerLauncher::class.java))
        }

        fun pickBiosFolder(context: Context) {
            val intent =
                Intent(context, StorageFrameworkPickerLauncher::class.java).apply {
                    putExtra(
                        EXTRA_PREFERENCE_KEY,
                        context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_external_bios_folder),
                    )
                }
            context.startActivity(intent)
        }

        fun pickCacheFolder(context: Context) {
            val intent =
                Intent(context, StorageFrameworkPickerLauncher::class.java).apply {
                    putExtra(
                        EXTRA_PREFERENCE_KEY,
                        context.getString(com.swordfish.lemuroid.lib.R.string.pref_key_custom_cache_folder),
                    )
                }
            context.startActivity(intent)
        }
    }
}
