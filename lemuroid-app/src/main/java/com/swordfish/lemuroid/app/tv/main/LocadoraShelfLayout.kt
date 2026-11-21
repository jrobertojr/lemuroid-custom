package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GeneratedThemeBackdrop
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.themeArtwork


import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendLoop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.lib.library.db.entity.Game

private const val SHELF_COLUMNS = 6

private val ShelfWoodDark = Color(0xFF140B07)
private val ShelfWoodBack = Color(0xFF23130B)
private val ShelfWoodMid = Color(0xFF4A2B18)
private val ShelfWoodLight = Color(0xFF734526)
private val ShelfWoodHighlight = Color(0xFF966038)
private val ShelfVelvetCard = Color(0xE81C110C)
private val ShelfGold = Color(0xFFFFC107)
private val ShelfGoldSoft = Color(0xFFFFE082)
private val ShelfGoldDark = Color(0xFF8C5E00)
private val ShelfCream = Color(0xFFF7EFE6)
private val ShelfMuted = Color(0xFFCBB49D)

private enum class BoxPatternType {
    NINTENDO_RED_GOLD,
    SEGA_GRID,
    PSX_JEWEL,
    NES_SILVER_STRIPE,
    HANDHELD_COMPACT,
    NDS_WHITE_SPINE,
    PSP_UMD,
    ARCADE_NEON,
}

private data class ConsoleBoxStyle(
    val consoleTag: String,
    val spineShortLabel: String,
    val boxBaseColor: Color,
    val spinePrimaryColor: Color,
    val spineAccentColor: Color,
    val spineTextColor: Color,
    val headerBandColor: Color,
    val frontAspectRatio: Float,
    val patternType: BoxPatternType,
)

private val GroupedGameEntry.shelfGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

private val GroupedGameEntry.shelfTitle: String
    get() =
        when (this) {
            is GroupedGameEntry.Single ->
                game.customName?.takeIf { it.isNotBlank() } ?: game.title
            is GroupedGameEntry.MultiDisc ->
                representative.customName?.takeIf { it.isNotBlank() }
                    ?: folderName.ifBlank { representative.title }
        }

private val GroupedGameEntry.shelfVersionCount: Int
    get() =
        when (this) {
            is GroupedGameEntry.Single -> 1
            is GroupedGameEntry.MultiDisc -> discs.size
        }

