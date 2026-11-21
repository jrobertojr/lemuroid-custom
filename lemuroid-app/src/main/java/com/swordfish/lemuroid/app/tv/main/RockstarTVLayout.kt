package com.swordfish.lemuroid.app.tv.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.HardRockVinylLayout
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry

@Composable
fun RockstarTVLayout(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    systemKey: String,
    screenshots: List<String>,
    screenshotIndex: Int,
    description: String?,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    HardRockVinylLayout(
        games = games,
        selectedIndex = selectedIndex,
        systemTitle = systemTitle,
        description = description,
        onSelectIndex = onSelectIndex,
        onConfirmGame = onConfirmGame,
        modifier = modifier,
    )
}
