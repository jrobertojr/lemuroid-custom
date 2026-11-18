package com.swordfish.lemuroid.metadata.libretrodb

import com.swordfish.lemuroid.common.kotlin.filterNullable
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDBManager
import com.swordfish.lemuroid.metadata.libretrodb.db.LibretroDatabase
import com.swordfish.lemuroid.metadata.libretrodb.db.entity.LibretroRom
import timber.log.Timber
import java.util.Locale

class LibretroDBMetadataProvider(private val ovgdbManager: LibretroDBManager) :
    GameMetadataProvider {
    companion object {
        private val THUMB_REPLACE = Regex("[&*/:`<>?\\\\|]")
        private val PARENS_OR_BRACKETS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
        private val DISC_SUFFIX = Regex("\\s+(?:CD|Disc|Disk)\\s*\\d+.*$", RegexOption.IGNORE_CASE)
        private val VERSION_SUFFIX = Regex("\\s+v\\d+(?:\\.\\d+)*$", RegexOption.IGNORE_CASE)
        private val LEADING_ARTICLE = Regex("^(?:The|A|An)\\s+", RegexOption.IGNORE_CASE)
        private val TRAILING_ARTICLE = Regex(",\\s*(?:The|A|An)\\b", RegexOption.IGNORE_CASE)
        private val EXTRA_TAG_WORDS = setOf("ultimate", "pt", "br", "unl", "hack", "remastered")
        private val STOP_WORDS = setOf("the", "and", "for", "of")
        private val TOKEN_NORMALIZATIONS =
            mapOf(
                "ii" to "2",
                "iii" to "3",
                "iv" to "4",
                "vi" to "6",
                "vii" to "7",
                "viii" to "8",
                "ix" to "9",
                "bros" to "brothers",
                "vs" to "versus",
            )
        private val PENALTY_TAGS =
            listOf(
                "(Beta",
                "(Proto",
                "(Demo",
                "(Sample",
                "(Debug",
                "(LodgeNet",
                "(Virtual Console",
                "(Switch Online",
                "(GameCube",
                "(Aftermarket",
                "(Unl)",
                "(Pirate",
                "(Alt",
                "[tr ",
                "[T-",
                "[h",
                "[t ",
            )
        private val SYSTEM_FOLDER_ALIASES =
            mapOf(
                SystemID.GENESIS.dbname to listOf("mega", "genesis", "megadrive"),
                SystemID.FBNEO.dbname to listOf("arcade"),
                SystemID.MAME2003PLUS.dbname to listOf("arcade"),
            )
    }

    private val sortedSystemIds: List<String> by lazy {
        SystemID.values()
            .map { it.dbname }
            .sortedByDescending { it.length }
    }

    override suspend fun retrieveMetadata(storageFile: StorageFile): GameMetadata? {
        val db = ovgdbManager.dbInstance

        Timber.d("Looking metadata for file: $storageFile")

        val metadata =
            runCatching {
                findByCRC(storageFile, db)
                    ?: findBySerial(storageFile, db)
                    ?: findByFilename(db, storageFile)
                    ?: findByPathAndFilename(db, storageFile)
                    ?: findByUniqueExtension(db, storageFile)
                    ?: findByKnownSystem(db, storageFile)
                    ?: findByPathAndSupportedExtension(db, storageFile)
            }.getOrElse {
                Timber.e("Error in retrieving $storageFile metadata: $it... Skipping.")
                null
            }

        metadata?.let { Timber.d("Metadata retrieved for item: $it") }

        return metadata
    }

    private fun convertToGameMetadata(rom: LibretroRom): GameMetadata {
        val system = GameSystem.findById(rom.system!!)
        return GameMetadata(
            name = rom.name,
            romName = rom.romName,
            thumbnail = computeCoverUrl(system, rom.name),
            system = rom.system,
            developer = rom.developer,
        )
    }

    private suspend fun findByFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByFilename }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByPathAndFilename(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        return db.gameDao().findByFileName(file.name)
            .filterNullable { extractGameSystem(it).scanOptions.scanByPathAndFilename }
            .filterNullable { parentContainsSystem(file.path, extractGameSystem(it).id.dbname) }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByPathAndSupportedExtension(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        val system =
            sortedSystemIds
                .filter { parentContainsSystem(file.path, it) }
                .map { GameSystem.findById(it) }
                .filter { it.scanOptions.scanByPathAndSupportedExtensions }
                .firstOrNull { it.supportedExtensions.contains(file.extension) }

        return system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = findFallbackCoverUrl(db, it, file.extensionlessName),
                system = it.id.dbname,
                developer = null,
            )
        }
    }

    private fun parentContainsSystem(
        parent: String?,
        dbname: String,
    ): Boolean {
        val lowerPath = parent?.lowercase(Locale.getDefault()) ?: return false
        if (lowerPath.contains(dbname)) return true

        val directoryPath = lowerPath.substringBeforeLast('/', "")
        return SYSTEM_FOLDER_ALIASES[dbname].orEmpty().any { alias ->
            directoryPath.contains(alias)
        }
    }

    private suspend fun findByCRC(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.crc == null || file.crc == "0") return null
        return file.crc?.let { crc32 -> db.gameDao().findByCRC(crc32) }
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findBySerial(
        file: StorageFile,
        db: LibretroDatabase,
    ): GameMetadata? {
        if (file.serial == null) return null
        return db.gameDao().findBySerial(file.serial!!)
            ?.let { convertToGameMetadata(it) }
    }

    private suspend fun findByKnownSystem(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        val systemId = file.systemID ?: return null
        val system = GameSystem.findById(systemId.dbname)

        return GameMetadata(
            name = file.extensionlessName,
            romName = file.name,
            thumbnail = findFallbackCoverUrl(db, system, file.extensionlessName),
            system = systemId.dbname,
            developer = null,
        )
    }

    private suspend fun findByUniqueExtension(
        db: LibretroDatabase,
        file: StorageFile,
    ): GameMetadata? {
        val system = GameSystem.findByUniqueFileExtension(file.extension)

        if (system?.scanOptions?.scanByUniqueExtension == false) {
            return null
        }

        return system?.let {
            GameMetadata(
                name = file.extensionlessName,
                romName = file.name,
                thumbnail = findFallbackCoverUrl(db, it, file.extensionlessName),
                system = it.id.dbname,
                developer = null,
            )
        }
    }

    private suspend fun findFallbackCoverUrl(
        db: LibretroDatabase,
        system: GameSystem,
        rawTitle: String,
    ): String? {
        val cleaned = cleanTitleForSearch(rawTitle)
        if (cleaned.isEmpty()) {
            return computeCoverUrl(system, rawTitle)
        }

        val queryTokens = tokenizeTitle(cleaned)
        if (queryTokens.isEmpty()) {
            return computeCoverUrl(system, rawTitle)
        }

        val patterns = linkedSetOf<String>()
        patterns.add("$cleaned%")

        val subtitle = cleaned.substringAfter(" - ", "").substringAfter(": ", "").trim()
        val subtitleTokens = tokenizeTitle(subtitle)
        if (subtitleTokens.size >= 2) {
            patterns.add(subtitleTokens.take(3).joinToString("%", prefix = "%", postfix = "%"))
        }

        if (queryTokens.size >= 2) {
            patterns.add("%${queryTokens.first()}%${queryTokens.last()}%")
            patterns.add(queryTokens.take(3).joinToString("%", prefix = "%", postfix = "%"))
        }

        val longestToken = queryTokens.maxByOrNull { it.length }
        if (longestToken != null && longestToken.length >= 5) {
            patterns.add("%$longestToken%")
        }

        val candidates = linkedMapOf<Int, LibretroRom>()
        for (pattern in patterns) {
            val found = db.gameDao().findCandidatesBySystemAndNameLike(system.id.dbname, pattern)
            for (rom in found) {
                candidates.putIfAbsent(rom.id, rom)
            }
            if (candidates.size >= 40) break
        }

        val bestMatch =
            candidates.values
                .map { rom -> rom to scoreCandidate(queryTokens, rom.name.orEmpty()) }
                .filter { (_, score) -> score > 0 }
                .maxByOrNull { (_, score) -> score }
                ?.first

        return if (bestMatch?.name != null) {
            computeCoverUrl(system, bestMatch.name)
        } else {
            computeCoverUrl(system, rawTitle)
        }
    }

    private fun cleanTitleForSearch(rawTitle: String): String {
        return rawTitle
            .replace(PARENS_OR_BRACKETS, "")
            .replace(DISC_SUFFIX, "")
            .replace(VERSION_SUFFIX, "")
            .replace(TRAILING_ARTICLE, "")
            .replace(LEADING_ARTICLE, "")
            .trim(' ', '-', '_')
    }

    private fun tokenizeTitle(title: String): List<String> {
        return title
            .lowercase(Locale.ROOT)
            .split(Regex("[^a-z0-9]+"))
            .map { TOKEN_NORMALIZATIONS[it] ?: it }
            .filter {
                (it.length >= 2 || (it.isNotEmpty() && it.all { ch -> ch.isDigit() })) &&
                    it !in STOP_WORDS &&
                    it !in EXTRA_TAG_WORDS
            }
    }

    private fun scoreCandidate(
        queryTokens: List<String>,
        candidateName: String,
    ): Int {
        if (candidateName.isBlank()) return Int.MIN_VALUE

        val candidateTokens = tokenizeTitle(cleanTitleForSearch(candidateName))
        if (candidateTokens.isEmpty()) return Int.MIN_VALUE

        val querySet = queryTokens.toSet()
        val candidateSet = candidateTokens.toSet()
        val matchedCount = querySet.count { it in candidateSet }
        if (matchedCount == 0) return Int.MIN_VALUE

        val extraTokens = (candidateSet - querySet).size
        var score = (matchedCount * 100) / querySet.size - (extraTokens * 12)

        score +=
            when {
                candidateName.contains("(USA", ignoreCase = true) -> 30
                candidateName.contains("(World", ignoreCase = true) -> 28
                candidateName.contains("(Europe", ignoreCase = true) -> 25
                candidateName.contains("(Brazil", ignoreCase = true) -> 20
                candidateName.contains("(Japan", ignoreCase = true) -> 5
                else -> 0
            }

        for (tag in PENALTY_TAGS) {
            if (candidateName.contains(tag, ignoreCase = true)) {
                score -= 40
            }
        }

        return score
    }

    private fun extractGameSystem(rom: LibretroRom): GameSystem {
        return GameSystem.findById(rom.system!!)
    }

    private fun computeCoverUrl(
        system: GameSystem,
        name: String?,
    ): String? {
        var systemName = system.libretroFullName

        // Specific mame version don't have any thumbnails in Libretro database
        if (system.id == SystemID.MAME2003PLUS) {
            systemName = "MAME"
        }

        if (name == null) {
            return null
        }

        val imageType = "Named_Boxarts"

        val thumbGameName = name.replace(THUMB_REPLACE, "_")

        return "https://thumbnails.libretro.com/$systemName/$imageType/$thumbGameName.png"
    }
}