private fun resolveBoxStyle(systemId: String): ConsoleBoxStyle {
    return when (systemId.trim().lowercase()) {
        "snes" ->
            ConsoleBoxStyle(
                consoleTag = "SUPER NINTENDO",
                spineShortLabel = "SNES",
                boxBaseColor = Color(0xFF1A191E),
                spinePrimaryColor = Color(0xFF212026),
                spineAccentColor = Color(0xFFD92626),
                spineTextColor = Color(0xFFF5F3EE),
                headerBandColor = Color(0xFFC62828),
                frontAspectRatio = 1.36f,
                patternType = BoxPatternType.NINTENDO_RED_GOLD,
            )

        "n64" ->
            ConsoleBoxStyle(
                consoleTag = "NINTENDO 64",
                spineShortLabel = "N64",
                boxBaseColor = Color(0xFF181A20),
                spinePrimaryColor = Color(0xFF1D2028),
                spineAccentColor = Color(0xFFE53935),
                spineTextColor = Color(0xFFF7F5EF),
                headerBandColor = Color(0xFFB71C1C),
                frontAspectRatio = 1.34f,
                patternType = BoxPatternType.NINTENDO_RED_GOLD,
            )

        "md", "genesis" ->
            ConsoleBoxStyle(
                consoleTag = "MEGA DRIVE",
                spineShortLabel = "MD",
                boxBaseColor = Color(0xFF101218),
                spinePrimaryColor = Color(0xFF12151E),
                spineAccentColor = Color(0xFF1E88E5),
                spineTextColor = Color(0xFFF2F6FA),
                headerBandColor = Color(0xFFD32F2F),
                frontAspectRatio = 0.72f,
                patternType = BoxPatternType.SEGA_GRID,
            )

        "sms" ->
            ConsoleBoxStyle(
                consoleTag = "MASTER SYSTEM",
                spineShortLabel = "SMS",
                boxBaseColor = Color(0xFF11141B),
                spinePrimaryColor = Color(0xFF161B26),
                spineAccentColor = Color(0xFF1976D2),
                spineTextColor = Color(0xFFF5F7FA),
                headerBandColor = Color(0xFFE53935),
                frontAspectRatio = 0.72f,
                patternType = BoxPatternType.SEGA_GRID,
            )

        "segacd", "gg" ->
            ConsoleBoxStyle(
                consoleTag = if (systemId.equals("gg", ignoreCase = true)) "GAME GEAR" else "SEGA CD",
                spineShortLabel = if (systemId.equals("gg", ignoreCase = true)) "GG" else "SCD",
                boxBaseColor = Color(0xFF10131C),
                spinePrimaryColor = Color(0xFF141824),
                spineAccentColor = Color(0xFF00ACC1),
                spineTextColor = Color(0xFFF2F6FA),
                headerBandColor = Color(0xFF1565C0),
                frontAspectRatio = 0.76f,
                patternType = BoxPatternType.SEGA_GRID,
            )

        "psx" ->
            ConsoleBoxStyle(
                consoleTag = "PlayStation",
                spineShortLabel = "PS1",
                boxBaseColor = Color(0xFF121316),
                spinePrimaryColor = Color(0xFF101114),
                spineAccentColor = Color(0xFFCFD8DC),
                spineTextColor = Color(0xFFF5F5F5),
                headerBandColor = Color(0xFF0B0C0E),
                frontAspectRatio = 1.0f,
                patternType = BoxPatternType.PSX_JEWEL,
            )

        "nes" ->
            ConsoleBoxStyle(
                consoleTag = "NINTENDO NES",
                spineShortLabel = "NES",
                boxBaseColor = Color(0xFF151618),
                spinePrimaryColor = Color(0xFF1B1D20),
                spineAccentColor = Color(0xFFBDBDBD),
                spineTextColor = Color(0xFFFAFAFA),
                headerBandColor = Color(0xFFE53935),
                frontAspectRatio = 0.72f,
                patternType = BoxPatternType.NES_SILVER_STRIPE,
            )

        "gba", "gb", "gbc" -> {
            val tag =
                when (systemId.trim().lowercase()) {
                    "gba" -> "GAME BOY ADVANCE"
                    "gbc" -> "GAME BOY COLOR"
                    else -> "GAME BOY"
                }
            val accent =
                when (systemId.trim().lowercase()) {
                    "gba" -> Color(0xFF5C6BC0)
                    "gbc" -> Color(0xFF26A69A)
                    else -> Color(0xFF8E99A2)
                }
            ConsoleBoxStyle(
                consoleTag = tag,
                spineShortLabel = systemId.trim().uppercase(),
                boxBaseColor = Color(0xFF1E2028),
                spinePrimaryColor = Color(0xFF252834),
                spineAccentColor = accent,
                spineTextColor = Color(0xFFF5F6FA),
                headerBandColor = accent,
                frontAspectRatio = 1.0f,
                patternType = BoxPatternType.HANDHELD_COMPACT,
            )
        }

        "nds", "3ds", "n3ds" ->
            ConsoleBoxStyle(
                consoleTag = if (systemId.contains("3ds", ignoreCase = true)) "NINTENDO 3DS" else "NINTENDO DS",
                spineShortLabel = if (systemId.contains("3ds", ignoreCase = true)) "3DS" else "NDS",
                boxBaseColor = Color(0xFF26282C),
                spinePrimaryColor = Color(0xFFF2F3F5),
                spineAccentColor = Color(0xFF29B6F6),
                spineTextColor = Color(0xFF1B1D21),
                headerBandColor = Color(0xFFECEFF1),
                frontAspectRatio = 1.06f,
                patternType = BoxPatternType.NDS_WHITE_SPINE,
            )

        "psp" ->
            ConsoleBoxStyle(
                consoleTag = "PlayStation Portable",
                spineShortLabel = "PSP",
                boxBaseColor = Color(0xFF1E2228),
                spinePrimaryColor = Color(0xFF2B3038),
                spineAccentColor = Color(0xFFE0E5EC),
                spineTextColor = Color(0xFFF8FAFC),
                headerBandColor = Color(0xFFDCE2E8),
                frontAspectRatio = 0.62f,
                patternType = BoxPatternType.PSP_UMD,
            )

        "fbneo", "mame2003plus", "arcade" ->
            ConsoleBoxStyle(
                consoleTag = "ARCADE CLASSICS",
                spineShortLabel = "ARC",
                boxBaseColor = Color(0xFF171022),
                spinePrimaryColor = Color(0xFF1F1430),
                spineAccentColor = Color(0xFFFF9100),
                spineTextColor = Color(0xFFFFF3E0),
                headerBandColor = Color(0xFFFF1744),
                frontAspectRatio = 0.78f,
                patternType = BoxPatternType.ARCADE_NEON,
            )

        else ->
            ConsoleBoxStyle(
                consoleTag = systemId.uppercase().ifBlank { "RETRÔ" },
                spineShortLabel = systemId.uppercase().take(4).ifBlank { "BOX" },
                boxBaseColor = Color(0xFF1E1713),
                spinePrimaryColor = Color(0xFF2D211B),
                spineAccentColor = ShelfGold,
                spineTextColor = ShelfCream,
                headerBandColor = ShelfGoldDark,
                frontAspectRatio = 0.80f,
                patternType = BoxPatternType.NINTENDO_RED_GOLD,
            )
    }
}

