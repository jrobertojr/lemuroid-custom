package com.swordfish.lemuroid.lib.bios

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.swordfish.lemuroid.common.files.safeDelete
import com.swordfish.lemuroid.common.kotlin.associateByNotNull
import com.swordfish.lemuroid.common.kotlin.writeToFile
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.StorageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.Locale

class BiosManager(private val directoriesManager: DirectoriesManager) {
    private val crcLookup = SUPPORTED_BIOS.associateByNotNull { it.externalCRC32 }
    private val nameLookup = SUPPORTED_BIOS.associateByNotNull { it.externalName }

    fun getMissingBiosFiles(
        coreConfig: SystemCoreConfig,
        game: Game,
    ): List<String> {
        val regionalBiosFiles = coreConfig.regionalBIOSFiles

        val gameLabels =
            Regex("\\([A-Za-z]+\\)")
                .findAll(game.title)
                .map { it.value.drop(1).dropLast(1) }
                .filter { it.isNotBlank() }
                .toSet()

        Timber.d("Found game labels: $gameLabels")

        val requiredRegionalFiles =
            gameLabels.intersect(regionalBiosFiles.keys)
                .ifEmpty { regionalBiosFiles.keys }
                .mapNotNull { regionalBiosFiles[it] }

        Timber.d("Required regional files for game: $requiredRegionalFiles")

        return (coreConfig.requiredBIOSFiles + requiredRegionalFiles)
            .filter { !File(directoriesManager.getSystemDirectory(), it).exists() }
    }

    fun deleteBiosBefore(timestampMs: Long) {
        Timber.i("Pruning old bios files")
        SUPPORTED_BIOS
            .map { File(directoriesManager.getSystemDirectory(), it.libretroFileName) }
            .filter { it.lastModified() < normalizeTimestamp(timestampMs) }
            .forEach {
                Timber.d("Pruning old bios file: ${it.path}")
                it.safeDelete()
            }
    }

    @Deprecated("Use the suspend variant")
    fun getBiosInfo(): BiosInfo {
        val bios =
            SUPPORTED_BIOS.groupBy {
                File(directoriesManager.getSystemDirectory(), it.libretroFileName).exists()
            }.withDefault { listOf() }

        return BiosInfo(bios.getValue(true), bios.getValue(false))
    }

    suspend fun getBiosInfoAsync(): BiosInfo =
        withContext(Dispatchers.IO) {
            getBiosInfo()
        }

    fun tryAddBiosAfter(
        storageFile: StorageFile,
        inputStream: InputStream,
        timestampMs: Long,
    ): Boolean {
        val bios = findByCRC(storageFile) ?: findByName(storageFile) ?: return false

        Timber.i("Importing bios file: $bios")

        val biosFile = File(directoriesManager.getSystemDirectory(), bios.libretroFileName)
        if (biosFile.exists() && biosFile.setLastModified(normalizeTimestamp(timestampMs))) {
            Timber.d("Bios file already present. Updated last modification date.")
        } else {
            Timber.d("Bios file not available. Copying new file.")
            inputStream.writeToFile(biosFile)
        }
        return true
    }

    private fun findByCRC(storageFile: StorageFile): Bios? {
        return crcLookup[storageFile.crc]
    }

    private fun findByName(storageFile: StorageFile): Bios? {
        return nameLookup[storageFile.name]
    }

    private fun normalizeTimestamp(timestamp: Long) = (timestamp / 1000) * 1000

    suspend fun resolveMissingBiosFromCustomFolder(
        context: Context,
        missingFiles: List<String>,
    ) = withContext(Dispatchers.IO) {
        if (missingFiles.isEmpty()) return@withContext
        val treeUri = getCustomBiosFolderUri(context) ?: return@withContext

        val targetByLowerName = mutableMapOf<String, String>()
        for (missing in missingFiles) {
            targetByLowerName[missing.lowercase(Locale.ROOT)] = missing
            SUPPORTED_BIOS
                .firstOrNull { it.libretroFileName.equals(missing, ignoreCase = true) }
                ?.let { bios ->
                    bios.externalName?.let { extName ->
                        targetByLowerName[extName.lowercase(Locale.ROOT)] = bios.libretroFileName
                    }
                }
        }

        copyMatchingBiosFromSafTree(context, treeUri, targetByLowerName)
    }

    suspend fun syncArcadeBiosFromCustomFolder(context: Context) =
        withContext(Dispatchers.IO) {
            val treeUri = getCustomBiosFolderUri(context) ?: return@withContext
            val systemDir = directoriesManager.getSystemDirectory()
            val targetByLowerName =
                ARCADE_BIOS_FILES
                    .filter { !File(systemDir, it).exists() }
                    .associateBy { it.lowercase(Locale.ROOT) }

            if (targetByLowerName.isEmpty()) return@withContext
            copyMatchingBiosFromSafTree(context, treeUri, targetByLowerName)
        }

