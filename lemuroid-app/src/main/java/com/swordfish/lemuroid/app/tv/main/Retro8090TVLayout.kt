package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GeneratedThemeBackdrop
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.themeArtwork


import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendLoop

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.lib.library.db.entity.Game

const val RETRO_8090_GRID_COLUMNS = 3

private val SynthSkyTop = Color(0xFF0A0318)
private val SynthSkyMid = Color(0xFF1E083D)
private val SynthHorizon = Color(0xFF420A4E)
private val SynthMagenta = Color(0xFFFF1F8E)
private val SynthCyan = Color(0xFF00F0FF)
private val SynthSunYellow = Color(0xFFFFE64D)
private val SynthSunOrange = Color(0xFFFF6E27)
private val SynthCardDark = Color(0xCC120624)

private val GroupedGameEntry.retroGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

private val GroupedGameEntry.retroVersions: Int
    get() =
        when (this) {
            is GroupedGameEntry.Single -> 1
            is GroupedGameEntry.MultiDisc -> discs.size
        }

@Composable
fun Retro8090TVLayout(
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
    val motion = LocalFrontendMotion.current
    val safeIndex = if (games.isEmpty()) 0 else selectedIndex.coerceIn(0, games.lastIndex)
    val selectedEntry = games.getOrNull(safeIndex)
    val selectedGame = selectedEntry?.retroGame
    val gridState = rememberLazyGridState()

    LaunchedEffect(safeIndex, games.size) {
        if (games.isNotEmpty() && safeIndex in games.indices) {
            if (motion.animatedScroll) gridState.animateScrollToItem(safeIndex) else gridState.scrollToItem(safeIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Full-screen Synthwave / Outrun 80s-90s Canvas Backdrop
        SynthwaveOutrunBackground(modifier = Modifier.fillMaxSize())

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // LEFT PANEL (42%): VHS / CRT Neon Preview Deck
            SynthwavePreviewDeck(
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

            // RIGHT PANEL (58%): True Multi-Row 3-Column Synthwave Grid
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
                    Text(
                        text = "ARCADE TAPE DECK // ${systemTitle.uppercase()}",
                        color = SynthCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "${if (games.isEmpty()) 0 else safeIndex + 1} / ${games.size}",
                        color = SynthMagenta,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(RETRO_8090_GRID_COLUMNS),
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
                        key = { index, item -> "${item.retroGame.id}_$index" },
                    ) { index, entry ->
                        SynthwaveGridCard(
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
private fun SynthwaveOutrunBackground(modifier: Modifier = Modifier) {
    GeneratedThemeBackdrop(FrontendTheme.RETRO_80_90, modifier)
}

@Composable
private fun SynthwavePreviewDeck(
    entry: GroupedGameEntry?,
    systemTitle: String,
    screenshots: List<String>,
    screenshotIndex: Int,
    description: String?,
    selectedPosition: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val context = LocalContext.current
    val game = entry?.retroGame

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = SynthCardDark,
        border = BorderStroke(2.dp, Brush.linearGradient(listOf(SynthCyan, SynthMagenta))),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // VHS / CRT Live Screenshot Frame
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.56f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF080212))
                        .border(1.5.dp, SynthCyan.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
            ) {
                val activeUrl =
                    if (screenshots.isNotEmpty()) {
                        screenshots[screenshotIndex % screenshots.size]
                    } else {
                        null
                    }
                var mediaFailed by remember(activeUrl) { mutableStateOf(false) }
                if (activeUrl != null && !mediaFailed) {
                    Crossfade(
                        targetState = activeUrl,
                        animationSpec = tween(motion.mediaMillis),
                        label = "synthwave_snap",
                    ) { url ->
                        AsyncImage(
                            model =
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .crossfade(false)
                                    .build(),
                            onError = { mediaFailed = true },
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

                // VHS REC timestamp badge
                Surface(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, SynthMagenta.copy(alpha = 0.8f)),
                ) {
                    Text(
                        text = "VHS PLAY ▶ 198X",
                        color = SynthCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                // Cover inset in bottom-right corner
                if (game != null) {
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(width = 62.dp, height = 82.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF140728),
                        border = BorderStroke(1.5.dp, SynthMagenta),
                    ) {
                        LemuroidGameImage(
                            game = game,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(2.dp),
                        )
                    }
                }
            }

            // Title + Badges + Description
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.44f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = game?.title?.uppercase() ?: "NENHUM JOGO",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SynthMagenta.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, SynthMagenta),
                    ) {
                        Text(
                            text = systemTitle.uppercase(),
                            color = SynthSunYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                    if (game?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = SynthMagenta,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if ((entry?.retroVersions ?: 1) > 1) {
                        Text(
                            text = "${entry?.retroVersions} VERSÕES",
                            color = SynthCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "#$selectedPosition/$totalCount",
                        color = SynthCyan.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Text(
                    text =
                        description?.takeIf { it.isNotBlank() }
                            ?: "Clássico retrô dos anos 80/90 pronto para rodar em $systemTitle. Aperte A para iniciar.",
                    color = Color(0xFFE3D5FF),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SynthwaveGridCard(
    entry: GroupedGameEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val motion = LocalFrontendMotion.current
    val game = entry.retroGame
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = tween(motion.focusMillis),
        label = "synth_card_scale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) SynthCyan else SynthMagenta.copy(alpha = 0.35f),
        animationSpec = tween(motion.focusMillis),
        label = "synth_card_border",
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
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xE6260B47) else Color(0xCC130624),
        border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0A0316)),
                contentAlignment = Alignment.Center,
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                )
                if (game.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = SynthMagenta,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(13.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = game.title,
                color = if (isSelected) SynthSunYellow else Color(0xFFE0D0FF),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
