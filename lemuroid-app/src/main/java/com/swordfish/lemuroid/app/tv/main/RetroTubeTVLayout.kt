package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GeneratedThemeBackdrop
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.themeArtwork


import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendLoop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.Game3DObject
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.RetroCrtTv3DView
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val RoomWoodDark = Color(0xFF140C08)
private val RoomWoodMid = Color(0xFF21140D)
private val RoomWoodWarm = Color(0xFF2E1B11)
private val CrtWoodLight = Color(0xFF6B3F24)
private val CrtWoodMid = Color(0xFF4A2A17)
private val CrtWoodDark = Color(0xFF2B170C)
private val CrtCharcoal = Color(0xFF1E2024)
private val CrtAmber = Color(0xFFFFB74D)
private val CrtCyan = Color(0xFF38E8FF)
private val CrtOsdGreen = Color(0xFF4CFF6A)
private val CrtPowerRed = Color(0xFFFF2626)
private val GuideBgDark = Color(0xFF0B0E14)
private val GuideHeaderBg = Color(0xFF121824)

private val GroupedGameEntry.retroGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

private val GroupedGameEntry.retroVersionCount: Int
    get() =
        when (this) {
            is GroupedGameEntry.Single -> 1
            is GroupedGameEntry.MultiDisc -> discs.size
        }

@Composable
fun RetroTubeTVLayout(
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
    if (games.isEmpty()) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(RoomWoodDark),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "SEM SINAL — NENHUM CANAL EM ${systemTitle.uppercase(Locale.ROOT)}",
                color = CrtAmber,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

    val safeIndex = selectedIndex.coerceIn(0, games.lastIndex)
    val selectedEntry = games[safeIndex]
    val selectedGame = selectedEntry.retroGame
    val channelLabel = formatChannelNumber(safeIndex + 1)

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(RoomWoodDark),
    ) {
        // Left half (weight 0.54f): Warm vintage living room + 3D Angled CRT TV + 3D Cartridge + Teletext Info
        Box(
            modifier =
                Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(RoomWoodWarm, RoomWoodMid, RoomWoodDark),
                        ),
                    ),
        ) {
            GeneratedThemeBackdrop(FrontendTheme.TV, Modifier.fillMaxSize())
            // Subtle warm vintage wood-panel backdrop & ambient lamp glow
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Warm radial ambient lamp glow behind the CRT
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color(0x38FF9D3B),
                                    Color(0x14FF7B24),
                                    Color.Transparent,
                                ),
                            center = Offset(size.width * 0.42f, size.height * 0.36f),
                            radius = size.width * 0.58f,
                        ),
                    radius = size.width * 0.58f,
                    center = Offset(size.width * 0.42f, size.height * 0.36f),
                )

                // Subtle wood paneling vertical grooves
                val plankWidth = 64.dp.toPx()
                var x = plankWidth
                while (x < size.width) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.22f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    x += plankWidth
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top showcase row: 3D Angled CRT Tube TV + 3D Cartridge beside it
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 3D Angled Retro CRT Tube TV
                    RetroCrtTubeTV(
                        game = selectedGame,
                        channelLabel = channelLabel,
                        selectedIndex = safeIndex,
                        screenshots = screenshots,
                        screenshotIndex = screenshotIndex,
                        onScreenClick = { onConfirmGame(selectedEntry) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                    )

                    // 3D Cartridge / Game Box beside the TV
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.width(142.dp),
                    ) {
                        Text(
                            text = systemKey.uppercase(Locale.ROOT),
                            color = CrtAmber.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Game3DObject(
                            game = selectedGame,
                            onClick = { onConfirmGame(selectedEntry) },
                            onLongClick = {},
                            isSelected = true,
                            modifier = Modifier.size(135.dp),
                        )
                        // Wooden shelf ledge under the cartridge
                        Box(
                            modifier =
                                Modifier
                                    .width(120.dp)
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(CrtWoodDark, CrtWoodLight, CrtWoodDark),
                                        ),
                                    )
                                    .border(
                                        0.75.dp,
                                        Color(0x44FFD9A0),
                                        RoundedCornerShape(3.dp),
                                    ),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Below the TV: Strict fixed-height teletext panel so the 3D TV NEVER shifts vertically
                RetroTeletextInfoPanel(
                    entry = selectedEntry,
                    channelLabel = channelLabel,
                    systemTitle = systemTitle,
                    description = description,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(114.dp),
                )
            }
        }

        // Subtle vertical wood/metallic separator
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(CrtAmber.copy(alpha = 0.35f), CrtCyan.copy(alpha = 0.25f), CrtAmber.copy(alpha = 0.35f)),
                        ),
                    ),
        )

        // Right half (weight 0.46f): Classic Text Channel-Guide Game List
        RetroChannelGuidePanel(
            games = games,
            selectedIndex = safeIndex,
            systemTitle = systemTitle,
            onSelectIndex = onSelectIndex,
            onConfirmGame = onConfirmGame,
            modifier =
                Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
        )
    }
}