    private fun getCustomBiosFolderUri(context: Context): Uri? {
        val prefKey = context.getString(R.string.pref_key_external_bios_folder)
        val prefs = SharedPreferencesHelper.getLegacySharedPreferences(context)
        val folderString = prefs.getString(prefKey, null) ?: return null
        return runCatching { Uri.parse(folderString) }.getOrNull()
    }

    private fun copyMatchingBiosFromSafTree(
        context: Context,
        treeUri: Uri,
        targetByLowerName: Map<String, String>,
    ) {
        val remaining = targetByLowerName.toMutableMap()
        val directoryDocumentIds = mutableListOf<String>()
        DocumentsContract.getTreeDocumentId(treeUri)?.let { directoryDocumentIds.add(it) }

        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )

        val systemDir = directoriesManager.getSystemDirectory()

        while (directoryDocumentIds.isNotEmpty() && remaining.isNotEmpty()) {
            val currentDocId = directoryDocumentIds.removeAt(0)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, currentDocId)

            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext() && remaining.isNotEmpty()) {
                        val docId = cursor.getString(0) ?: continue
                        val displayName = cursor.getString(1) ?: continue
                        val mimeType = cursor.getString(2)

                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                            directoryDocumentIds.add(docId)
                        } else {
                            val lowerName = displayName.lowercase(Locale.ROOT)
                            val destFileName = remaining.remove(lowerName) ?: continue
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val destFile = File(systemDir, destFileName)
                            runCatching {
                                context.contentResolver.openInputStream(docUri)?.use { input ->
                                    input.writeToFile(destFile)
                                    Timber.i("Copied BIOS from custom folder: $displayName -> ${destFile.path}")
                                }
                            }.onFailure { e ->
                                Timber.e(e, "Failed to copy BIOS $displayName from custom folder")
                            }
                        }
                    }
                }
            }.onFailure { e ->
                Timber.e(e, "Failed to traverse custom BIOS folder: $childrenUri")
            }
        }
    }

    data class BiosInfo(val detected: List<Bios>, val notDetected: List<Bios>)

    companion object {
        private val ARCADE_BIOS_FILES =
            listOf(
                "neogeo.zip",
                "pgm.zip",
                "skns.zip",
                "decocass.zip",
                "isgsm.zip",
                "midssio.zip",
                "nmk004.zip",
                "bubsys.zip",
                "cchip.zip",
                "namcoc69.zip",
                "namcoc70.zip",
                "namcoc75.zip",
                "qsound.zip",
                "ym2608.zip",
                "hiscore.dat",
            )
        private val SUPPORTED_BIOS =
            listOf(
                Bios(
                    "scph101.bin",
                    "6E3735FF4C7DC899EE98981385F6F3D0",
                    "PS One 4.5 NTSC-U/C",
                    SystemID.PSX,
                    "171BDCEC",
                ),
                Bios(
                    "scph7001.bin",
                    "1E68C231D0896B7EADCAD1D7D8E76129",
                    "PS Original 4.1 NTSC-U/C",
                    SystemID.PSX,
                    "502224B6",
                ),
                Bios(
                    "scph5501.bin",
                    "490F666E1AFB15B7362B406ED1CEA246",
                    "PS Original 3.0 NTSC-U/C",
                    SystemID.PSX,
                    "8D8CB7E4",
                ),
                Bios(
                    "scph1001.bin",
                    "924E392ED05558FFDB115408C263DCCF",
                    "PS Original 2.2 NTSC-U/C",
                    SystemID.PSX,
                    "37157331",
                ),
                Bios(
                    "lynxboot.img",
                    "FCD403DB69F54290B51035D82F835E7B",
                    "Lynx Boot Image",
                    SystemID.LYNX,
                    "0D973C9D",
                ),
                Bios(
                    "bios_CD_E.bin",
                    "E66FA1DC5820D254611FDCDBA0662372",
                    "Sega CD E",
                    SystemID.SEGACD,
                    "529AC15A",
                ),
                Bios(
                    "bios_CD_J.bin",
                    "278A9397D192149E84E820AC621A8EDD",
                    "Sega CD J",
                    SystemID.SEGACD,
                    "9D2DA8F2",
                ),
                Bios(
                    "bios_CD_U.bin",
                    "2EFD74E3232FF260E371B99F84024F7F",
                    "Sega CD U",
                    SystemID.SEGACD,
                    "C6D10268",
                ),
                Bios(
                    "bios7.bin",
                    "DF692A80A5B1BC90728BC3DFC76CD948",
                    "Nintendo DS ARM7",
                    SystemID.NDS,
                    "1280F0D5",
                ),
                Bios(
                    "bios9.bin",
                    "A392174EB3E572FED6447E956BDE4B25",
                    "Nintendo DS ARM9",
                    SystemID.NDS,
                    "2AB23573",
                ),
                Bios(
                    "firmware.bin",
                    "E45033D9B0FA6B0DE071292BBA7C9D13",
                    "Nintendo DS Firmware",
                    SystemID.NDS,
                    "945F9DC9",
                    "nds_firmware.bin",
                ),
            )
    }
}
