package com.swordfish.lemuroid.app.mobile.feature.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameListRow
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
