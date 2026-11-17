package com.swordfish.lemuroid.app.shared.games

import android.net.Uri
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game

sealed class GroupedGameEntry {
    data class Single(val game: Game) : GroupedGameEntry()

    data class MultiDisc(
        val folderKey: String,
        val folderName: String,
        val representative: Game,
        val discs: List<Game>,
    ) : GroupedGameEntry()
}

object MultiDiscGrouper {
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

        val emittedFolders = HashSet<String>()
        val result = ArrayList<GroupedGameEntry>(games.size)

        for (game in games) {
            val folderKey =
                if (game.systemId == SystemID.PSX.dbname) {
                    extractParentFolder(game)?.first
                } else {
                    null
                }
            val multi = if (folderKey != null) multiDiscFolders[folderKey] else null
            if (multi != null) {
                if (emittedFolders.add(folderKey!!)) {
                    val folderName = multi.first
                    val discs = multi.second
                    val primary = discs.first()
                    val coverGame = discs.firstOrNull { !it.displayCoverUrl().isNullOrBlank() } ?: primary
                    val representative =
                        primary.copy(
                            title = folderName,
                            customName = null,
                            developer = "${discs.size} CDs",
                            coverFrontUrl = coverGame.coverFrontUrl,
                            customCoverUri = coverGame.customCoverUri,
                            isFavorite = discs.any { it.isFavorite },
                        )
                    result.add(GroupedGameEntry.MultiDisc(folderKey, folderName, representative, discs))
                }
            } else {
                result.add(GroupedGameEntry.Single(game))
            }
        }

        return result
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
