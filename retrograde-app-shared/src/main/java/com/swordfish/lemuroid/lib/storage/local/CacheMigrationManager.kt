package com.swordfish.lemuroid.lib.storage.local

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import timber.log.Timber
import java.io.File

object CacheMigrationManager {
    private const val STORAGE_FRAMEWORK_GAMES_DIR = "storage-framework-games"
    private val migrationLock = Any()

    fun resolveWritableDirectoryFromTreeUri(uriString: String?): File? {
        if (uriString.isNullOrBlank()) {
            return null
        }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val candidateDir =
            when {
                uri.scheme.equals("file", ignoreCase = true) -> {
                    uri.path?.let { File(it) }
                }
                else -> {
                    val rawDocId =
                        runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                            ?: uri.lastPathSegment
                    val docId = rawDocId?.let { Uri.decode(it) }
                    resolveFileFromDocumentId(docId)
                        ?: uri.path?.takeIf { it.startsWith("/storage/") }?.let { File(it) }
                }
            } ?: return null

        return runCatching {
            if (!candidateDir.exists()) {
                candidateDir.mkdirs()
            }
            if (candidateDir.exists() && candidateDir.isDirectory && candidateDir.canWrite()) {
                candidateDir
            } else {
                null
            }
        }.getOrNull()
    }

    private fun resolveFileFromDocumentId(docId: String?): File? {
        if (docId.isNullOrBlank()) {
            return null
        }

        return when {
            docId.startsWith("/") -> File(docId)
            docId.startsWith("raw:") -> File(docId.removePrefix("raw:"))
            docId.contains(":") -> {
                val volumeId = docId.substringBefore(':')
                val relativePath = docId.substringAfter(':').trimStart('/')
                val baseDir =
                    when {
                        volumeId.equals("primary", ignoreCase = true) -> {
                            Environment.getExternalStorageDirectory()
                        }
                        volumeId.equals("home", ignoreCase = true) -> {
                            File(Environment.getExternalStorageDirectory(), "Documents")
                        }
                        else -> {
                            File("/storage", volumeId)
                        }
                    }
                if (relativePath.isBlank()) baseDir else File(baseDir, relativePath)
            }
            else -> null
        }
    }

    fun getActiveCacheRoot(context: Context): File {
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val enabledKey = context.getString(R.string.pref_key_custom_cache_enabled)
        val folderKey = context.getString(R.string.pref_key_custom_cache_folder)

        if (prefs.getBoolean(enabledKey, false)) {
            val customUri = prefs.getString(folderKey, null)
            val customDir = resolveWritableDirectoryFromTreeUri(customUri)
            if (customDir != null) {
                return customDir
            }
        }
        return context.cacheDir
    }

    fun migrateCache(
        context: Context,
        toCustom: Boolean,
    ) {
        synchronized(migrationLock) {
            runCatching {
                val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
                val folderKey = context.getString(R.string.pref_key_custom_cache_folder)
                val customDir =
                    resolveWritableDirectoryFromTreeUri(prefs.getString(folderKey, null))
                        ?: return

                val defaultDir = context.cacheDir
                val sourceRoot = if (toCustom) defaultDir else customDir
                val targetRoot = if (toCustom) customDir else defaultDir

                if (sourceRoot.canonicalPath == targetRoot.canonicalPath) {
                    return
                }

                val sourceDir = File(sourceRoot, STORAGE_FRAMEWORK_GAMES_DIR)
                if (!sourceDir.exists() || !sourceDir.isDirectory) {
                    return
                }

                val targetDir = File(targetRoot, STORAGE_FRAMEWORK_GAMES_DIR)
                sourceDir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { sourceFile ->
                        val relativePath = sourceFile.relativeTo(sourceDir).path
                        val destFile = File(targetDir, relativePath)
                        destFile.parentFile?.mkdirs()

                        val copied =
                            runCatching {
                                sourceFile.copyTo(destFile, overwrite = true)
                                destFile.exists() && destFile.length() == sourceFile.length()
                            }.getOrElse { error ->
                                Timber.e(error, "Failed to copy cached file %s", sourceFile.absolutePath)
                                false
                            }

                        if (copied) {
                            runCatching { sourceFile.delete() }
                        }
                    }

                sourceDir.walkBottomUp()
                    .filter { it.isDirectory }
                    .forEach { dir ->
                        if (dir.listFiles().isNullOrEmpty()) {
                            runCatching { dir.delete() }
                        }
                    }
            }.onFailure { error ->
                Timber.e(error, "Failed to migrate cache (toCustom=%s)", toCustom)
            }
        }
    }
}
