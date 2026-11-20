package com.swordfish.lemuroid.app.tv.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.lib.library.db.entity.Game

const val ROCKSTAR_GRID_COLUMNS = 3

private val RockstarInk = Color(0xFF090A0E)
private val RockstarPanelDark = Color(0xFF14161D)
private val RockstarGold = Color(0xFFF5A623)
private val RockstarVicePink = Color(0xFFFF2A85)
private val RockstarMint = Color(0xFF2CE6A6)
private val RockstarCream = Color(0xFFF7F2E7)

private val GroupedGameEntry.rockstarGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

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
    val safeIndex = if (games.isEmpty()) 0 else selectedIndex.coerceIn(0, games.lastIndex)
    val selectedEntry = games.getOrNull(safeIndex)
    val gridState = rememberLazyGridState()

    LaunchedEffect(safeIndex, games.size) {
        if (games.isNotEmpty() && safeIndex in games.indices) {
            gridState.animateScrollToItem(safeIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // GTA / Vice City & San Andreas Comic-Panel Urban Backdrop
        RockstarComicBackdrop(modifier = Modifier.fillMaxSize())

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // LEFT PANEL (42%): GTA Comic-Panel Hero Poster & Mission Briefing
            RockstarMissionHero(
                entry = selectedEntry,
                systemTitle = systemTitle,
                screenshots = screenshots,
                screenshotIndex = screenshotIndex,
                description = description,
                selectedPosition = if (games.isEmpty()) 0 else safeIndex + 1,
                totalCount = games.size,
                modifier =
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight(),
            )

            // RIGHT PANEL (58%): Multi-Row 3-Column Mission Select Grid
            Column(
                modifier =
                    Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = RockstarGold,
                        border = BorderStroke(2.dp, RockstarInk),
                    ) {
                        Text(
                            text = "MISSION SELECT • ${systemTitle.uppercase()}",
                            color = RockstarInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = "\$${(games.size * 1000).toString().padStart(6, '0')}",
                        color = RockstarMint,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(ROCKSTAR_GRID_COLUMNS),
                    state = gridState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = false },
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items = games,
                        key = { index, item -> "${item.rockstarGame.id}_$index" },
                    ) { index, entry ->
                        RockstarMissionCard(
                            entry = entry,
                            isSelected = index == safeIndex,
                            onClick = {
                                if (index == safeIndex) {
                                    onConfirmGame(entry)
                                } else {
                                    onSelectIndex(index)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RockstarComicBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush =
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0C0E16), Color(0xFF1A1124), Color(0xFF121016)),
                    start = Offset(0f, 0f),
                    end = Offset(w, h),
                ),
        )

        // Diagonal GTA comic strip bands
        val slash1 =
            Path().apply {
                moveTo(w * 0.38f, 0f)
                lineTo(w * 0.45f, 0f)
                lineTo(w * 0.34f, h)
                lineTo(w * 0.27f, h)
                close()
            }
        drawPath(slash1, color = RockstarVicePink.copy(alpha = 0.08f))

        val slash2 =
            Path().apply {
                moveTo(w * 0.46f, 0f)
                lineTo(w * 0.49f, 0f)
                lineTo(w * 0.38f, h)
                lineTo(w * 0.35f, h)
                close()
            }
        drawPath(slash2, color = RockstarGold.copy(alpha = 0.09f))

        // Bold comic ink divider lines
        drawLine(
            color = RockstarInk.copy(alpha = 0.7f),
            start = Offset(w * 0.43f, 0f),
            end = Offset(w * 0.40f, h),
            strokeWidth = 6f,
        )
    }
}

@Composable
private fun RockstarMissionHero(
    entry: GroupedGameEntry?,
    systemTitle: String,
    screenshots: List<String>,
    screenshotIndex: Int,
    description: String?,
    selectedPosition: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val game = entry?.rockstarGame
    val wantedStars = if (game?.isFavorite == true) 5 else 3

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = RockstarPanelDark,
        border = BorderStroke(3.dp, RockstarInk),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(1.5.dp, RockstarGold.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top comic header: WANTED LEVEL stars + index
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (star in 1..5) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (star <= wantedStars) RockstarGold else Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Text(
                    text = "MISSION #$selectedPosition/$totalCount",
                    color = RockstarVicePink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Comic panel screenshot + tilted polaroid cover inset
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RockstarInk)
                        .border(3.dp, RockstarInk, RoundedCornerShape(4.dp)),
            ) {
                val activeUrl =
                    if (screenshots.isNotEmpty()) {
                        screenshots[screenshotIndex % screenshots.size]
                    } else {
                        null
                    }
                if (activeUrl != null) {
                    Crossfade(
                        targetState = activeUrl,
                        animationSpec = tween(420),
                        label = "rockstar_snap",
                    ) { url ->
                        AsyncImage(
                            model =
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(true)
                                    .build(),
                            contentDescription = game?.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else if (game != null) {
                    LemuroidGameImage(
                        game = game,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Tilted cover inset
                if (game != null) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(width = 66.dp, height = 86.dp)
                                .graphicsLayer { rotationZ = -4f },
                        shape = RoundedCornerShape(4.dp),
                        color = RockstarInk,
                        border = BorderStroke(2.5.dp, RockstarGold),
                    ) {
                        LemuroidGameImage(
                            game = game,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                        )
                    }
                }
            }

            // Title + Mission Briefing box
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = game?.title?.uppercase() ?: "SEM MISSÕES",
                    color = RockstarCream,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF0E1016),
                    border = BorderStroke(1.5.dp, RockstarVicePink.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "MISSION BRIEFING // ${systemTitle.uppercase()}",
                            color = RockstarGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text =
                                description?.takeIf { it.isNotBlank() }
                                    ?: "Prepare-se para a ação em $systemTitle. Pressione A para iniciar esta missão agora.",
                            color = RockstarCream.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RockstarMissionCard(
    entry: GroupedGameEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val game = entry.rockstarGame
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rockstar_card_scale",
    )
    val frameColor by animateColorAsState(
        targetValue = if (isSelected) RockstarGold else RockstarInk,
        animationSpec = tween(150),
        label = "rockstar_card_border",
    )

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(5.dp),
        color = if (isSelected) Color(0xFF231B12) else RockstarPanelDark,
        border = BorderStroke(if (isSelected) 3.dp else 2.5.dp, frameColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(RockstarInk),
                contentAlignment = Alignment.Center,
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
                if (isSelected) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = RockstarVicePink,
                    ) {
                        Text(
                            text = "PLAY",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
                if (game.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = RockstarGold,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = game.title.uppercase(),
                color = if (isSelected) RockstarGold else RockstarCream,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
