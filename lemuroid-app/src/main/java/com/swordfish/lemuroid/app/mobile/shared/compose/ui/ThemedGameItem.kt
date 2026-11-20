package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.db.entity.Game

private val CARTRIDGE_SYSTEMS =
    setOf("snes", "n64", "md", "nes", "gba", "gb", "gbc", "sms", "gg", "lynx", "atari2600", "pce")

private val JEWEL_CASE_SYSTEMS =
    setOf("psx", "psp", "nds", "segacd", "gc", "3ds")

private val ARCADE_SYSTEMS =
    setOf("fbneo", "mame2003plus")

private fun systemAccentColor(systemId: String): Color =
    when (systemId.lowercase()) {
        "snes" -> Color(0xFF7E6BE0)
        "nes" -> Color(0xFFD92B2B)
        "n64" -> Color(0xFFE5A50A)
        "md", "segacd" -> Color(0xFF1F6EE0)
        "sms", "gg" -> Color(0xFF0096C7)
        "gba" -> Color(0xFF5A4AE3)
        "gb", "gbc" -> Color(0xFF28A763)
        "psx" -> Color(0xFF2E7CE6)
        "psp" -> Color(0xFF00A8E8)
        "nds", "3ds" -> Color(0xFFE63946)
        "gc" -> Color(0xFF6C5CE7)
        "pce" -> Color(0xFFFF6B35)
        "atari2600", "lynx" -> Color(0xFFD97706)
        else -> Color(0xFFF5B700)
    }

/**
 * Renders a game card styled according to the active [FrontendTheme].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemedGameGridItem(
    game: Game,
    theme: FrontendTheme,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavoriteToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val clickModifier =
        modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        )

    when (theme) {
        FrontendTheme.LOCADORA ->
            LocadoraGameItem(
                game = game,
                onFavoriteToggle = onFavoriteToggle,
                modifier = clickModifier,
            )

        FrontendTheme.NETFLIX ->
            NetflixGameItem(
                game = game,
                onFavoriteToggle = onFavoriteToggle,
                modifier = clickModifier,
            )

        FrontendTheme.CONSOLE ->
            ConsoleGameItem(
                game = game,
                onFavoriteToggle = onFavoriteToggle,
                modifier = clickModifier,
            )

        FrontendTheme.TV ->
            TvGameItem(
                game = game,
                onFavoriteToggle = onFavoriteToggle,
                modifier = clickModifier,
            )

        FrontendTheme.RETRO_80_90 ->
            Retro8090GameItem(
                game = game,
                onFavoriteToggle = onFavoriteToggle,
                modifier = clickModifier,
            )

        FrontendTheme.ROCKSTAR ->
            RockstarGameItem(
                game = game,
                onFavoriteToggle = onFavoriteToggle,
                modifier = clickModifier,
            )

        FrontendTheme.THRASH_METAL ->
            ThrashMetalGameItem(
                game = game,
                onFavoriteToggle = onFavoriteToggle,
                modifier = clickModifier,
            )

        FrontendTheme.DEFAULT,
        FrontendTheme.THREE_D,
        ->
            Box(modifier = clickModifier) {
                LemuroidGameCard(
                    game = game,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onFavoriteToggle != null) {
                    CompactFavoriteOverlay(
                        isFavorite = game.isFavorite,
                        onToggle = onFavoriteToggle,
                        activeColor = Color(0xFFFF4D6D),
                    )
                }
            }
    }
}

/**
 * Draws a rich wooden rental-store shelf plank with multi-tone wood grain, top bevel highlight,
 * and realistic drop shadow underneath, intended to be placed below rows in [FrontendTheme.LOCADORA] mode.
 */