@Composable
fun LocadoraShelfLayout(
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
    val selectedEntry = games.getOrNull(safeSelectedIndex)
    val activeSystemId = selectedEntry?.shelfGame?.systemId ?: systemKey
    val activeBoxStyle = remember(activeSystemId) { resolveBoxStyle(activeSystemId) }

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(ShelfWoodDark, Color(0xFF1B0F0A), ShelfWoodDark),
                    ),
                )
                .themeArtwork(FrontendTheme.LOCADORA)
                .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Left 50%: Screenshot slideshow showcase + full game description card
        LocadoraLeftDetailsStage(
            entry = selectedEntry,
            selectedIndex = safeSelectedIndex,
            totalGames = games.size,
            systemTitle = systemTitle,
            boxStyle = activeBoxStyle,
            screenshots = screenshots,
            screenshotIndex = screenshotIndex,
            description = description,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )

        // Right 50%: 3D Wooden Bookshelf + sideways game boxes + 3D pull-out & turn showcase
        LocadoraWoodenBookshelfStage(
            games = games,
            selectedIndex = safeSelectedIndex,
            systemTitle = systemTitle,
            fallbackSystemKey = systemKey,
            onSelectIndex = onSelectIndex,
            onConfirmGame = onConfirmGame,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
    }
}

