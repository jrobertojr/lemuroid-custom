package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendLoop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadButtonIcon
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintBar
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

private enum class ConsoleDashboardStyle {
    ARCADE,
    SONY,
    NINTENDO,
    SEGA,
    RETRO_TERMINAL,
}

private fun resolveDashboardStyle(systemKey: String): ConsoleDashboardStyle {
    return when (systemKey.lowercase(Locale.ROOT)) {
        "arcade", "fbneo", "mame2003plus" -> ConsoleDashboardStyle.ARCADE
        "psx", "psp" -> ConsoleDashboardStyle.SONY
        "snes", "n64", "nes", "gba", "gb", "gbc", "nds", "3ds" -> ConsoleDashboardStyle.NINTENDO
        "md", "sms", "segacd", "gg" -> ConsoleDashboardStyle.SEGA
        else -> ConsoleDashboardStyle.RETRO_TERMINAL
    }
}

private val GroupedGameEntry.dashGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

private val GroupedGameEntry.dashDiscCount: Int
    get() =
        when (this) {
            is GroupedGameEntry.Single -> 1
            is GroupedGameEntry.MultiDisc -> discs.size
        }

private fun GroupedGameEntry.dashTitle(): String =
    when (this) {
        is GroupedGameEntry.Single -> game.displayName()
        is GroupedGameEntry.MultiDisc -> folderName.ifBlank { representative.displayName() }
    }

@Composable
fun ConsoleDashboardLayout(
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
    val safeSelectedIndex =
        if (games.isEmpty()) 0 else selectedIndex.coerceIn(0, games.lastIndex)
    val activeScreenshotUrl =
        if (screenshots.isNotEmpty()) {
            screenshots[screenshotIndex.coerceAtLeast(0) % screenshots.size]
        } else {
            null
        }

    when (resolveDashboardStyle(systemKey)) {
        ConsoleDashboardStyle.SONY ->
            SonyXmbDashboard(
                games = games,
                selectedIndex = safeSelectedIndex,
                systemTitle = systemTitle,
                systemKey = systemKey,
                activeScreenshotUrl = activeScreenshotUrl,
                description = description,
                onSelectIndex = onSelectIndex,
                onConfirmGame = onConfirmGame,
                modifier = modifier,
            )

        ConsoleDashboardStyle.NINTENDO ->
            NintendoSwitchDashboard(
                games = games,
                selectedIndex = safeSelectedIndex,
                systemTitle = systemTitle,
                activeScreenshotUrl = activeScreenshotUrl,
                description = description,
                onSelectIndex = onSelectIndex,
                onConfirmGame = onConfirmGame,
                modifier = modifier,
            )

        ConsoleDashboardStyle.SEGA ->
            SegaDreamcastMegaDashboard(
                games = games,
                selectedIndex = safeSelectedIndex,
                systemTitle = systemTitle,
                systemKey = systemKey,
                activeScreenshotUrl = activeScreenshotUrl,
                description = description,
                onSelectIndex = onSelectIndex,
                onConfirmGame = onConfirmGame,
                modifier = modifier,
            )

        ConsoleDashboardStyle.ARCADE ->
            ArcadeCabinetHallDashboard(
                games = games,
                selectedIndex = safeSelectedIndex,
                systemTitle = systemTitle,
                activeScreenshotUrl = activeScreenshotUrl,
                description = description,
                onSelectIndex = onSelectIndex,
                onConfirmGame = onConfirmGame,
                modifier = modifier,
            )

        ConsoleDashboardStyle.RETRO_TERMINAL ->
            RetroWoodgrainTerminalDashboard(
                games = games,
                selectedIndex = safeSelectedIndex,
                systemTitle = systemTitle,
                systemKey = systemKey,
                activeScreenshotUrl = activeScreenshotUrl,
                description = description,
                onSelectIndex = onSelectIndex,
                onConfirmGame = onConfirmGame,
                modifier = modifier,
            )
    }
}

// =================================================================================================
// SHARED LARGE SCREENSHOT STAGE & NON-CLIPPED PLAY ACTION BUTTON
// =================================================================================================