@Composable
private fun RetroCrtTubeTV(
    game: Game,
    channelLabel: String,
    selectedIndex: Int,
    screenshots: List<String>,
    screenshotIndex: Int,
    onScreenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val activeScreenshotUrl =
        if (screenshots.isNotEmpty()) {
            screenshots[screenshotIndex.crtFloorMod(screenshots.size)]
        } else {
            null
        }
    val screenMediaKey = game to activeScreenshotUrl

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val boxW = maxWidth
        val boxH = maxHeight

        // Genuine OpenGL ES 2.0 3D CRT TV chassis with rotating 3D VHF/UHF knobs & rabbit-ear antennas
        RetroCrtTv3DView(
            channelIndex = selectedIndex,
            modifier = Modifier.fillMaxSize(),
        )

        // Live recessed CRT glass screen aligned cleanly inside the 3D TV bezel aperture
        val screenH = (boxH * 0.515f).coerceAtLeast(108.dp)
        val screenW = (boxH * 0.675f).coerceAtMost(boxW * 0.58f)
        val offsetX = -(boxH * 0.088f)
        val offsetY = -(boxH * 0.005f)

        Box(
            modifier =
                Modifier
                    .offset(x = offsetX, y = offsetY)
                    .width(screenW)
                    .height(screenH)
                    .graphicsLayer {
                        rotationY = 3.8f
                        cameraDistance = 18f * density
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF04070B))
                    .border(2.dp, Color(0xFF14181F), RoundedCornerShape(14.dp))
                    .clickable(onClick = onScreenClick),
        ) {
            Crossfade(
                targetState = screenMediaKey,
                animationSpec = tween(durationMillis = motion.mediaMillis),
                label = "crt_3d_screen_media",
                modifier = Modifier.fillMaxSize(),
            ) { (mediaGame, mediaUrl) ->
                var mediaFailed by remember(mediaUrl) { mutableStateOf(false) }
                if (mediaUrl != null && !mediaFailed) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data(mediaUrl)
                                .crossfade(false)
                                .build(),
                        onError = { mediaFailed = true },
                        contentDescription = mediaGame.displayName(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LemuroidGameImage(
                        game = mediaGame,
                        contentScale = ContentScale.Fit,
                        preserveAspect = true,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                    )
                }
            }

            // Authentic CRT scanlines + phosphor glass curvature vignette
            Canvas(modifier = Modifier.fillMaxSize()) {
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.24f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    y += 3.5.dp.toPx()
                }
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.48f),
                                ),
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = size.maxDimension * 0.64f,
                        ),
                )
            }

            // Classic glowing green CRT OSD overlays ("AV-1" & "CH 01")
            Text(
                text = "AV-1",
                color = CrtOsdGreen.copy(alpha = 0.88f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 7.dp),
            )
            Text(
                text = channelLabel,
                color = CrtOsdGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 7.dp, end = 10.dp)
                        .background(Color(0x88000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun RetroTeletextInfoPanel(
    entry: GroupedGameEntry,
    channelLabel: String,
    systemTitle: String,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val game = entry.retroGame
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier =
            modifier
                .clip(shape)
                .background(Color(0xFF10151E))
                .border(1.dp, CrtAmber.copy(alpha = 0.40f), shape),
    ) {
        // Classic Teletext RGBY top accent bar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(3.dp),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFE53935)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF43A047)))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CrtAmber))
            Box(modifier = Modifier.weight(1f).fillMaxHeight().background(CrtCyan))
        }

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CrtAmber)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = channelLabel,
                        color = Color.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                }

                Text(
                    text = game.displayName(),
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                if (game.isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = CrtAmber,
                        modifier = Modifier.size(15.dp),
                    )
                }

                Text(
                    text = systemTitle.uppercase(Locale.ROOT),
                    color = CrtCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val resolvedDescription =
                if (!description.isNullOrBlank()) {
                    description
                } else {
                    "Transmissão direta em $channelLabel — sintonize para jogar $systemTitle na TV de tubo 3D."
                }
            Text(
                text = resolvedDescription,
                color = Color(0xFFD0D8E4),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RetroChannelGuidePanel(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex, games.size) {
        if (games.isNotEmpty()) {
            if (motion.animatedScroll) listState.animateScrollToItem(selectedIndex.coerceIn(0, games.lastIndex)) else listState.scrollToItem(selectedIndex.coerceIn(0, games.lastIndex))
        }
    }

    Column(
        modifier =
            modifier
                .background(GuideBgDark),
    ) {
        // Channel Guide Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(GuideHeaderBg)
                    .border(width = 0.5.dp, color = CrtCyan.copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CrtOsdGreen),
                )
                Text(
                    text = "GUIA DE CANAIS • ${systemTitle.uppercase(Locale.ROOT)}",
                    color = CrtCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = "${selectedIndex + 1}/${games.size}",
                color = CrtAmber,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Scrollable Classic Channel-Guide List (NO per-item focusable())
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(
                items = games,
                key = { index, entry -> "${entry.retroGame.id}_$index" },
            ) { index, entry ->
                val isSelected = index == selectedIndex
                RetroChannelGuideRow(
                    channelNumber = index + 1,
                    entry = entry,
                    isSelected = isSelected,
                    onClick = {
                        if (isSelected) {
                            onConfirmGame(entry)
                        } else {
                            onSelectIndex(index)
                        }
                    },
                )
            }
        }

        // Bottom Retro Broadcast Legend Bar with standardized gamepad icons
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(GuideHeaderBg)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GamepadHintPill(
                    glyph = GamepadGlyph.DPAD_UD,
                    label = "Canal",
                    textColor = Color(0xFFB5C4D8),
                    iconSize = 17.dp,
                    fontSizeSp = 10.5f,
                )
                GamepadHintPill(
                    glyph = GamepadGlyph.A,
                    label = "Sintonizar",
                    textColor = Color(0xFFB5C4D8),
                    iconSize = 17.dp,
                    fontSizeSp = 10.5f,
                )
            }
            GamepadHintPill(
                glyph = GamepadGlyph.Y,
                label = "Favorito",
                textColor = CrtAmber,
                iconSize = 17.dp,
                fontSizeSp = 10.5f,
            )
        }
    }
}