@Composable
fun WoodenShelfDivider(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color(0xFFA07449),
                                        Color(0xFF7E5734),
                                        Color(0xFF5D3E23),
                                        Color(0xFF3E2714),
                                    ),
                            ),
                    ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Warm horizontal tonal sheen across the wood plank
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0x22000000),
                                    Color(0x18FFE0B2),
                                    Color(0x10000000),
                                    Color(0x16FFCC80),
                                    Color(0x26000000),
                                ),
                        ),
                )

                // Crisp top edge bevel highlight
                drawLine(
                    color = Color(0xFFC99E70),
                    start = Offset(0f, 0.8.dp.toPx()),
                    end = Offset(size.width, 0.8.dp.toPx()),
                    strokeWidth = 1.4.dp.toPx(),
                )

                // Layered multi-tone wood grain fibers
                val grainRatios = listOf(0.26f, 0.44f, 0.62f, 0.78f)
                grainRatios.forEachIndexed { index, ratio ->
                    val y = size.height * ratio
                    val strokeColor = if (index % 2 == 0) Color(0x38241408) else Color(0x24D6A874)
                    drawLine(
                        color = strokeColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = (if (index == 1) 1.2f else 0.85f).dp.toPx(),
                    )
                }

                // Subtle darker wood-grain streaks for natural plank character
                val segmentCount = 5
                val segWidth = size.width / segmentCount
                for (i in 0 until segmentCount) {
                    val startX = i * segWidth + segWidth * 0.12f
                    val endX = startX + segWidth * 0.58f
                    val y = size.height * (0.34f + (i % 3) * 0.2f)
                    drawLine(
                        color = Color(0x361E1107),
                        start = Offset(startX, y),
                        end = Offset(endX, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                // Lower plank lip shadow
                drawLine(
                    color = Color(0xFF26160A),
                    start = Offset(0f, size.height - 0.8.dp.toPx()),
                    end = Offset(size.width, size.height - 0.8.dp.toPx()),
                    strokeWidth = 1.4.dp.toPx(),
                )
            }
        }
        // Soft multi-stop cast shadow underneath the wooden shelf
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color(0xB8000000),
                                        Color(0x52000000),
                                        Color(0x18000000),
                                        Color.Transparent,
                                    ),
                            ),
                    ),
        )
    }
}

@Composable
private fun LocadoraGameItem(
    game: Game,
    onFavoriteToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val systemKey = game.systemId.lowercase()
    Column(
        modifier = modifier.padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            when (systemKey) {
                in JEWEL_CASE_SYSTEMS -> LocadoraJewelCase(game = game)
                in ARCADE_SYSTEMS -> LocadoraArcadeMarquee(game = game)
                else -> LocadoraCartridgeShell(game = game, isCartridgeSystem = systemKey in CARTRIDGE_SYSTEMS)
            }

            // Crisp retro video-rental #id tag sticker
            Surface(
                color = Color(0xFFFFE156),
                shape = RoundedCornerShape(3.dp),
                border = BorderStroke(0.75.dp, Color(0xFFB58900)),
                shadowElevation = 3.dp,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 6.dp)
                        .graphicsLayer { rotationZ = -3.5f },
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier =
                            Modifier
                                .width(26.dp)
                                .height(2.dp)
                                .background(Color(0xFFD62828)),
                    )
                    Text(
                        text = "#${game.id}",
                        color = Color(0xFF1A1408),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }

            if (onFavoriteToggle != null) {
                CompactFavoriteOverlay(
                    isFavorite = game.isFavorite,
                    onToggle = onFavoriteToggle,
                    activeColor = Color(0xFFFF4D6D),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = game.displayName(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LocadoraCartridgeShell(
    game: Game,
    isCartridgeSystem: Boolean,
) {
    val shellShape = RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val shellTopColor = if (isCartridgeSystem) Color(0xFF636875) else Color(0xFF535864)
    val shellMidColor = if (isCartridgeSystem) Color(0xFF4B4F5B) else Color(0xFF41454F)
    val shellBottomColor = Color(0xFF31343D)
    val accentColor = systemAccentColor(game.systemId)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shellShape)
                .background(
                    brush = Brush.verticalGradient(listOf(shellTopColor, shellMidColor, shellBottomColor)),
                )
                .border(
                    width = 1.dp,
                    brush =
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF949AA8),
                                Color(0xFF5C616E),
                                Color(0xFF23252C),
                            ),
                        ),
                    shape = shellShape,
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Molded 3D top grip ridges with paired highlight & shadow lines
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp),
        ) {
            val ridgeCount = 3
            val spacing = size.height / (ridgeCount + 1)
            for (i in 1..ridgeCount) {
                val y = spacing * i
                drawLine(
                    color = Color(0xFF21242B),
                    start = Offset(2.dp.toPx(), y),
                    end = Offset(size.width - 2.dp.toPx(), y),
                    strokeWidth = 1.2.dp.toPx(),
                )
                drawLine(
                    color = Color(0x55C8CEDB),
                    start = Offset(2.dp.toPx(), y + 1.1.dp.toPx()),
                    end = Offset(size.width - 2.dp.toPx(), y + 1.1.dp.toPx()),
                    strokeWidth = 0.7.dp.toPx(),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Authentic color-coded system stripe with crisp label
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                listOf(accentColor, accentColor.copy(alpha = 0.78f), Color(0xFF1F222A)),
                            ),
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = game.systemId.uppercase(),
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Box(
                modifier =
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xCCFFFFFF)),
            )
        }

        // Recessed label well (no cropping: ContentScale.Fit)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                    .background(Color(0xFF12141A))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF1E2129),
                        shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp),
                    ),
        ) {
            LemuroidGameImage(
                game = game,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtle molded bottom grip indent
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(28.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF23262E)),
        )
    }
}

