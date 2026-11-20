package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LemuroidGameCard(
    modifier: Modifier = Modifier,
    game: Game,
    onClick: () -> Unit = { },
    onLongClick: () -> Unit = { },
) {
    val theme = LocalFrontendTheme.current

    when (theme) {
        FrontendTheme.DEFAULT -> {
            ElevatedCard(
                modifier = modifier,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = onClick,
                                onLongClick = onLongClick,
                            ),
                ) {
                    LemuroidGameImage(game = game)
                    LemuroidGameTexts(game = game)
                }
            }
        }

        FrontendTheme.THREE_D -> {
            Game3DObject(
                game = game,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
            )
        }

        else -> {
            ThemedGameGridItem(
                game = game,
                theme = theme,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
            )
        }
    }
}
