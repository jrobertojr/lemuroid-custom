package com.swordfish.lemuroid.app.shared.games

import android.net.Uri
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.util.Locale

sealed class GroupedGameEntry {
    data class Single(val game: Game) : GroupedGameEntry()

    data class MultiDisc(
        val folderKey: String,
        val folderName: String,
        val representative: Game,
        val discs: List<Game>,
    ) : GroupedGameEntry() {
        fun itemLabel(item: Game): String {
            val hasDuplicateTitle = discs.count { it.title == item.title } > 1
            return if (hasDuplicateTitle) "${item.title} (${item.fileName})" else item.title
        }
    }
}

object MultiDiscGrouper {
    private val PARENS_OR_BRACKETS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")
    private val DISC_SUFFIX = Regex("\\s+(?:CD|Disc|Disk)\\s*\\d+.*$", RegexOption.IGNORE_CASE)
    private val MULTI_SPACE = Regex("\\s+")

    fun group(games: List<Game>): List<GroupedGameEntry> {
        val groupedByFolder =
            games
                .filter { it.systemId == SystemID.PSX.dbname }
                .mapNotNull { game -> extractParentFolder(game)?.let { it to game } }
                .groupBy({ it.first.first }, { it.first.second to it.second })

        val multiDiscFolders =
            groupedByFolder
                .filter { (_, list) -> list.size > 1 }
                .mapValues { (_, list) ->
                    list.first().first to list.map { it.second }.sortedBy { it.fileName }
                }

        val multiDiscGameIds =
            multiDiscFolders.values
                .flatMap { it.second }
                .map { it.id }
                .toHashSet()

        val versionGroups =
            games
                .asSequence()
                .filter { it.id !in multiDiscGameIds }
                .mapNotNull { game ->
                    val baseTitle = normalizeTitleForGrouping(game.title)
                    if (baseTitle.isBlank()) {
                        null
                    } else {
                        val key = "${game.systemId}|${baseTitle.lowercase(Locale.ROOT)}"
                        Triple(key, baseTitle, game)
                    }
                }
                .groupBy({ it.first }, { it.second to it.third })
                .filter { (_, list) -> list.size > 1 }
                .mapValues { (_, list) ->
                    val displayTitle = list.first().first
                    val variants = list.map { it.second }.sortedWith(compareBy({ it.title }, { it.fileName }))
                    displayTitle to variants
                }

        val emittedKeys = HashSet<String>()
        val result = ArrayList<GroupedGameEntry>(games.size)

        for (game in games) {
            val folderKey =
                if (game.systemId == SystemID.PSX.dbname) {
                    extractParentFolder(game)?.first
                } else {
                    null
                }
            val multi = if (folderKey != null) multiDiscFolders[folderKey] else null
            if (folderKey != null && multi != null) {
                val emitKey = "folder:$folderKey"
                if (emittedKeys.add(emitKey)) {
                    val folderName = multi.first
                    val discs = multi.second
                    result.add(
                        buildGroupEntry(
                            key = folderKey,
                            groupTitle = folderName,
                            subtitle = "${discs.size} CDs",
                            items = discs,
                        ),
                    )
                }
                continue
            }

            val baseTitle = normalizeTitleForGrouping(game.title)
            val versionKey =
                if (baseTitle.isNotBlank()) {
                    "${game.systemId}|${baseTitle.lowercase(Locale.ROOT)}"
                } else {
                    null
                }
            val versionGroup = if (versionKey != null) versionGroups[versionKey] else null
            if (versionKey != null && versionGroup != null) {
                val emitKey = "version:$versionKey"
                if (emittedKeys.add(emitKey)) {
                    val displayTitle = versionGroup.first
                    val variants = versionGroup.second
                    result.add(
                        buildGroupEntry(
                            key = versionKey,
                            groupTitle = displayTitle,
                            subtitle = "${variants.size} versões",
                            items = variants,
                        ),
                    )
                }
            } else {
                result.add(GroupedGameEntry.Single(game))
            }
        }

        return result
    }

    private fun buildGroupEntry(
        key: String,
        groupTitle: String,
        subtitle: String,
        items: List<Game>,
    ): GroupedGameEntry.MultiDisc {
        val primary = items.first()
        val coverGame = items.firstOrNull { !it.displayCoverUrl().isNullOrBlank() } ?: primary
        val representative =
            primary.copy(
                title = groupTitle,
                customName = null,
                developer = subtitle,
                coverFrontUrl = coverGame.coverFrontUrl,
                customCoverUri = coverGame.customCoverUri,
                isFavorite = items.any { it.isFavorite },
            )
        return GroupedGameEntry.MultiDisc(key, groupTitle, representative, items)
    }

    private fun normalizeTitleForGrouping(title: String): String {
        return title
            .replace(PARENS_OR_BRACKETS, "")
            .replace(DISC_SUFFIX, "")
            .replace(MULTI_SPACE, " ")
            .trim(' ', '-', '_')
    }

    private fun extractParentFolder(game: Game): Pair<String, String>? {
        val decoded = Uri.decode(game.fileUri)
        val lastSlash = decoded.lastIndexOf('/')
        if (lastSlash <= 0) return null
        val parentPath = decoded.substring(0, lastSlash)
        val folderName = parentPath.substringAfterLast('/').substringAfterLast(':')
        if (folderName.isBlank() || folderName.equals(game.systemId, ignoreCase = true)) {
            return null
        }
        return parentPath to folderName
    }
}