@Composable
private fun LocadoraLeftDetailsStage(
    entry: GroupedGameEntry?,
    selectedIndex: Int,
    totalGames: Int,
    systemTitle: String,
    boxStyle: ConsoleBoxStyle,
    screenshots: List<String>,
    screenshotIndex: Int,
    description: String?,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val context = LocalContext.current
    val game = entry?.shelfGame

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Rounded Screenshot Slideshow Card
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.56f),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0E0806),
            border =
                BorderStroke(
                    width = 1.6.dp,
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    ShelfGold.copy(alpha = 0.78f),
                                    ShelfGoldDark.copy(alpha = 0.55f),
                                ),
                        ),
                ),
            shadowElevation = 10.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val activeScreenshotUrl =
                    if (screenshots.isNotEmpty()) {
                        screenshots[screenshotIndex.coerceIn(0, screenshots.lastIndex)]
                    } else {
                        null
                    }

                var mediaFailed by remember(activeScreenshotUrl) { mutableStateOf(false) }
                Crossfade(
                    targetState = activeScreenshotUrl to game?.id,
                    animationSpec = tween(durationMillis = motion.mediaMillis),
                    label = "locadoraScreenshotCrossfade",
                    modifier = Modifier.fillMaxSize(),
                ) { (url, _) ->
                    when {
                        !url.isNullOrBlank() && !mediaFailed -> {
                            AsyncImage(
                                model =
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .crossfade(false)
                                        .build(),
                                onError = { mediaFailed = true },
                                contentDescription = entry?.shelfTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        game != null -> {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        boxStyle.spineAccentColor.copy(alpha = 0.22f),
                                                        Color(0xFF0D0806),
                                                    ),
                                            ),
                                        )
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                LemuroidGameImage(
                                    game = game,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "Nenhum jogo nesta prateleira",
                                    color = ShelfMuted,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }

                // Bottom gradient + slideshow dots
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xBB0D0705)),
                                ),
                            ),
                )

                if (screenshots.size > 1) {
                    val safeDotIndex = screenshotIndex.coerceIn(0, screenshots.lastIndex)
                    Row(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        screenshots.indices.forEach { dotIdx ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(if (dotIdx == safeDotIndex) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (dotIdx == safeDotIndex) {
                                                ShelfGold
                                            } else {
                                                ShelfCream.copy(alpha = 0.42f)
                                            },
                                        ),
                            )
                        }
                    }
                }
            }
        }

        // Dark Velvet Metadata & Description Card
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(0.44f),
            shape = RoundedCornerShape(18.dp),
            color = ShelfVelvetCard,
            border =
                BorderStroke(
                    width = 1.2.dp,
                    color = ShelfGoldDark.copy(alpha = 0.60f),
                ),
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry?.shelfTitle ?: systemTitle,
                    color = ShelfGoldSoft,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 25.sp,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Console badge
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = boxStyle.spineAccentColor.copy(alpha = 0.24f),
                        border = BorderStroke(1.dp, boxStyle.spineAccentColor.copy(alpha = 0.72f)),
                    ) {
                        Text(
                            text = systemTitle.uppercase(),
                            color = ShelfCream,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.7.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                            maxLines = 1,
                        )
                    }

                    // Multi-version / multi-disc badge
                    val versions = entry?.shelfVersionCount ?: 0
                    if (versions > 1) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ShelfGold.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, ShelfGold.copy(alpha = 0.75f)),
                        ) {
                            Text(
                                text = "$versions versões",
                                color = ShelfGoldSoft,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                maxLines = 1,
                            )
                        }
                    }

                    // Favorite star
                    if (game?.isFavorite == true) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Favorito",
                            tint = ShelfGold,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (totalGames > 0) {
                        Text(
                            text = "${selectedIndex + 1} / $totalGames",
                            color = ShelfMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Full Game Description (up to 5 lines)
                val resolvedDescription =
                    description?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "Clássico da prateleira $systemTitle pronto para jogar. Pressione A para iniciar."

                Text(
                    text = resolvedDescription,
                    color = ShelfCream.copy(alpha = 0.90f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LocadoraWoodenBookshelfStage(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    fallbackSystemKey: String,
    onSelectIndex: (Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val shelfRows =
        remember(games) {
            games.chunked(SHELF_COLUMNS)
        }
    val listState = rememberLazyListState()
    val selectedRowIndex =
        if (shelfRows.isEmpty()) 0 else (selectedIndex / SHELF_COLUMNS).coerceIn(0, shelfRows.lastIndex)
    val selectedEntry = games.getOrNull(selectedIndex)

    LaunchedEffect(selectedRowIndex, shelfRows.size) {
        if (shelfRows.isNotEmpty()) {
            val targetScrollRow = (selectedRowIndex - 1).coerceAtLeast(0)
            if (motion.animatedScroll) listState.animateScrollToItem(index = targetScrollRow) else listState.scrollToItem(index = targetScrollRow)
        }
    }

    // Outer 3D walnut wooden cabinet frame
    Box(
        modifier =
            modifier
                .shadow(16.dp, RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(ShelfWoodBack)
                .border(
                    width = 2.dp,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(ShelfWoodHighlight, ShelfWoodMid, Color(0xFF28160C)),
                        ),
                    shape = RoundedCornerShape(18.dp),
                ),
    ) {
        // Warm wood grain backboard
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF2A170D), ShelfWoodBack, Color(0xFF190D07)),
                    ),
            )
            // Subtle vertical wood grain slats
            val plankCount = 9
            val plankWidth = w / plankCount
            for (i in 1 until plankCount) {
                val x = i * plankWidth
                drawLine(
                    color = Color(0x1C000000),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 2f,
                )
                drawLine(
                    color = Color(0x10D9A066),
                    start = Offset(x + 2f, 0f),
                    end = Offset(x + 2f, h),
                    strokeWidth = 1f,
                )
            }
            // Warm top shelf spotlight glow
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(ShelfGold.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(w * 0.5f, h * 0.08f),
                        radius = w * 0.72f,
                    ),
            )
        }

        // Left & Right 3D vertical wooden side planks
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(14.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(ShelfWoodMid, ShelfWoodLight, Color(0xFF2A160B)),
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(14.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF2A160B), ShelfWoodLight, ShelfWoodMid),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
        ) {
            // Top wooden crown molding with brass aisle nameplate
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ShelfWoodLight, ShelfWoodMid, Color(0xFF2B170C)),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF26170D),
                    border = BorderStroke(1.dp, ShelfGold.copy(alpha = 0.8f)),
                ) {
                    Text(
                        text = "PRATELEIRA • ${systemTitle.uppercase()}",
                        color = ShelfGoldSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp),
                    )
                }
            }

            // Shelves LazyColumn + Elevated 3D Pull-Out Box Stage
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                if (shelfRows.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Prateleira vazia",
                            color = ShelfMuted,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp),
                    ) {
                        itemsIndexed(
                            items = shelfRows,
                            key = { rowIdx, rowItems ->
                                "shelf_${rowIdx}_${rowItems.firstOrNull()?.shelfGame?.id ?: 0}"
                            },
                        ) { rowIndex, rowGames ->
                            val baseIndex = rowIndex * SHELF_COLUMNS
                            WoodenShelfRow(
                                rowGames = rowGames,
                                baseIndex = baseIndex,
                                selectedIndex = selectedIndex,
                                fallbackSystemKey = fallbackSystemKey,
                                onSelectIndex = onSelectIndex,
                            )
                        }
                    }

                    // Elevated 3D Pull-Out & Turn Showcase Stage (never clipped by neighboring spines!)
                    if (selectedEntry != null) {
                        val entrySystemId = selectedEntry.shelfGame.systemId.ifBlank { fallbackSystemKey }
                        val entryBoxStyle = remember(entrySystemId) { resolveBoxStyle(entrySystemId) }
                        SelectedPullOut3DBoxOverlay(
                            entry = selectedEntry,
                            boxStyle = entryBoxStyle,
                            selectedIndex = selectedIndex,
                            onConfirm = { onConfirmGame(selectedEntry) },
                            modifier =
                                Modifier
                                    .align(Alignment.Center)
                                    .zIndex(20f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WoodenShelfRow(
    rowGames: List<GroupedGameEntry>,
    baseIndex: Int,
    selectedIndex: Int,
    fallbackSystemKey: String,
    onSelectIndex: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Recessed wooden cubby holding upright sideways game boxes
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(144.dp)
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color(0x66000000),
                                    Color(0x18000000),
                                    Color(0x40000000),
                                ),
                        ),
                    )
                    .padding(horizontal = 12.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                for (slot in 0 until SHELF_COLUMNS) {
                    val item = rowGames.getOrNull(slot)
                    if (item != null) {
                        val globalIndex = baseIndex + slot
                        val isSelected = globalIndex == selectedIndex
                        val style =
                            remember(item.shelfGame.systemId, fallbackSystemKey) {
                                resolveBoxStyle(item.shelfGame.systemId.ifBlank { fallbackSystemKey })
                            }
                        GameBoxSpineSlot(
                            entry = item,
                            boxStyle = style,
                            isSelected = isSelected,
                            onClick = { onSelectIndex(globalIndex) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // 3D Wooden Shelf Plank with perspective top bevel, thick front face, and cast drop shadow
        WoodenShelfPlank3D(
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun WoodenShelfPlank3D(
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.height(26.dp),
    ) {
        val w = size.width
        val topBevelH = 7.dp.toPx()
        val frontFaceH = 11.dp.toPx()
        val shadowH = size.height - topBevelH - frontFaceH

        // Top receding horizontal shelf surface (lighter warm walnut)
        val topPath =
            Path().apply {
                moveTo(4f, 0f)
                lineTo(w - 4f, 0f)
                lineTo(w, topBevelH)
                lineTo(0f, topBevelH)
                close()
            }
        drawPath(
            path = topPath,
            brush =
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF3E2313), ShelfWoodLight, ShelfWoodHighlight),
                    startY = 0f,
                    endY = topBevelH,
                ),
        )

        // Front thick plank face
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(ShelfWoodLight, ShelfWoodMid, Color(0xFF28150B)),
                    startY = topBevelH,
                    endY = topBevelH + frontFaceH,
                ),
            topLeft = Offset(0f, topBevelH),
            size = Size(w, frontFaceH),
        )

        // Specular wood edge highlight along the front lip
        drawLine(
            color = ShelfGoldSoft.copy(alpha = 0.25f),
            start = Offset(0f, topBevelH + 0.8f),
            end = Offset(w, topBevelH + 0.8f),
            strokeWidth = 1.4f,
        )

        // Cast drop shadow onto the shelf cubby underneath
        drawRect(
            brush =
                Brush.verticalGradient(
                    colors = listOf(Color(0x99000000), Color.Transparent),
                    startY = topBevelH + frontFaceH,
                    endY = topBevelH + frontFaceH + shadowH,
                ),
            topLeft = Offset(0f, topBevelH + frontFaceH),
            size = Size(w, shadowH),
        )
    }
}

@Composable
private fun GameBoxSpineSlot(
    entry: GroupedGameEntry,
    boxStyle: ConsoleBoxStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val slotPullAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.26f else 1f,
        animationSpec = tween(durationMillis = 220),
        label = "spinePullAlpha",
    )
    val spineHeight =
        when (boxStyle.patternType) {
            BoxPatternType.HANDHELD_COMPACT, BoxPatternType.NDS_WHITE_SPINE -> 114.dp
            BoxPatternType.PSX_JEWEL -> 118.dp
            BoxPatternType.PSP_UMD -> 132.dp
            else -> 128.dp
        }

    Box(
        modifier =
            modifier
                .height(spineHeight)
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (isSelected) {
            // Warm golden recessed glow showing where the box was pulled from
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        ShelfGold.copy(alpha = 0.22f),
                                        ShelfGold.copy(alpha = 0.08f),
                                    ),
                            ),
                        )
                        .border(
                            width = 1.4.dp,
                            color = ShelfGold.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        ),
            )
        }

        // Authentic sideways 3D game box spine
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = slotPullAlpha }
                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    .background(boxStyle.spinePrimaryColor)
                    .drawBehind {
                        drawSpinePattern(boxStyle)
                        // Realistic 3D cylindrical spine bevel shading (left highlight, right shadow)
                        drawRect(
                            brush =
                                Brush.horizontalGradient(
                                    colors =
                                        listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.Transparent,
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.42f),
                                        ),
                                ),
                        )
                        // Subtle top thickness edge
                        drawRect(
                            color = Color.White.copy(alpha = 0.16f),
                            topLeft = Offset.Zero,
                            size = Size(size.width, 2.5f),
                        )
                    },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Top console brand cap on spine
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(boxStyle.headerBandColor.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = boxStyle.spineShortLabel,
                        color =
                            if (boxStyle.patternType == BoxPatternType.NDS_WHITE_SPINE ||
                                boxStyle.patternType == BoxPatternType.PSP_UMD
                            ) {
                                Color(0xFF1A1D22)
                            } else {
                                Color.White
                            },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                    )
                }

                // Vertical rotated game title along the spine
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = entry.shelfTitle,
                        color = boxStyle.spineTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.verticalSpineText(),
                    )
                }

                // Bottom console accent footer band
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(boxStyle.spineAccentColor),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpinePattern(
    boxStyle: ConsoleBoxStyle,
) {
    when (boxStyle.patternType) {
        BoxPatternType.SEGA_GRID -> {
            // Iconic Sega blue/red grid pattern on spine
            val gridColor = boxStyle.spineAccentColor.copy(alpha = 0.28f)
            val step = 7.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
        }

        BoxPatternType.NES_SILVER_STRIPE -> {
            // Classic NES vertical silver stripe
            drawRect(
                color = Color(0xFFCFD8DC).copy(alpha = 0.35f),
                topLeft = Offset(size.width * 0.14f, 0f),
                size = Size(3.dp.toPx(), size.height),
            )
        }

        BoxPatternType.NINTENDO_RED_GOLD -> {
            // Gold Nintendo seal dot near bottom
            drawCircle(
                color = ShelfGold.copy(alpha = 0.75f),
                radius = 3.5.dp.toPx(),
                center = Offset(size.width * 0.5f, size.height - 14.dp.toPx()),
            )
        }

        BoxPatternType.PSX_JEWEL -> {
            // Translucent acrylic jewel edge highlights
            drawLine(
                color = Color.White.copy(alpha = 0.38f),
                start = Offset(1.5f, 0f),
                end = Offset(1.5f, size.height),
                strokeWidth = 2f,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.22f),
                start = Offset(size.width - 1.5f, 0f),
                end = Offset(size.width - 1.5f, size.height),
                strokeWidth = 1.5f,
            )
        }

        BoxPatternType.ARCADE_NEON -> {
            // Bright vertical neon collector stripe
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFF00E5FF)),
                    ),
                topLeft = Offset(size.width - 4.dp.toPx(), 0f),
                size = Size(3.dp.toPx(), size.height),
            )
        }

        else -> Unit
    }
}