@Composable
private fun DashboardScreenshotMedia(
    game: Game?,
    screenshotUrl: String?,
    showScanlines: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    Box(
        modifier = modifier.background(Color(0xFF060910)),
        contentAlignment = Alignment.Center,
    ) {
        if (screenshotUrl != null) {
            Crossfade(
                targetState = screenshotUrl,
                animationSpec = tween(durationMillis = motion.mediaMillis),
                label = "dashboardScreenshotCrossfade",
                modifier = Modifier.fillMaxSize(),
            ) { url ->
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(false)
                            .build(),
                    contentDescription = game?.displayName(),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (game != null) {
            LemuroidGameImage(
                game = game,
                contentScale = ContentScale.Fit,
                preserveAspect = true,
                modifier = Modifier.fillMaxSize().padding(10.dp),
            )
        }

        if (showScanlines) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var y = 0f
                val step = 3.5.dp.toPx()
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.22f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    y += step
                }
            }
        }
    }
}

@Composable
private fun DashboardPlayActionButton(
    label: String,
    accentStart: Color,
    accentEnd: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            modifier
                .height(40.dp)
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(accentStart, accentEnd)))
                .border(1.2.dp, Color.White.copy(alpha = 0.40f), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GamepadButtonIcon(glyph = GamepadGlyph.A, size = 20.dp)
        Text(
            text = label,
            color = textColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// =================================================================================================
// 1. SONY — AUTHENTIC PLAYSTATION XMB (CROSS MEDIA BAR)
//    Horizontal category crossbar at top + vertical translucent game spine on left + HUGE preview
// =================================================================================================

@Composable
private fun SonyXmbDashboard(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    systemKey: String,
    activeScreenshotUrl: String?,
    description: String?,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val selectedEntry = games.getOrNull(selectedIndex)
    val selectedGame = selectedEntry?.dashGame
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, games.size) {
        if (games.isNotEmpty()) {
            if (motion.animatedScroll) listState.animateScrollToItem(selectedIndex.coerceIn(0, games.lastIndex)) else listState.scrollToItem(selectedIndex.coerceIn(0, games.lastIndex))
        }
    }

    val wavePhase by rememberFrontendLoop(targetValue = (2.0 * PI).toFloat(), durationMillis = 9000)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF061638), Color(0xFF0D2E6B), Color(0xFF051128)),
                    ),
                ),
    ) {
        // Signature PlayStation XMB flowing silk ribbon wave
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for (ribbon in 0..2) {
                val baseY = h * (0.42f + ribbon * 0.11f)
                val amp = h * (0.045f + ribbon * 0.015f)
                val path =
                    Path().apply {
                        moveTo(0f, baseY)
                        var x = 0f
                        while (x <= w) {
                            val t = (x / w) * 2.2f * PI.toFloat() + wavePhase + ribbon * 1.1f
                            lineTo(x, baseY + sin(t) * amp)
                            x += 24f
                        }
                    }
                drawPath(
                    path = path,
                    color = Color(0xFF56B8FF).copy(alpha = 0.16f - ribbon * 0.035f),
                    style = Stroke(width = (3.5f - ribbon * 0.8f).dp.toPx()),
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Authentic XMB Top Horizontal Crossbar Categories
            val categories = listOf("CONFIG", "FOTO", "MÚSICA", "VÍDEO", "JOGOS", "REDE")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    categories.forEach { cat ->
                        val isActive = cat == "JOGOS"
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(if (isActive) 34.dp else 26.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive) Color(0xFF2896FF).copy(alpha = 0.35f)
                                            else Color.White.copy(alpha = 0.08f),
                                        )
                                        .border(
                                            width = if (isActive) 1.5.dp else 1.dp,
                                            color =
                                                if (isActive) Color(0xFF8CD3FF)
                                                else Color.White.copy(alpha = 0.20f),
                                            shape = CircleShape,
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (isActive) "🎮" else cat.take(1),
                                    color = if (isActive) Color.White else Color(0xFF9EBBE0),
                                    fontSize = if (isActive) 14.sp else 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cat,
                                color = if (isActive) Color.White else Color(0xFF8AA8CE),
                                fontSize = if (isActive) 10.sp else 12.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }

                // Right PlayStation System Status Pill
                Surface(
                    color = Color(0x44000000),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color(0x5582C6FF)),
                ) {
                    Text(
                        text = "${systemTitle.uppercase(Locale.ROOT)}  •  ${games.size} TÍTULOS",
                        color = Color(0xFFD6ECFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            // Main XMB Body: Vertical translucent game spine (35%) + HUGE floating preview stage (65%)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Vertical XMB Game Spine
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .weight(0.36f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(games, key = { idx, e -> "${e.dashGame.id}_$idx" }) { idx, entry ->
                        val isSel = idx == selectedIndex
                        val rowShape = RoundedCornerShape(10.dp)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .background(
                                        if (isSel) {
                                            Brush.horizontalGradient(
                                                listOf(Color(0x552DA4FF), Color(0x220B234A)),
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(Color.Transparent, Color.Transparent),
                                            )
                                        },
                                    )
                                    .border(
                                        width = if (isSel) 1.5.dp else 0.dp,
                                        color = if (isSel) Color(0xFF9CD8FF) else Color.Transparent,
                                        shape = rowShape,
                                    )
                                    .clickable {
                                        if (isSel) onConfirmGame(entry) else onSelectIndex(idx)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(if (isSel) 42.dp else 32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x33000000)),
                            ) {
                                LemuroidGameImage(
                                    game = entry.dashGame,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.dashTitle(),
                                    color = if (isSel) Color.White else Color(0xFFB3CCE8),
                                    fontSize = if (isSel) 13.5.sp else 12.sp,
                                    fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isSel) {
                                    Text(
                                        text = if (entry.dashDiscCount > 1) "${entry.dashDiscCount} Discos" else systemKey.uppercase(Locale.ROOT),
                                        color = Color(0xFF8CD3FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }

                // Right 64%: HUGE XMB Floating Preview Stage + Clean Info & Action Strip
                Column(
                    modifier =
                        Modifier
                            .weight(0.64f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // LARGE Game Screenshot Showcase
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, Color(0x778CD3FF), RoundedCornerShape(14.dp)),
                    ) {
                        DashboardScreenshotMedia(
                            game = selectedGame,
                            screenshotUrl = activeScreenshotUrl,
                            showScanlines = false,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Guaranteed Non-Clipped XMB Title + Description + Action Row
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x66061329))
                                .border(1.dp, Color(0x448CD3FF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedEntry?.dashTitle() ?: systemTitle,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text =
                                    description?.takeIf { it.isNotBlank() }
                                        ?: "Disco PlayStation pronto no leitor óptico.",
                                color = Color(0xFFBDD8F5),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (selectedEntry != null) {
                            DashboardPlayActionButton(
                                label = "INICIAR",
                                accentStart = Color(0xFF1F7AE0),
                                accentEnd = Color(0xFF0D47A1),
                                onClick = { onConfirmGame(selectedEntry) },
                            )
                        }
                    }
                }
            }

            // Standardized Bottom Gamepad Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                GamepadHintBar(
                    hints =
                        listOf(
                            GamepadGlyph.DPAD_UD to "Navegar",
                            GamepadGlyph.A to "Iniciar Disco",
                            GamepadGlyph.B to "Voltar ao Quarto",
                        ),
                    containerColor = Color(0x77051126),
                    borderColor = Color(0x448CD3FF),
                )
            }
        }
    }
}

// =================================================================================================
// 2. NINTENDO — AUTHENTIC NINTENDO SWITCH HOME OS
//    Top status bar + HUGE upper screenshot hero + Bottom horizontal square-tile carousel
// =================================================================================================

@Composable
private fun NintendoSwitchDashboard(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    activeScreenshotUrl: String?,
    description: String?,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val selectedEntry = games.getOrNull(selectedIndex)
    val selectedGame = selectedEntry?.dashGame
    val rowState = rememberLazyListState()

    LaunchedEffect(selectedIndex, games.size) {
        if (games.isNotEmpty()) {
            if (motion.animatedScroll) rowState.animateScrollToItem(selectedIndex.coerceIn(0, games.lastIndex)) else rowState.scrollToItem(selectedIndex.coerceIn(0, games.lastIndex))
        }
    }

    val pulseAlpha by rememberFrontendLoop(initialValue = 0.85f, targetValue = 1f, durationMillis = 1400, reverse = true)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF1E2127))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Authentic Switch Top Status Bar (Avatar + System | Battery/Wi-Fi)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE60012))
                            .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("N", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Text(
                    text = systemTitle.uppercase(Locale.ROOT),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${selectedIndex + 1} / ${games.size.coerceAtLeast(1)}",
                    color = Color(0xFF00E5FF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2E333D))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "100% ▮",
                        color = Color(0xFF69F0AE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // UPPER HERO (54% height): HUGE Screenshot Preview (62%) + Info & Play Card (38%)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.56f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(0.60f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .border(2.dp, Color(0xFF3A404D), RoundedCornerShape(14.dp)),
            ) {
                DashboardScreenshotMedia(
                    game = selectedGame,
                    screenshotUrl = activeScreenshotUrl,
                    showScanlines = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF262A33))
                        .border(1.dp, Color(0xFF3A404D), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = selectedEntry?.dashTitle() ?: systemTitle,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            description?.takeIf { it.isNotBlank() }
                                ?: "Cartucho Nintendo inserido e pronto para jogar.",
                        color = Color(0xFFB8C2D1),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Guaranteed Non-Clipped Joy-Con Play Action Row
                if (selectedEntry != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DashboardPlayActionButton(
                            label = "JOGAR",
                            accentStart = Color(0xFFE60012),
                            accentEnd = Color(0xFFB7000E),
                            onClick = { onConfirmGame(selectedEntry) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // LOWER CAROUSEL (44% height): Authentic Switch Square-Tile Row with Pulsing Cyan Border
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.44f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = selectedEntry?.dashTitle() ?: "",
                color = Color(0xFF00E5FF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            LazyRow(
                state = rowState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(games, key = { idx, e -> "${e.dashGame.id}_$idx" }) { idx, entry ->
                    val isSel = idx == selectedIndex
                    val tileShape = RoundedCornerShape(10.dp)
                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight(if (isSel) 0.96f else 0.82f)
                                .aspectRatio(1f)
                                .clip(tileShape)
                                .background(Color(0xFF14171C))
                                .border(
                                    width = if (isSel) 3.5.dp else 1.dp,
                                    color =
                                        if (isSel) Color(0xFF00E5FF).copy(alpha = pulseAlpha)
                                        else Color(0xFF3B414D),
                                    shape = tileShape,
                                )
                                .clickable {
                                    if (isSel) onConfirmGame(entry) else onSelectIndex(idx)
                                },
                    ) {
                        LemuroidGameImage(
                            game = entry.dashGame,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Authentic Switch Bottom Controller Bar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(width = 0.dp, color = Color.Transparent)
                    .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NINTENDO HOME MENU",
                color = Color(0xFF8B95A5),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            GamepadHintBar(
                hints =
                    listOf(
                        GamepadGlyph.DPAD_LR to "Escolher",
                        GamepadGlyph.A to "Iniciar",
                        GamepadGlyph.B to "Voltar",
                    ),
                containerColor = Color(0xFF282C35),
                borderColor = Color(0xFF3E4552),
            )
        }
    }
}

// =================================================================================================
// 3. SEGA — AUTHENTIC SEGA DREAMCAST / MEGA DRIVE MINI DECK
//    LEFT 62% HUGE Widescreen Monitor + RIGHT 38% Metallic Blue Angled Cartridge/GD-ROM Rack
// =================================================================================================

@Composable
private fun SegaDreamcastMegaDashboard(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    systemKey: String,
    activeScreenshotUrl: String?,
    description: String?,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val selectedEntry = games.getOrNull(selectedIndex)
    val selectedGame = selectedEntry?.dashGame
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, games.size) {
        if (games.isNotEmpty()) {
            if (motion.animatedScroll) listState.animateScrollToItem(selectedIndex.coerceIn(0, games.lastIndex)) else listState.scrollToItem(selectedIndex.coerceIn(0, games.lastIndex))
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF071A3B), Color(0xFF0E3266), Color(0xFF051126)),
                    ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Sega Metallic Header Banner with Dreamcast Orange Spiral Accent
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                                .background(Color(0xFF0089FF))
                                .border(1.dp, Color(0xFF80D8FF), CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "SEGA",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.5.sp,
                        )
                    }
                    Text(
                        text = systemTitle.uppercase(Locale.ROOT),
                        color = Color(0xFFE3F2FD),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }

                Text(
                    text = "${selectedIndex + 1} / ${games.size.coerceAtLeast(1)}  •  ${systemKey.uppercase(Locale.ROOT)}",
                    color = Color(0xFFFF9100),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            // INVERTED SPLIT: HUGE Monitor on LEFT (62%) + Angled Sega Cartridge List on RIGHT (38%)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // LEFT 62%: Giant Sega Preview Monitor
                Box(
                    modifier =
                        Modifier
                            .weight(0.62f)
                            .fillMaxHeight()
                            .clip(CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp))
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFFF6D00))),
                                CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
                            ),
                ) {
                    DashboardScreenshotMedia(
                        game = selectedGame,
                        screenshotUrl = activeScreenshotUrl,
                        showScanlines = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // RIGHT 38%: Metallic Sega Cartridge / GD-ROM Selector
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .weight(0.38f)
                            .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    itemsIndexed(games, key = { idx, e -> "${e.dashGame.id}_$idx" }) { idx, entry ->
                        val isSel = idx == selectedIndex
                        val slotShape = CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp)
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(slotShape)
                                    .background(
                                        if (isSel) {
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF0077E6), Color(0xFF004599)),
                                            )
                                        } else {
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF0B2247), Color(0xFF081733)),
                                            )
                                        },
                                    )
                                    .border(
                                        width = if (isSel) 1.8.dp else 1.dp,
                                        color = if (isSel) Color(0xFFFF9100) else Color(0x3364B5F6),
                                        shape = slotShape,
                                    )
                                    .clickable {
                                        if (isSel) onConfirmGame(entry) else onSelectIndex(idx)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF061124)),
                            ) {
                                LemuroidGameImage(
                                    game = entry.dashGame,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Text(
                                text = entry.dashTitle(),
                                color = if (isSel) Color.White else Color(0xFFAED4F8),
                                fontSize = 12.5.sp,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Dedicated Full-Width Non-Clipped Sega Action Deck
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                        .background(Color(0xAA06142B))
                        .border(1.dp, Color(0x5500E5FF), CutCornerShape(topStart = 10.dp, bottomEnd = 10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedEntry?.dashTitle() ?: systemTitle,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = description?.takeIf { it.isNotBlank() } ?: "SEGA 16-BIT / CD READY.",
                        color = Color(0xFF9AC8F5),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (selectedEntry != null) {
                    DashboardPlayActionButton(
                        label = "PRESS START",
                        accentStart = Color(0xFFFF6D00),
                        accentEnd = Color(0xFFD54E00),
                        onClick = { onConfirmGame(selectedEntry) },
                    )
                }

                GamepadHintPill(glyph = GamepadGlyph.B, label = "Voltar", textColor = Color(0xFFCDE7FF))
            }
        }
    }
}

// =================================================================================================
// 4. ARCADE — AUTHENTIC NEO GEO / CPS2 CABINET ATTRACT MODE
//    Illuminated Neon Marquee Top + Left High-Score List + GIANT Curved CRT Attract Monitor
// =================================================================================================

@Composable
private fun ArcadeCabinetHallDashboard(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    activeScreenshotUrl: String?,
    description: String?,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val selectedEntry = games.getOrNull(selectedIndex)
    val selectedGame = selectedEntry?.dashGame
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, games.size) {
        if (games.isNotEmpty()) {
            if (motion.animatedScroll) listState.animateScrollToItem(selectedIndex.coerceIn(0, games.lastIndex)) else listState.scrollToItem(selectedIndex.coerceIn(0, games.lastIndex))
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF140724), Color(0xFF0A0514), Color(0xFF1A0928)),
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Illuminated Arcade Marquee Banner
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF2B0B4E), Color(0xFF6A0F49), Color(0xFF2B0B4E)),
                        ),
                    )
                    .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "★  ${systemTitle.uppercase(Locale.ROOT)}  •  ARCADE HALL  ★",
                color = Color(0xFFFFE57F),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = "CREDIT 99  •  FREE PLAY",
                color = Color(0xFF00E5FF),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // Main Arcade Cabinet Stage: Left Neon ROM List (36%) + Right GIANT CRT Monitor (64%)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(0.36f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0D0818))
                        .border(1.5.dp, Color(0x66FF4081), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                itemsIndexed(games, key = { idx, e -> "${e.dashGame.id}_$idx" }) { idx, entry ->
                    val isSel = idx == selectedIndex
                    val shape = RoundedCornerShape(7.dp)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (isSel) Color(0xFF3E105E) else Color.Transparent)
                                .border(
                                    width = if (isSel) 1.5.dp else 0.dp,
                                    color = if (isSel) Color(0xFFFFD54F) else Color.Transparent,
                                    shape = shape,
                                )
                                .clickable {
                                    if (isSel) onConfirmGame(entry) else onSelectIndex(idx)
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", idx + 1),
                            color = if (isSel) Color(0xFFFFD54F) else Color(0xFF8A6FA8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = entry.dashTitle(),
                            color = if (isSel) Color.White else Color(0xFFD2C4E8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Right 64%: GIANT Curved CRT Arcade Screen
            Box(
                modifier =
                    Modifier
                        .weight(0.64f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF050408))
                        .border(3.dp, Color(0xFF00E5FF), RoundedCornerShape(18.dp)),
            ) {
                DashboardScreenshotMedia(
                    game = selectedGame,
                    screenshotUrl = activeScreenshotUrl,
                    showScanlines = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Dedicated Non-Clipped Arcade Control Deck
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF160B24))
                    .border(1.dp, Color(0x55FFD54F), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedEntry?.dashTitle() ?: systemTitle,
                    color = Color(0xFFFFE57F),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description?.takeIf { it.isNotBlank() } ?: "INSIRA FICHA E PRESSIONE A PARA JOGAR.",
                    color = Color(0xFFD0C2E6),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selectedEntry != null) {
                DashboardPlayActionButton(
                    label = "INSERT COIN",
                    accentStart = Color(0xFFFF1744),
                    accentEnd = Color(0xFFC51162),
                    onClick = { onConfirmGame(selectedEntry) },
                )
            }

            GamepadHintPill(glyph = GamepadGlyph.B, label = "Sair", textColor = Color(0xFFEAD9FF))
        }
    }
}

