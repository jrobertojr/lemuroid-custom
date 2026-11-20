package com.swordfish.lemuroid.app.tv.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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

const val THRASH_METAL_GRID_COLUMNS = 3

private val MetalCharcoal = Color(0xFF0A0A0C)
private val MetalSteelDark = Color(0xFF18181D)
private val MetalCrimson = Color(0xFFCC0000)
private val MetalFire = Color(0xFFFF6D00)
private val MetalChrome = Color(0xFFE4E6EB)
private val MetalMuted = Color(0xFFA0A4AE)

private val GroupedGameEntry.metalGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

@Composable
fun ThrashMetalTVLayout(
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
        // Aggressive 80s/90s Thrash Metal Brushed Steel & Lightning Backdrop
        ThrashMetalBackdrop(modifier = Modifier.fillMaxSize())

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // LEFT PANEL (42%): 12" Vinyl LP Sleeve + Rotating Record + Liner Notes
            ThrashVinylShowcase(
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

            // RIGHT PANEL (58%): Multi-Row 3-Column Vinyl LP Crate Grid
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
                        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                        color = MetalCrimson,
                        border = BorderStroke(1.5.dp, MetalChrome),
                    ) {
                        Text(
                            text = "⚡ VINYL VAULT // ${systemTitle.uppercase()} ⚡",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = "TRACK ${if (games.isEmpty()) 0 else safeIndex + 1}/${games.size}",
                        color = MetalFire,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(THRASH_METAL_GRID_COLUMNS),
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
                        key = { index, item -> "${item.metalGame.id}_$index" },
                    ) { index, entry ->
                        ThrashVinylCrateCard(
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
private fun ThrashMetalBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF140406), MetalCharcoal, Color(0xFF0E0507)),
                ),
        )

        // Brushed steel horizontal scratches
        for (i in 0..18) {
            val y = h * (i / 18f)
            drawLine(
                color = MetalChrome.copy(alpha = 0.03f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.2f,
            )
        }

        // Aggressive jagged lightning crack accent
        val bolt =
            Path().apply {
                moveTo(w * 0.44f, 0f)
                lineTo(w * 0.40f, h * 0.24f)
                lineTo(w * 0.45f, h * 0.26f)
                lineTo(w * 0.38f, h * 0.62f)
                lineTo(w * 0.43f, h * 0.65f)
                lineTo(w * 0.37f, h)
            }
        drawPath(
            path = bolt,
            color = MetalCrimson.copy(alpha = 0.28f),
            style = Stroke(width = 4f),
        )
        drawPath(
            path = bolt,
            color = MetalFire.copy(alpha = 0.18f),
            style = Stroke(width = 1.5f),
        )
    }
}

@Composable
private fun ThrashVinylShowcase(
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
    val game = entry?.metalGame
    val infiniteTransition = rememberInfiniteTransition(label = "vinyl_spin")
    val vinylAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "vinyl_angle",
    )

    Surface(
        modifier = modifier,
        shape = CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp),
        color = MetalSteelDark.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, Brush.linearGradient(listOf(MetalCrimson, MetalChrome, MetalFire))),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top section: 12" Vinyl Album Sleeve + Spinning Black LP Record peeking out!
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.50f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Vinyl sleeve + spinning disc
                Box(
                    modifier =
                        Modifier
                            .width(195.dp)
                            .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // Spinning 12" Vinyl LP peeking to the right
                    Canvas(
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .size(134.dp),
                    ) {
                        val r = size.minDimension / 2f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        rotate(degrees = vinylAngle, pivot = c) {
                            drawCircle(color = Color(0xFF0A0A0D), radius = r, center = c)
                            // Concentric grooves
                            for (g in 1..6) {
                                drawCircle(
                                    color = Color.White.copy(alpha = if (g % 2 == 0) 0.11f else 0.05f),
                                    radius = r * (0.42f + g * 0.085f),
                                    center = c,
                                    style = Stroke(width = 1.2f),
                                )
                            }
                            // Specular diagonal vinyl sheen
                            drawArc(
                                color = Color.White.copy(alpha = 0.14f),
                                startAngle = -35f,
                                sweepAngle = 45f,
                                useCenter = true,
                                topLeft = Offset(c.x - r, c.y - r),
                                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                            )
                            // Crimson center record label
                            drawCircle(color = MetalCrimson, radius = r * 0.34f, center = c)
                            drawCircle(
                                color = MetalFire,
                                radius = r * 0.24f,
                                center = c,
                                style = Stroke(width = 2f),
                            )
                            // Spindle hole
                            drawCircle(color = MetalCharcoal, radius = r * 0.07f, center = c)
                        }
                    }

                    // Square 12" LP Album Sleeve in front
                    Surface(
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .size(138.dp),
                        shape = RoundedCornerShape(3.dp),
                        color = MetalCharcoal,
                        border = BorderStroke(2.dp, MetalChrome.copy(alpha = 0.85f)),
                    ) {
                        if (game != null) {
                            LemuroidGameImage(
                                game = game,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(3.dp),
                            )
                        }
                    }
                }

                // Live Gameplay Screenshot inset beside the Vinyl
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp))
                            .background(MetalCharcoal)
                            .border(
                                1.5.dp,
                                MetalCrimson,
                                CutCornerShape(topEnd = 8.dp, bottomStart = 8.dp),
                            ),
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
                            label = "metal_snap",
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
                }
            }

            // Bottom section: Aggressive Title + Liner Notes Description
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.50f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = game?.title?.uppercase() ?: "NO RECORDS FOUND",
                    color = MetalChrome,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(
                    shape = CutCornerShape(bottomEnd = 8.dp),
                    color = MetalCharcoal,
                    border = BorderStroke(1.5.dp, MetalCrimson.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "LINER NOTES // ${systemTitle.uppercase()} 33⅓ RPM",
                                color = MetalFire,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                text = "#$selectedPosition/$totalCount",
                                color = MetalMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text =
                                description?.takeIf { it.isNotBlank() }
                                    ?: "EDICAO HEAVY METAL EM VINIL PARA $systemTitle. APERTE A PARA SOLTAR O SOM E JOGAR.",
                            color = MetalChrome.copy(alpha = 0.88f),
                            fontSize = 11.5.sp,
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
private fun ThrashVinylCrateCard(
    entry: GroupedGameEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val game = entry.metalGame
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "metal_card_scale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MetalFire else MetalCrimson.copy(alpha = 0.45f),
        animationSpec = tween(150),
        label = "metal_card_border",
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
        shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
        color = if (isSelected) Color(0xFF28080A) else MetalSteelDark,
        border = BorderStroke(if (isSelected) 2.5.dp else 1.2.dp, borderColor),
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
                        .background(MetalCharcoal),
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
                        tint = MetalCrimson,
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
                text = game.title.uppercase(),
                color = if (isSelected) MetalFire else MetalChrome,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