/**
 * Rotates a single-line Text -90 degrees so it reads bottom-to-top along a vertical book spine,
 * swapping measured width and height so ellipsis clips cleanly to the spine length.
 */
private fun Modifier.verticalSpineText(): Modifier =
    this
        .layout { measurable, constraints ->
            val swappedConstraints =
                constraints.copy(
                    minWidth = constraints.minHeight,
                    maxWidth = constraints.maxHeight,
                    minHeight = constraints.minWidth,
                    maxHeight = constraints.maxWidth,
                )
            val placeable = measurable.measure(swappedConstraints)
            layout(placeable.height, placeable.width) {
                placeable.place(
                    x = -(placeable.width - placeable.height) / 2,
                    y = (placeable.width - placeable.height) / 2,
                )
            }
        }
        .graphicsLayer { rotationZ = -90f }

@Composable
private fun SelectedPullOut3DBoxOverlay(
    entry: GroupedGameEntry,
    boxStyle: ConsoleBoxStyle,
    selectedIndex: Int,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val density = LocalDensity.current
    val pullProgress = remember(selectedIndex) { Animatable(0f) }

    LaunchedEffect(selectedIndex, motion.animatedScroll) {
        pullProgress.snapTo(if (motion.animatedScroll) 0f else 1f)
        if (motion.animatedScroll) pullProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(motion.mediaMillis),
        )
    }

    val p = pullProgress.value
    val rotationYDeg = -80f * (1f - p) + (-10f) * p
    val scaleVal = 0.66f + 0.34f * p
    val slideOffsetPx = with(density) { (44.dp * (1f - p)).toPx() }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        val maxStageW = maxWidth * 0.78f
        val maxStageH = maxHeight * 0.72f
        val aspect = boxStyle.frontAspectRatio

        val boxWidth: Dp
        val boxHeight: Dp
        if (maxStageW / maxStageH > aspect) {
            boxHeight = maxStageH.coerceIn(180.dp, 280.dp)
            boxWidth = (boxHeight * aspect).coerceAtMost(maxStageW)
        } else {
            boxWidth = maxStageW.coerceIn(170.dp, 310.dp)
            boxHeight = (boxWidth / aspect).coerceAtMost(maxStageH)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 3D Turned Authentic Console Game Box
            Box(
                modifier =
                    Modifier
                        .graphicsLayer {
                            translationX = slideOffsetPx
                            scaleX = scaleVal
                            scaleY = scaleVal
                            rotationY = rotationYDeg
                            cameraDistance = 14f * density.density
                        }
                        .shadow(
                            elevation = (10f + 16f * p).dp,
                            shape = RoundedCornerShape(10.dp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        )
                        .focusProperties { canFocus = false }
                        .clickable(onClick = onConfirm),
            ) {
                AuthenticFrontGameBox3D(
                    entry = entry,
                    boxStyle = boxStyle,
                    boxWidth = boxWidth,
                    boxHeight = boxHeight,
                )
            }

            // Floating compact "A Jogar" brass badge beneath the pulled-out 3D box
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xDF1D120C),
                border = BorderStroke(1.3.dp, ShelfGold),
                shadowElevation = 8.dp,
                modifier =
                    Modifier
                        .graphicsLayer { alpha = p }
                        .focusProperties { canFocus = false }
                        .clickable(onClick = onConfirm),
            ) {
                Text(
                    text = "▶ A • JOGAR ESTE CARTUCHO",
                    color = ShelfGoldSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthenticFrontGameBox3D(
    entry: GroupedGameEntry,
    boxStyle: ConsoleBoxStyle,
    boxWidth: Dp,
    boxHeight: Dp,
) {
    val cornerRadius =
        when (boxStyle.patternType) {
            BoxPatternType.PSX_JEWEL -> 5.dp
            BoxPatternType.PSP_UMD -> 10.dp
            BoxPatternType.NINTENDO_RED_GOLD, BoxPatternType.NES_SILVER_STRIPE -> 4.dp
            else -> 7.dp
        }
    val shape = RoundedCornerShape(cornerRadius)
    val spineEdgeWidth = 14.dp

    Row(
        modifier =
            Modifier
                .size(width = boxWidth + spineEdgeWidth, height = boxHeight)
                .clip(shape)
                .background(boxStyle.boxBaseColor)
                .border(
                    width = if (boxStyle.patternType == BoxPatternType.PSX_JEWEL) 2.dp else 1.5.dp,
                    color =
                        if (boxStyle.patternType == BoxPatternType.PSX_JEWEL ||
                            boxStyle.patternType == BoxPatternType.PSP_UMD
                        ) {
                            Color.White.copy(alpha = 0.45f)
                        } else {
                            ShelfGold.copy(alpha = 0.70f)
                        },
                    shape = shape,
                ),
    ) {
        // Visible 3D left spine strip (gives authentic physical box depth!)
        Box(
            modifier =
                Modifier
                    .width(spineEdgeWidth)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colors =
                                listOf(
                                    boxStyle.spinePrimaryColor,
                                    boxStyle.spinePrimaryColor.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.55f),
                                ),
                        ),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    if (boxStyle.patternType == BoxPatternType.PSX_JEWEL) {
                        "PlayStation"
                    } else {
                        boxStyle.spineShortLabel
                    },
                color = boxStyle.spineTextColor.copy(alpha = 0.88f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.verticalSpineText(),
            )
        }

        // Authentic front box cover area
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Console-specific top header band
                AuthenticBoxHeaderBar(boxStyle = boxStyle)

                // Front cover artwork (ContentScale.Fit inside authentic frame)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(boxStyle.boxBaseColor)
                            .padding(
                                horizontal =
                                    if (boxStyle.patternType == BoxPatternType.SEGA_GRID) 8.dp else 6.dp,
                                vertical = 6.dp,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (boxStyle.patternType == BoxPatternType.SEGA_GRID) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val gridColor = boxStyle.spineAccentColor.copy(alpha = 0.22f)
                            val step = 12.dp.toPx()
                            var x = 0f
                            while (x < size.width) {
                                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                                x += step
                            }
                            var y = 0f
                            while (y < size.height) {
                                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                                y += step
                            }
                        }
                    }

                    LemuroidGameImage(
                        game = entry.shelfGame,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // Nintendo Gold Seal of Quality accent on SNES / N64 boxes
            if (boxStyle.patternType == BoxPatternType.NINTENDO_RED_GOLD) {
                Canvas(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(width = 24.dp, height = 16.dp),
                ) {
                    drawRoundRect(
                        color = ShelfGold,
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                    drawRoundRect(
                        color = Color(0xFF5D3A00),
                        topLeft = Offset(2f, 2f),
                        size = Size(size.width - 4f, size.height - 4f),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                    drawRoundRect(
                        color = ShelfGoldSoft,
                        topLeft = Offset(4f, 4f),
                        size = Size(size.width - 8f, size.height - 8f),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                    )
                }
            }

            // Realistic glossy plastic / cardboard diagonal light sheen
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    Color.White.copy(alpha = 0.16f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.18f),
                                ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                )
            }
        }
    }
}

@Composable
private fun AuthenticBoxHeaderBar(
    boxStyle: ConsoleBoxStyle,
) {
    val isLightHeader =
        boxStyle.patternType == BoxPatternType.NDS_WHITE_SPINE ||
            boxStyle.patternType == BoxPatternType.PSP_UMD

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(boxStyle.headerBandColor, boxStyle.boxBaseColor),
                    ),
                )
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = boxStyle.consoleTag,
            color = if (isLightHeader) Color(0xFF15181E) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier =
                Modifier
                    .size(width = 18.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(boxStyle.spineAccentColor),
        )
    }
}