// =================================================================================================
// 5. RETRO TERMINAL (ATARI / PC ENGINE / DOS) — AUTHENTIC 80s WOODGRAIN & PHOSPHOR CRT DECK
// =================================================================================================

@Composable
private fun RetroWoodgrainTerminalDashboard(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    systemKey: String,
    activeScreenshotUrl: String?,
    description: String?,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val selectedEntry = games.getOrNull(selectedIndex)
    val selectedGame = selectedEntry?.dashGame
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex, games.size) {
        if (games.isNotEmpty()) {
            if (motion.animatedScroll) listState.animateScrollToItem(selectedIndex.coerceIn(0, games.lastIndex)) else listState.scrollToItem(selectedIndex.coerceIn(0, games.lastIndex))
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color(0xFF1A110B))
                .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Woodgrain Top Plate
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF4A2B18), Color(0xFF6E4124), Color(0xFF4A2B18))))
                    .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "C:\\${systemKey.uppercase(Locale.ROOT)}\\GAMES>  ${systemTitle.uppercase(Locale.ROOT)}",
                color = Color(0xFFFFCC80),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "${games.size} FILES",
                color = Color(0xFF81C784),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Main Split: Left 38% Phosphor Terminal Listing + Right 62% Large CRT Monitor
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .weight(0.38f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF090E08))
                        .border(1.5.dp, Color(0xFF4CAF50).copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(games, key = { idx, e -> "${e.dashGame.id}_$idx" }) { idx, entry ->
                    val isSel = idx == selectedIndex
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSel) Color(0xFF1B3B1E) else Color.Transparent)
                                .clickable {
                                    if (isSel) onConfirmGame(entry) else onSelectIndex(idx)
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isSel) "> ${entry.dashTitle()}" else "  ${entry.dashTitle()}",
                            color = if (isSel) Color(0xFF69F0AE) else Color(0xFFA5D6A7),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .weight(0.62f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .border(2.dp, Color(0xFFFFB74D), RoundedCornerShape(14.dp)),
            ) {
                DashboardScreenshotMedia(
                    game = selectedGame,
                    screenshotUrl = activeScreenshotUrl,
                    showScanlines = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Dedicated Non-Clipped Bottom Woodgrain Rocker Switch Bar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF261810))
                    .border(1.dp, Color(0x55FFB74D), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedEntry?.dashTitle() ?: systemTitle,
                    color = Color(0xFFFFE0B2),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description?.takeIf { it.isNotBlank() } ?: "READY TO LOAD.",
                    color = Color(0xFFC5B39F),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (selectedEntry != null) {
                DashboardPlayActionButton(
                    label = "RUN",
                    accentStart = Color(0xFF2E7D32),
                    accentEnd = Color(0xFF1B5E20),
                    onClick = { onConfirmGame(selectedEntry) },
                )
            }

            GamepadHintPill(glyph = GamepadGlyph.B, label = "Voltar", textColor = Color(0xFFFFE0B2))
        }
    }
}
