package com.swordfish.lemuroid.app.mobile.feature.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.games.GroupedGameSelectionDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameListRow
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.app.shared.games.MultiDiscGrouper
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel,
    searchQuery: String,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
    onResetSearchQuery: () -> Unit,
) {
    val searchState = viewModel.searchState.collectAsState(SearchViewModel.UIState.Idle)
    val searchGames = viewModel.searchResults.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        onResetSearchQuery()
    }

    LaunchedEffect(key1 = searchQuery) {
        viewModel.queryString.value = searchQuery
    }

    AnimatedContent(
        targetState = searchState.value,
        label = "SearchContent",
        transitionSpec = { fadeIn() togetherWith fadeOut() },
    ) { state ->
        when {
            state == SearchViewModel.UIState.Idle -> {
                SearchEmptyView(modifier, stringResource(R.string.game_page_search_suggestion))
            }

            state == SearchViewModel.UIState.Loading -> {
                SearchLoadingView(modifier)
            }

            state == SearchViewModel.UIState.Ready && searchGames.itemCount == 0 -> {
                SearchEmptyView(modifier, stringResource(id = R.string.empty_view_default))
            }

            else -> {
                SearchResultsView(
                    modifier,
                    searchGames,
                    onGameClick,
                    onGameLongClick,
                    onGameFavoriteToggle,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultsView(
    modifier: Modifier,
    games: LazyPagingItems<Game>,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    var selectedGroup by remember { mutableStateOf<GroupedGameEntry.MultiDisc?>(null) }
    val loadedGames = games.itemSnapshotList.items
    val entries = remember(loadedGames) { MultiDiscGrouper.group(loadedGames) }

    LazyColumn(modifier = modifier) {
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
                        onFavoriteToggle = { isFavorite ->
                            onGameFavoriteToggle(game, isFavorite)
                        },
                    )
                }

                is GroupedGameEntry.MultiDisc -> {
                    val primary = entry.discs.first()
                    LemuroidGameListRow(
                        modifier = Modifier.animateItem(),
                        game = entry.representative,
                        onClick = { selectedGroup = entry },
                        onLongClick = { onGameLongClick(primary) },
                        onFavoriteToggle = { isFavorite ->
                            onGameFavoriteToggle(primary, isFavorite)
                        },
                    )
                }
            }
        }
    }

    selectedGroup?.let { group ->
        GroupedGameSelectionDialog(
            group = group,
            onSelect = { selected ->
                selectedGroup = null
                onGameClick(selected)
            },
            onDismiss = { selectedGroup = null },
        )
    }
}

@Composable
private fun SearchEmptyView(
    modifier: Modifier,
    text: String,
) {
    LemuroidEmptyView(
        modifier = modifier,
        text = text,
    )
}

@Composable
private fun SearchLoadingView(modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}