@Composable
private fun RetroChannelGuideRow(
    channelNumber: Int,
    entry: GroupedGameEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val game = entry.retroGame
    val versionCount = entry.retroVersionCount
    val channelText = formatChannelNumber(channelNumber)
    val shape = RoundedCornerShape(6.dp)

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) CrtCyan else Color(0xFF1D2534),
        animationSpec = tween(140),
        label = "crt_guide_row_border",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    if (isSelected) {
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    Color(0xFF2B220B),
                                    Color(0xFF132D38),
                                    Color(0xFF0E202B),
                                ),
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF101520), Color(0xFF0E131D)),
                        )
                    },
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Channel Number Pill ("CH 01")
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) CrtAmber else Color(0xFF1A2232))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                text = channelText,
                color = if (isSelected) Color.Black else CrtAmber.copy(alpha = 0.80f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // Game Title in crisp monospace retro typography
        Text(
            text = game.displayName(),
            color = if (isSelected) Color.White else Color(0xFFCFD8E6),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Favorite star
        if (game.isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = CrtAmber,
                modifier = Modifier.size(15.dp),
            )
        }

        // Multi-disc / Version badge
        if (versionCount > 1) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) CrtCyan.copy(alpha = 0.22f) else Color(0xFF1A2333))
                        .border(
                            width = 0.75.dp,
                            color = if (isSelected) CrtCyan else Color(0xFF334159),
                            shape = RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = "${versionCount}v",
                    color = if (isSelected) CrtCyan else Color(0xFF9EB0C8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatChannelNumber(channel: Int): String =
    String.format(Locale.US, "CH %02d", channel)

private fun Int.crtFloorMod(modulus: Int): Int {
    if (modulus <= 0) return 0
    val rem = this % modulus
    return if (rem < 0) rem + modulus else rem
}