@Composable
private fun LocadoraJewelCase(game: Game) {
    val caseShape = RoundedCornerShape(5.dp)
    val accentColor = systemAccentColor(game.systemId)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(caseShape)
                .background(Color(0xFF181A20))
                .border(
                    width = 1.2.dp,
                    brush =
                        Brush.linearGradient(
                            listOf(Color(0xBBFFFFFF), Color(0x44FFFFFF), Color(0x88AAB4C4)),
                        ),
                    shape = caseShape,
                ),
    ) {
        // Ribbed translucent left jewel-case spine strip
        Box(
            modifier =
                Modifier
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                listOf(Color(0xFF303542), Color(0xFF1A1D25), Color(0xFF252934)),
                            ),
                    ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 3.5.dp.toPx()
                var y = step
                while (y < size.height - step) {
                    drawLine(
                        color = Color(0x4DFFFFFF),
                        start = Offset(2.dp.toPx(), y),
                        end = Offset(size.width - 3.dp.toPx(), y),
                        strokeWidth = 0.85.dp.toPx(),
                    )
                    drawLine(
                        color = Color(0x55000000),
                        start = Offset(2.dp.toPx(), y + 1.dp.toPx()),
                        end = Offset(size.width - 3.dp.toPx(), y + 1.dp.toPx()),
                        strokeWidth = 0.7.dp.toPx(),
                    )
                    y += step
                }
            }
            // Authentic system accent edge along the spine
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(accentColor.copy(alpha = 0.85f)),
            )
        }

        // Recessed CD booklet art well with subtle plastic gloss sheen
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF101218)),
        ) {
            LemuroidGameImage(
                game = game,
                contentScale = ContentScale.Fit,
                preserveAspect = true,
                modifier = Modifier.fillMaxSize(),
            )
            // Subtle molded jewel-case plastic sheen
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            brush =
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            Color(0x1CFFFFFF),
                                            Color.Transparent,
                                            Color(0x0AFFFFFF),
                                        ),
                                ),
                        ),
            )
        }
    }
}

