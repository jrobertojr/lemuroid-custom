package com.swordfish.lemuroid.app.mobile.feature.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.Game3DObject
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameListRow
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ThemedGameGridItem
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.WoodenShelfDivider
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.app.shared.games.MultiDiscGrouper
import com.swordfish.lemuroid.lib.library.db.entity.Game

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    val games = viewModel.games.collectAsLazyPagingItems()
    var selectedMultiDisc by remember { mutableStateOf<GroupedGameEntry.MultiDisc?>(null) }

    if (games.itemCount == 0) {
        LemuroidEmptyView()
        return
    }

    val loadedGames = games.itemSnapshotList.items
    val entries = remember(loadedGames) { MultiDiscGrouper.group(loadedGames) }
    val theme = LocalFrontendTheme.current

    if (theme == FrontendTheme.DEFAULT) {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(
                entries,
                key = { entry ->
                    when (entry) {
                        is GroupedGameEntry.Single -> "single_${entry.game.id}"
                        is GroupedGameEntry.MultiDisc -> "multi_${entry.folderKey}"
                    }
                },
            ) { entry ->
                when (entry) {
                    is GroupedGameEntry.Single -> {
                        val game = entry.game
                        LemuroidGameListRow(
                            modifier = Modifier.animateItem(),
                            game = game,
                            onClick = { onGameClick(game) },
                            onLongClick = { onGameLongClick(game) },
                            onFavoriteToggle = { isFavorite -> onGameFavoriteToggle(game, isFavorite) },
                        )
                    }

                    is GroupedGameEntry.MultiDisc -> {
                        val primary = entry.discs.first()
                        LemuroidGameListRow(
                            modifier = Modifier.animateItem(),
                            game = entry.representative,
                            onClick = { selectedMultiDisc = entry },
                            onLongClick = { onGameLongClick(primary) },
                            onFavoriteToggle = { isFavorite -> onGameFavoriteToggle(primary, isFavorite) },
                        )
                    }
                }
            }
        }
    } else {
        val columns = if (theme == FrontendTheme.THREE_D) 2 else 3
        val chunkedRows = remember(entries, columns) { entries.chunked(columns) }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                chunkedRows,
                key = { row ->
                    row.joinToString(separator = "_") { entry ->
                        when (entry) {
                            is GroupedGameEntry.Single -> "single_${entry.game.id}"
                            is GroupedGameEntry.MultiDisc -> "multi_${entry.folderKey}"
                        }
                    }
                },
            ) { rowEntries ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowEntries.forEach { entry ->
                            ThemedGamesGridCell(
                                entry = entry,
                                theme = theme,
                                onSingleClick = onGameClick,
                                onMultiDiscClick = { selectedMultiDisc = it },
                                onGameLongClick = onGameLongClick,
                                onGameFavoriteToggle = onGameFavoriteToggle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - rowEntries.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (theme == FrontendTheme.LOCADORA) {
                        WoodenShelfDivider(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }

    selectedMultiDisc?.let { group ->
        GroupedGameSelectionDialog(
            group = group,
            onSelect = { selected ->
                selectedMultiDisc = null
                onGameClick(selected)
            },
            onDismiss = { selectedMultiDisc = null },
        )
    }
}

@Composable
private fun ThemedGamesGridCell(
    entry: GroupedGameEntry,
    theme: FrontendTheme,
    onSingleClick: (Game) -> Unit,
    onMultiDiscClick: (GroupedGameEntry.MultiDisc) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayGame: Game
    val targetGame: Game
    val onClick: () -> Unit

    when (entry) {
        is GroupedGameEntry.Single -> {
            displayGame = entry.game
            targetGame = entry.game
            onClick = { onSingleClick(entry.game) }
        }

        is GroupedGameEntry.MultiDisc -> {
            displayGame = entry.representative
            targetGame = entry.discs.first()
            onClick = { onMultiDiscClick(entry) }
        }
    }

    if (theme == FrontendTheme.THREE_D) {
        Game3DObject(
            game = displayGame,
            onClick = onClick,
            onLongClick = { onGameLongClick(targetGame) },
            onFavoriteToggle = { isFavorite -> onGameFavoriteToggle(targetGame, isFavorite) },
            modifier = modifier,
        )
    } else {
        ThemedGameGridItem(
            game = displayGame,
            theme = theme,
            onClick = onClick,
            onLongClick = { onGameLongClick(targetGame) },
            onFavoriteToggle = { isFavorite -> onGameFavoriteToggle(targetGame, isFavorite) },
            modifier = modifier,
        )
    }
}

@Composable
internal fun GroupedGameSelectionDialog(
    group: GroupedGameEntry.MultiDisc,
    onSelect: (Game) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = group.folderName) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                group.discs.forEachIndexed { index, disc ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    Text(
                        text = group.itemLabel(disc),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(disc) }
                                .padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}