@Composable
private fun LocadoraArcadeMarquee(game: Game) {
    val cabinetShape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 4.dp, bottomEnd = 4.dp)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(cabinetShape)
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFF252834), Color(0xFF14161D))),
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(listOf(Color(0xFFFFD166), Color(0xFFE09F00), Color(0xFF8C5E00))),
                    shape = cabinetShape,
                ),
    ) {
        // Illuminated backlit arcade marquee header
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        brush =
                            Brush.horizontalGradient(
                                listOf(Color(0xFFD62828), Color(0xFFFFB703), Color(0xFFFFE169), Color(0xFFFFB703), Color(0xFFD62828)),
                            ),
                    )
                    .padding(vertical = 3.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ARCADE",
                color = Color(0xFF141008),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
            )
        }

        Box(
            modifier =
                Modifier
                    .padding(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF0E1016))
                    .border(1.dp, Color(0xFF2D3140), RoundedCornerShape(3.dp)),
        ) {
            LemuroidGameImage(
                game = game,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun NetflixGameItem(
    game: Game,
    onFavoriteToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(6.dp)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = cardShape,
        color = Color(0xFF141414),
        border = BorderStroke(1.dp, Color(0xFF2A2A2A)),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Crisp signature #E50914 cinema accent strip
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFB20710), Color(0xFFE50914), Color(0xFFFF2430), Color(0xFFE50914)),
                                ),
                        ),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0A0A)),
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Subtle top-to-bottom cinema vignette
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        0.0f to Color(0x38000000),
                                        0.24f to Color.Transparent,
                                        0.68f to Color.Transparent,
                                        1.0f to Color(0xCC0D0D0D),
                                    ),
                            ),
                )

                // Clean cinema "PLAY" badge
                Surface(
                    color = Color(0xFFE50914),
                    shape = RoundedCornerShape(3.dp),
                    shadowElevation = 3.dp,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(7.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "PLAY",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                }

                if (onFavoriteToggle != null) {
                    CompactFavoriteOverlay(
                        isFavorite = game.isFavorite,
                        onToggle = onFavoriteToggle,
                        activeColor = Color(0xFFE50914),
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141414))
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = game.displayName(),
                    color = Color(0xFFFAFAFA),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = game.systemId.uppercase(),
                    color = Color(0xFF9E9E9E),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ConsoleGameItem(
    game: Game,
    onFavoriteToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val rimGradient =
        Brush.linearGradient(
            colors =
                listOf(
                    Color(0xFF00F0FF),
                    Color(0xFF2979FF),
                    Color(0xFF6C63FF),
                    Color(0xFF00C8FF),
                ),
        )

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .border(width = 1.5.dp, brush = rimGradient, shape = shape),
        shape = shape,
        color = Color(0xFF0F1729),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        brush =
                            Brush.verticalGradient(
                                listOf(Color(0xFF17233B), Color(0xFF0E1627), Color(0xFF0B1120)),
                            ),
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .background(Color(0xFF080D17)),
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Subtle translucent glass top-edge sheen
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush =
                                    Brush.verticalGradient(
                                        listOf(Color(0x24FFFFFF), Color.Transparent),
                                    ),
                            ),
                )

                // Sleek frosted dark-glass system pill with luminous cyan dot
                Surface(
                    color = Color(0xD9071426),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color(0xB300E5FF)),
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F0FF)),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = game.systemId.uppercase(),
                            color = Color(0xFFD6F9FF),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                        )
                    }
                }

                if (onFavoriteToggle != null) {
                    CompactFavoriteOverlay(
                        isFavorite = game.isFavorite,
                        onToggle = onFavoriteToggle,
                        activeColor = Color(0xFF00E5FF),
                    )
                }
            }

            // Luminous blue-cyan glass hairline divider
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color(0x8800E5FF), Color(0x882979FF), Color.Transparent),
                                ),
                        ),
            )

            Text(
                text = game.displayName(),
                color = Color(0xFFEEF6FF),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TvGameItem(
    game: Game,
    onFavoriteToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bezelShape = RoundedCornerShape(18.dp)
    val screenShape = RoundedCornerShape(13.dp)
    val channelNumber = ((game.id % 99) + 1).toString().padStart(2, '0')

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Authentic molded retro CRT bezel
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(bezelShape)
                    .background(
                        brush =
                            Brush.verticalGradient(
                                listOf(Color(0xFF473F39), Color(0xFF2B2521), Color(0xFF1B1714)),
                            ),
                    )
                    .border(
                        width = 1.8.dp,
                        brush =
                            Brush.verticalGradient(
                                listOf(Color(0xFF6E6259), Color(0xFF3E3732), Color(0xFF1A1613)),
                            ),
                        shape = bezelShape,
                    )
                    .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        ) {
            // Recessed rounded CRT phosphor glass screen
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(screenShape)
                        .background(Color(0xFF060A07))
                        .border(
                            width = 1.5.dp,
                            brush =
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0B0E0C), Color(0x4039FF14), Color(0xFF0A0D0B)),
                                ),
                            shape = screenShape,
                        ),
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Fine horizontal CRT scanlines + subtle phosphor edge glow
                Canvas(modifier = Modifier.matchParentSize()) {
                    // Subtle phosphor glow halo around tube edge
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color(0x0A39FF14),
                                        Color.Transparent,
                                        Color(0x45000000),
                                    ),
                                center = Offset(size.width * 0.5f, size.height * 0.5f),
                                radius = maxOf(size.width, size.height) * 0.72f,
                            ),
                    )

                    // Faint curved CRT glass top-left reflection
                    drawRect(
                        brush =
                            Brush.linearGradient(
                                colors = listOf(Color(0x1AFFFFFF), Color.Transparent),
                                start = Offset(0f, 0f),
                                end = Offset(size.width * 0.45f, size.height * 0.35f),
                            ),
                    )

                    // Fine horizontal scanlines
                    val step = 2.6.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color(0x30000000),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.9.dp.toPx(),
                        )
                        y += step
                    }
                }

                // Glowing phosphor green CH XX OSD badge
                Surface(
                    color = Color(0xCC041205),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.9.dp, Color(0x9939FF14)),
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                ) {
                    Text(
                        text = "CH $channelNumber",
                        color = Color(0xFF39FF14),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                shadow =
                                    Shadow(
                                        color = Color(0xCC39FF14),
                                        offset = Offset.Zero,
                                        blurRadius = 6f,
                                    ),
                            ),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                if (onFavoriteToggle != null) {
                    CompactFavoriteOverlay(
                        isFavorite = game.isFavorite,
                        onToggle = onFavoriteToggle,
                        activeColor = Color(0xFF39FF14),
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Authentic CRT bottom bezel speaker vents & phosphor power LED
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) {
                        Box(
                            modifier =
                                Modifier
                                    .width(5.dp)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF14110F)),
                        )
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF39FF14))
                            .border(0.7.dp, Color(0x88B8FFA8), CircleShape),
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = game.displayName(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Retro8090GameItem(
    game: Game,
    onFavoriteToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(11.dp)
    val innerShape = RoundedCornerShape(8.dp)
    val sunsetBorder =
        Brush.linearGradient(
            colors =
                listOf(
                    Color(0xFFFF2A85),
                    Color(0xFFFF8C00),
                    Color(0xFFB5179E),
                    Color(0xFF00F0FF),
                ),
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(outerShape)
                .background(
                    brush = Brush.verticalGradient(listOf(Color(0xFF12032E), Color(0xFF0A011C))),
                )
                .border(width = 2.dp, brush = sunsetBorder, shape = outerShape)
                .padding(3.dp)
                .border(width = 1.dp, color = Color(0x8800F0FF), shape = innerShape),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                        .background(Color(0xFF070114)),
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Retro synthwave system tag
                Surface(
                    color = Color(0xD90D0221),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF2A85)),
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                ) {
                    Text(
                        text = game.systemId.uppercase(),
                        color = Color(0xFF00F0FF),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                    )
                }

                if (onFavoriteToggle != null) {
                    CompactFavoriteOverlay(
                        isFavorite = game.isFavorite,
                        onToggle = onFavoriteToggle,
                        activeColor = Color(0xFFFF2A85),
                    )
                }
            }

            // Neon horizon divider
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF2A85), Color(0xFFFFD600), Color(0xFF00F0FF)),
                                ),
                        ),
            )

            // Retro perspective grid accent footer with glowing title
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 7.dp, bottomEnd = 7.dp))
                        .background(Color(0xFF11032B)),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    // Subtle synthwave horizontal grid lines
                    val h1 = size.height * 0.35f
                    val h2 = size.height * 0.7f
                    drawLine(Color(0x28FF2A85), Offset(0f, h1), Offset(size.width, h1), strokeWidth = 0.8.dp.toPx())
                    drawLine(Color(0x2400F0FF), Offset(0f, h2), Offset(size.width, h2), strokeWidth = 0.8.dp.toPx())

                    // Subtle converging perspective rays
                    val rays = 6
                    val centerX = size.width * 0.5f
                    for (i in 0..rays) {
                        val bottomX = size.width * (i.toFloat() / rays)
                        val topX = centerX + (bottomX - centerX) * 0.45f
                        drawLine(
                            color = Color(0x20FF2A85),
                            start = Offset(topX, 0f),
                            end = Offset(bottomX, size.height),
                            strokeWidth = 0.75.dp.toPx(),
                        )
                    }
                }

                Text(
                    text = game.displayName(),
                    color = Color(0xFF99FAFF),
                    style =
                        MaterialTheme.typography.labelMedium.copy(
                            shadow =
                                Shadow(
                                    color = Color(0xE6FF2A85),
                                    offset = Offset(0f, 0f),
                                    blurRadius = 8f,
                                ),
                        ),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun RockstarGameItem(
    game: Game,
    onFavoriteToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .border(width = 2.dp, color = Color(0xFFFFB800), shape = shape),
        shape = shape,
        color = Color(0xFF0E0E0E),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090909)),
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Bold gold star emblem badge at top-start
                Surface(
                    color = Color(0xFFFFB800),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, Color(0xFF111111)),
                    shadowElevation = 3.dp,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFF111111),
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = game.systemId.uppercase(),
                            color = Color(0xFF111111),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }

                if (onFavoriteToggle != null) {
                    CompactFavoriteOverlay(
                        isFavorite = game.isFavorite,
                        onToggle = onFavoriteToggle,
                        activeColor = Color(0xFFFFB800),
                        useStarIcon = true,
                    )
                } else if (game.isFavorite) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC111111))
                                .border(1.dp, Color(0xFFFFB800), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(R.string.favorites),
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // Angled gold & black poster accent bar
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(Color(0xFF111111)),
            ) {
                val slant = 12.dp.toPx()
                val goldPath =
                    Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width * 0.72f, 0f)
                        lineTo(size.width * 0.72f - slant, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                drawPath(path = goldPath, color = Color(0xFFFFB800))

                val accentPath =
                    Path().apply {
                        moveTo(size.width * 0.76f, 0f)
                        lineTo(size.width * 0.85f, 0f)
                        lineTo(size.width * 0.85f - slant, size.height)
                        lineTo(size.width * 0.76f - slant, size.height)
                        close()
                    }
                drawPath(path = accentPath, color = Color(0xFFFFD54F))
            }

            // High-contrast gold title bar
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFFB800), Color(0xFFFFCA28), Color(0xFFE5A400)),
                                ),
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = game.displayName(),
                    color = Color(0xFF0E0E0E),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (game.isFavorite) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFF0E0E0E),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThrashMetalGameItem(
    game: Game,
    onFavoriteToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val outerShape = RoundedCornerShape(4.dp)
    val innerShape = RoundedCornerShape(2.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(outerShape)
                .background(
                    brush =
                        Brush.verticalGradient(
                            listOf(Color(0xFF23262E), Color(0xFF14161B), Color(0xFF1D2027), Color(0xFF101216)),
                        ),
                )
                .border(width = 2.dp, color = Color(0xFFC41212), shape = outerShape)
                .drawWithContent {
                    // Subtle brushed-steel micro-streaks across the metal plate
                    val step = 4.dp.toPx()
                    var y = 2.dp.toPx()
                    while (y < size.height) {
                        drawLine(
                            color = Color(0x12FFFFFF),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 0.6.dp.toPx(),
                        )
                        y += step
                    }
                    drawContent()
                    drawCornerRivets()
                }
                .padding(4.dp)
                .border(width = 1.dp, color = Color(0xAA8F0D0D), shape = innerShape),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090A0D)),
            ) {
                LemuroidGameImage(
                    game = game,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (onFavoriteToggle != null) {
                    CompactFavoriteOverlay(
                        isFavorite = game.isFavorite,
                        onToggle = onFavoriteToggle,
                        activeColor = Color(0xFFE01B1B),
                    )
                }
            }

            // Forged crimson steel separator
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(
                            brush =
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7A0A0A), Color(0xFFC41212), Color(0xFF7A0A0A)),
                                ),
                        ),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(3.dp)
                            .height(12.dp)
                            .background(Color(0xFFC41212)),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = game.displayName(),
                    color = Color(0xFFF2F4F7),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun DrawScope.drawCornerRivets() {
    val radius = 3.2.dp.toPx()
    val inset = 6.5.dp.toPx()
    val corners =
        listOf(
            Offset(inset, inset),
            Offset(size.width - inset, inset),
            Offset(inset, size.height - inset),
            Offset(size.width - inset, size.height - inset),
        )
    corners.forEach { center ->
        // Dark recessed socket shadow
        drawCircle(
            color = Color(0xCC060709),
            radius = radius + 0.9.dp.toPx(),
            center = Offset(center.x + 0.4.dp.toPx(), center.y + 0.5.dp.toPx()),
        )
        // Brushed steel rivet body
        drawCircle(
            color = Color(0xFF686E7A),
            radius = radius,
            center = center,
        )
        // Diagonal screw slot
        drawLine(
            color = Color(0xFF23262D),
            start = Offset(center.x - radius * 0.55f, center.y + radius * 0.35f),
            end = Offset(center.x + radius * 0.55f, center.y - radius * 0.35f),
            strokeWidth = 0.9.dp.toPx(),
        )
        // Crisp metallic specular highlight
        drawCircle(
            color = Color(0xFFD6DCE6),
            radius = radius * 0.38f,
            center = Offset(center.x - radius * 0.3f, center.y - radius * 0.3f),
        )
    }
}

@Composable
private fun BoxScope.CompactFavoriteOverlay(
    isFavorite: Boolean,
    onToggle: (Boolean) -> Unit,
    activeColor: Color,
    useStarIcon: Boolean = false,
) {
    val icon =
        when {
            useStarIcon && isFavorite -> Icons.Default.Star
            useStarIcon -> Icons.Default.StarBorder
            isFavorite -> Icons.Default.Favorite
            else -> Icons.Default.FavoriteBorder
        }
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xB8000000))
                .border(0.75.dp, Color(0x33FFFFFF), CircleShape)
                .clickable { onToggle(!isFavorite) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(R.string.favorites),
            tint = if (isFavorite) activeColor else Color.White,
            modifier = Modifier.size(16.dp),
        )
    }
}
