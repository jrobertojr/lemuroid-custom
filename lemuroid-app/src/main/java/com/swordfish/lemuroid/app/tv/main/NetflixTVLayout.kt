package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GameDescriptionText
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.lib.library.db.entity.Game
import java.util.Locale

private val NetflixBg = Color(0xFF0D131C)
private val NetflixRailBg = Color(0xFF090E15)
private val NetflixCardBg = Color(0xFF1B2532)
private val NetflixRed = Color(0xFFF04F5E)
private val NetflixMatchGreen = Color(0xFF46D369)
private val NetflixMutedText = Color(0xFFB3B3B3)

data class NetflixCategoryRow(
    val id: String,
    val title: String,
    val games: List<GroupedGameEntry>,
    val isTopTen: Boolean = false,
)

private val GroupedGameEntry.netflixGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

private val GroupedGameEntry.netflixVersionCount: Int
    get() =
        when (this) {
            is GroupedGameEntry.Single -> 1
            is GroupedGameEntry.MultiDisc -> discs.size
        }

private val ACTION_FIGHTING_KEYWORDS =
    listOf(
        "street fighter",
        "mortal kombat",
        "tekken",
        "king of fighters",
        "kof",
        "fatal fury",
        "samurai",
        "metal slug",
        "contra",
        "double dragon",
        "streets of rage",
        "final fight",
        "ninja",
        "shinobi",
        "batman",
        "spider",
        "x-men",
        "marvel",
        "capcom",
        "dragon ball",
        "dbz",
        "fight",
        "combat",
        "battle",
        "war",
        "gun",
        "shoot",
        "alien",
        "terminator",
        "doom",
        "castlevania",
        "resident evil",
        "dino",
        "devil",
        "god of",
        "syphon",
        "blade",
        "warrior",
        "striker",
        "commando",
        "bomberman",
        "megaman",
        "mega man",
        "rockman",
        "metroid",
        "tmnt",
        "turtle",
        "power rangers",
        "golden axe",
        "brawl",
        "smash",
        "wrestle",
        "wwf",
        "wcw",
        "boxing",
        "punch",
        "killer instinct",
        "soul calibur",
        "virtua fighter",
        "dead or alive",
        "guilty gear",
        "darkstalkers",
    )

private val ADVENTURE_RPG_KEYWORDS =
    listOf(
        "zelda",
        "final fantasy",
        "chrono",
        "dragon quest",
        "pokemon",
        "pokémon",
        "breath of fire",
        "mana",
        "suikoden",
        "tales of",
        "persona",
        "shin megami",
        "kingdom hearts",
        "vagrant",
        "xenogears",
        "parasite eve",
        "wild arms",
        "grandia",
        "lunar",
        "phantasy star",
        "shining",
        "fire emblem",
        "tactics",
        "harvest moon",
        "earthbound",
        "mother",
        "mario rpg",
        "paper mario",
        "golden sun",
        "diablo",
        "star ocean",
        "lufia",
        "illusion",
        "terranigma",
        "alundra",
        "tomb raider",
        "silent hill",
        "metal gear",
        "adventure",
        "quest",
        "legend",
        "saga",
        "rpg",
        "fantasy",
        "story",
        "chronicle",
        "odyssey",
        "mystery",
        "shadow",
        "soul",
        "legacy",
        "kain",
        "spyro",
        "ocarina",
        "majora",
    )

private val PLATFORM_CLASSICS_KEYWORDS =
    listOf(
        "mario",
        "sonic",
        "donkey kong",
        "kirby",
        "yoshi",
        "crash",
        "rayman",
        "banjo",
        "conker",
        "klonoa",
        "gex",
        "croc",
        "earthworm",
        "aladdin",
        "lion king",
        "disney",
        "mickey",
        "donald",
        "goofy",
        "ducktales",
        "tiny toon",
        "looney",
        "bugs bunny",
        "flintstones",
        "simpsons",
        "pac-man",
        "pacman",
        "tetris",
        "galaga",
        "dig dug",
        "bubble",
        "puzzle",
        "lemmings",
        "arkanoid",
        "breakout",
        "frogger",
        "centipede",
        "asteroids",
        "space invaders",
        "joust",
        "lode runner",
        "wonder boy",
        "alex kidd",
        "bonk",
        "kid icarus",
        "ice climber",
        "wario",
        "adventure island",
        "ghosts",
        "ghouls",
        "ristar",
        "sparkster",
        "rocket knight",
        "ape escape",
        "oddworld",
    )

private val RACING_SPORTS_KEYWORDS =
    listOf(
        "mario kart",
        "gran turismo",
        "need for speed",
        "nfs",
        "ridge racer",
        "wipeout",
        "f-zero",
        "top gear",
        "out run",
        "outrun",
        "daytona",
        "rally",
        "burnout",
        "cruis'n",
        "cruisn",
        "driver",
        "crazy taxi",
        "road rash",
        "excitebike",
        "micro machines",
        "f1",
        "formula",
        "nascar",
        "indy",
        "moto",
        "gp",
        "racing",
        "racer",
        "speed",
        "drift",
        "turbo",
        "kart",
        "fifa",
        "pes",
        "winning eleven",
        "iss",
        "soccer",
        "football",
        "nfl",
        "madden",
        "nba",
        "basketball",
        "mlb",
        "baseball",
        "nhl",
        "hockey",
        "tennis",
        "golf",
        "tony hawk",
        "skate",
        "snowboard",
        "ssx",
        "olympic",
        "sport",
        "cup",
        "league",
        "bowl",
        "slam",
        "jam",
    )

private fun GroupedGameEntry.matchesKeywords(keywords: List<String>): Boolean {
    val haystack = "${netflixGame.displayName()} ${netflixGame.title}".lowercase(Locale.ROOT)
    return keywords.any { keyword -> haystack.contains(keyword) }
}

fun buildNetflixCategoryRows(
    games: List<GroupedGameEntry>,
    recentGames: List<GroupedGameEntry>,
    favoriteGames: List<GroupedGameEntry>,
    systemTitle: String,
): List<NetflixCategoryRow> {
    if (games.isEmpty() && recentGames.isEmpty() && favoriteGames.isEmpty()) {
        return emptyList()
    }

    val rows = mutableListOf<NetflixCategoryRow>()
    val gameIds = games.map { it.netflixGame.id }.toHashSet()

    // 1. Continuar Jogando
    val matchingRecents =
        recentGames
            .filter { it.netflixGame.id in gameIds }
            .distinctBy { it.netflixGame.id }
    val continuePlaying =
        if (matchingRecents.isNotEmpty()) {
            matchingRecents.take(10)
        } else {
            recentGames.distinctBy { it.netflixGame.id }.take(10)
        }
    if (continuePlaying.isNotEmpty()) {
        rows +=
            NetflixCategoryRow(
                id = "continue_playing",
                title = "Continuar Jogando",
                games = continuePlaying,
            )
    }

    // 2. Minha Lista
    val matchingFavorites =
        (favoriteGames.filter { it.netflixGame.id in gameIds } + games.filter { it.netflixGame.isFavorite })
            .distinctBy { it.netflixGame.id }
    if (matchingFavorites.isNotEmpty()) {
        rows +=
            NetflixCategoryRow(
                id = "my_list",
                title = "Minha Lista",
                games = matchingFavorites,
            )
    }

    // 3. Top 10 em $systemTitle Hoje
    if (games.isNotEmpty()) {
        val recentIdSet = matchingRecents.map { it.netflixGame.id }.toHashSet()
        val topTenPool =
            (matchingFavorites + matchingRecents + games)
                .distinctBy { it.netflixGame.id }
                .sortedWith(
                    compareByDescending<GroupedGameEntry> { it.netflixGame.isFavorite }
                        .thenByDescending { it.netflixGame.id in recentIdSet },
                )
                .take(10)
        if (topTenPool.isNotEmpty()) {
            rows +=
                NetflixCategoryRow(
                    id = "top_10",
                    title = "Sua seleção • $systemTitle",
                    games = topTenPool,
                    isTopTen = true,
                )
        }
    }

    // 4. Categorized genre rows
    if (games.isNotEmpty()) {
        val actionFighting = games.filter { it.matchesKeywords(ACTION_FIGHTING_KEYWORDS) }
        if (actionFighting.isNotEmpty()) {
            rows +=
                NetflixCategoryRow(
                    id = "genre_action_fighting",
                    title = "Ação & Luta",
                    games = actionFighting,
                )
        }

        val adventureRpg = games.filter { it.matchesKeywords(ADVENTURE_RPG_KEYWORDS) }
        if (adventureRpg.isNotEmpty()) {
            rows +=
                NetflixCategoryRow(
                    id = "genre_adventure_rpg",
                    title = "Aventura & RPG",
                    games = adventureRpg,
                )
        }

        val platformClassics = games.filter { it.matchesKeywords(PLATFORM_CLASSICS_KEYWORDS) }
        if (platformClassics.isNotEmpty()) {
            rows +=
                NetflixCategoryRow(
                    id = "genre_platform_classics",
                    title = "Plataforma & Clássicos",
                    games = platformClassics,
                )
        }

        val racingSports = games.filter { it.matchesKeywords(RACING_SPORTS_KEYWORDS) }
        if (racingSports.isNotEmpty()) {
            rows +=
                NetflixCategoryRow(
                    id = "genre_racing_sports",
                    title = "Corrida & Esportes",
                    games = racingSports,
                )
        }

        // Full catalog row
        rows +=
            NetflixCategoryRow(
                id = "full_catalog",
                title = "Catálogo Completo — $systemTitle",
                games = games,
            )
    }

    return rows
}

@Composable
fun NetflixTVLayout(
    rows: List<NetflixCategoryRow>,
    selectedRowIndex: Int,
    selectedColIndex: Int,
    systemTitle: String,
    screenshots: List<String>,
    screenshotIndex: Int,
    description: String?,
    onSelectPosition: (row: Int, col: Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val safeRowIndex =
        if (rows.isEmpty()) 0 else selectedRowIndex.coerceIn(0, rows.lastIndex)
    val activeRow = rows.getOrNull(safeRowIndex)
    val safeColIndex =
        if (activeRow == null || activeRow.games.isEmpty()) {
            0
        } else {
            selectedColIndex.coerceIn(0, activeRow.games.lastIndex)
        }
    val selectedEntry =
        activeRow?.games?.getOrNull(safeColIndex)
            ?: rows.firstOrNull { it.games.isNotEmpty() }?.games?.firstOrNull()

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .themeArtwork(FrontendTheme.NETFLIX),
    ) {
        // Thin left visual rail (52.dp wide, non-focusable)
        NetflixLeftVisualRail(
            activeRowId = activeRow?.id,
            modifier =
                Modifier
                    .width(52.dp)
                    .fillMaxHeight(),
        )

        if (rows.isEmpty() || selectedEntry == null) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(NetflixBg.copy(alpha = 0.52f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nenhum título encontrado em $systemTitle",
                    color = NetflixMutedText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(NetflixBg.copy(alpha = 0.52f)),
            ) {
                // Hero reserves space for fixed actions and a scrollable synopsis.
                NetflixHeroBillboard(
                    entry = selectedEntry,
                    systemTitle = systemTitle,
                    screenshots = screenshots,
                    screenshotIndex = screenshotIndex,
                    description = description,
                    onConfirmGame = onConfirmGame,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(0.55f),
                )

                // Categorized rows retain their own independent scrolling.
                val verticalListState = rememberLazyListState()
                LaunchedEffect(safeRowIndex, rows.size) {
                    if (rows.isNotEmpty()) {
                        if (motion.animatedScroll) verticalListState.animateScrollToItem(safeRowIndex) else verticalListState.scrollToItem(safeRowIndex)
                    }
                }

                LazyColumn(
                    state = verticalListState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(0.45f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.id },
                    ) { rowIndex, row ->
                        val isRowActive = rowIndex == safeRowIndex
                        NetflixCategoryRowSection(
                            row = row,
                            rowIndex = rowIndex,
                            isRowActive = isRowActive,
                            selectedColIndex = if (isRowActive) safeColIndex else -1,
                            onSelectPosition = onSelectPosition,
                            onConfirmGame = onConfirmGame,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetflixLeftVisualRail(
    activeRowId: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(NetflixRailBg)
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.06f),
                )
                .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Original cinema/play emblem
        CineArcadeEmblem(
            modifier = Modifier.size(38.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        NetflixRailIcon(
            icon = Icons.Default.Search,
            isHighlighted = false,
        )
        Spacer(modifier = Modifier.height(22.dp))
        NetflixRailIcon(
            icon = Icons.Default.Home,
            isHighlighted = activeRowId != "my_list" && activeRowId != "top_10",
        )
        Spacer(modifier = Modifier.height(22.dp))
        NetflixRailIcon(
            icon = Icons.Default.Star,
            isHighlighted = activeRowId == "top_10",
        )
        Spacer(modifier = Modifier.height(22.dp))
        NetflixRailIcon(
            icon = Icons.Default.Favorite,
            isHighlighted = activeRowId == "my_list",
        )
        Spacer(modifier = Modifier.height(22.dp))
        NetflixRailIcon(
            icon = Icons.Default.List,
            isHighlighted = false,
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CineArcadeEmblem(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(NetflixRed.copy(alpha = 0.18f), radius = unit / 2f, center = center)
        drawArc(
            color = NetflixRed,
            startAngle = 48f,
            sweepAngle = 264f,
            useCenter = false,
            topLeft = Offset(center.x - unit * 0.43f, center.y - unit * 0.43f),
            size = androidx.compose.ui.geometry.Size(unit * 0.86f, unit * 0.86f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = unit * 0.12f),
        )
        val play = Path().apply {
            moveTo(center.x - unit * 0.08f, center.y - unit * 0.23f)
            lineTo(center.x + unit * 0.28f, center.y)
            lineTo(center.x - unit * 0.08f, center.y + unit * 0.23f)
            close()
        }
        drawPath(play, Color.White)
    }
}

@Composable
private fun NetflixRailIcon(
    icon: ImageVector,
    isHighlighted: Boolean,
) {
    val motion = LocalFrontendMotion.current
    val tint by animateColorAsState(
        targetValue = if (isHighlighted) Color.White else Color(0xFF666666),
        animationSpec = tween(motion.focusMillis),
        label = "netflix_rail_tint",
    )
    Box(
        modifier = Modifier.fillMaxWidth().height(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isHighlighted) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(3.dp)
                        .height(20.dp)
                        .background(NetflixRed, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NetflixHeroBillboard(
    entry: GroupedGameEntry,
    systemTitle: String,
    screenshots: List<String>,
    screenshotIndex: Int,
    description: String?,
    onConfirmGame: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalFrontendMotion.current
    val game = entry.netflixGame
    val activeScreenshotUrl =
        if (screenshots.isNotEmpty()) {
            screenshots[screenshotIndex.floorMod(screenshots.size)]
        } else {
            null
        }
    val heroMediaKey = game to activeScreenshotUrl
    val versionCount = entry.netflixVersionCount

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(NetflixBg),
    ) {
        // Full-bleed widescreen artwork on the right/full canvas with smooth Crossfade
        Crossfade(
            targetState = heroMediaKey,
            animationSpec = tween(durationMillis = motion.mediaMillis),
            modifier = Modifier.fillMaxSize(),
            label = "netflix_hero_crossfade",
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
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    LemuroidGameImage(
                        game = mediaGame,
                        contentScale = ContentScale.Fit,
                        preserveAspect = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Cinematic left-to-right scrim
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to NetflixBg,
                            0.36f to NetflixBg.copy(alpha = 0.94f),
                            0.58f to NetflixBg.copy(alpha = 0.62f),
                            0.85f to NetflixBg.copy(alpha = 0.18f),
                            1.0f to Color.Transparent,
                        ),
                    ),
        )

        // Cinematic bottom-to-top fade melting seamlessly into the rows
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to NetflixBg.copy(alpha = 0.25f),
                            0.55f to Color.Transparent,
                            0.86f to NetflixBg.copy(alpha = 0.85f),
                            1.0f to NetflixBg,
                        ),
                    ),
        )

        // Editorial metadata block on the left
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.58f)
                    .padding(start = 28.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // "N SÉRIE • $systemTitle"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CineArcadeEmblem(
                    modifier = Modifier.size(width = 11.dp, height = 18.dp),
                )
                Text(
                    text = "SÉRIE  •  ${systemTitle.uppercase(Locale.ROOT)}",
                    color = Color(0xFFD6D6D6),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Bold white Hero Title
            Text(
                text = game.displayName(),
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style =
                    TextStyle(
                        shadow =
                            Shadow(
                                color = Color.Black.copy(alpha = 0.85f),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f,
                            ),
                    ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Actual library status and console badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (game.isFavorite) "Na sua lista" else "Da sua biblioteca",
                    color = NetflixMatchGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )

                NetflixMetadataBadge(
                    text = game.systemId.uppercase(Locale.ROOT),
                )

                NetflixMetadataBadge(
                    text = "JOGAR",
                )

                if (versionCount > 1) {
                    NetflixMetadataBadge(
                        text = "$versionCount VERSÕES",
                    )
                }

                if (game.isFavorite) {
                    Text(
                        text = "★ Minha Lista",
                        color = Color(0xFFE5E5E5),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Only the synopsis scrolls; the action row remains outside its viewport.
            val resolvedDescription =
                if (!description.isNullOrBlank()) {
                    description
                } else {
                    "Clássico inesquecível de $systemTitle pronto para jogar em tela grande com salvamento instantâneo e suporte completo a controle."
                }
            GameDescriptionText(
                text = resolvedDescription,
                modifier = Modifier.weight(1f),
                color = Color(0xFFD8D8D8),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                visibleLines = 8,
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Visual [A] ▶ Jogar + [Y] + Minha Lista buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Primary white Play button
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .clickable { onConfirmGame(entry) }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadButtonIcon(
                        glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.A,
                        size = 18.dp,
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Jogar",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Secondary dark translucent My List button
                Row(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xA6333333))
                            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadButtonIcon(
                        glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.Y,
                        size = 18.dp,
                    )
                    Icon(
                        imageVector = if (game.isFavorite) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (game.isFavorite) "Na Minha Lista" else "Minha Lista",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetflixMetadataBadge(text: String) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(3.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFFE5E5E5),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun NetflixCategoryRowSection(
    row: NetflixCategoryRow,
    rowIndex: Int,
    isRowActive: Boolean,
    selectedColIndex: Int,
    onSelectPosition: (row: Int, col: Int) -> Unit,
    onConfirmGame: (GroupedGameEntry) -> Unit,
) {
    val motion = LocalFrontendMotion.current
    val horizontalListState = rememberLazyListState()
    LaunchedEffect(isRowActive, selectedColIndex, row.games.size) {
        if (isRowActive && selectedColIndex in row.games.indices) {
            if (motion.animatedScroll) horizontalListState.animateScrollToItem(selectedColIndex) else horizontalListState.scrollToItem(selectedColIndex)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (row.isTopTen) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(NetflixRed)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "SUA SELEÇÃO",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
            Text(
                text = row.title,
                color = if (isRowActive) Color.White else Color(0xFFD0D0D0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        LazyRow(
            state = horizontalListState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(if (row.isTopTen) 16.dp else 12.dp),
        ) {
            itemsIndexed(
                items = row.games,
                key = { colIndex, entry -> "${row.id}_${entry.netflixGame.id}_$colIndex" },
            ) { colIndex, entry ->
                val isSelected = isRowActive && colIndex == selectedColIndex
                if (row.isTopTen) {
                    NetflixTopTenPosterItem(
                        rank = colIndex + 1,
                        entry = entry,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onConfirmGame(entry)
                            } else {
                                onSelectPosition(rowIndex, colIndex)
                            }
                        },
                    )
                } else {
                    NetflixPosterCard(
                        entry = entry,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onConfirmGame(entry)
                            } else {
                                onSelectPosition(rowIndex, colIndex)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NetflixTopTenPosterItem(
    rank: Int,
    entry: GroupedGameEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val motion = LocalFrontendMotion.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1.0f,
        animationSpec = tween(motion.focusMillis),
        label = "netflix_top10_scale",
    )

    Row(
        modifier =
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Giant stylized outlined Netflix rank numeral (1..10)
        Box(
            modifier =
                Modifier
                    .width(if (rank >= 10) 76.dp else 52.dp)
                    .height(148.dp)
                    .offset(x = 8.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            val rankText = rank.toString()
            val fontSize = if (rank >= 10) 74.sp else 92.sp
            // Outlined stroke layer
            Text(
                text = rankText,
                color = if (isSelected) Color.White else Color(0xFF7A7A7A),
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = if (rank >= 10) (-6).sp else 0.sp,
                style =
                    TextStyle(
                        drawStyle =
                            Stroke(
                                width = 6f,
                                join = StrokeJoin.Round,
                            ),
                    ),
                maxLines = 1,
            )
            // Hollow dark fill layer
            Text(
                text = rankText,
                color = NetflixBg,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                letterSpacing = if (rank >= 10) (-6).sp else 0.sp,
                maxLines = 1,
            )
        }

        NetflixPosterCard(
            entry = entry,
            isSelected = isSelected,
            onClick = onClick,
            applyScale = false,
        )
    }
}

@Composable
private fun NetflixPosterCard(
    entry: GroupedGameEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    applyScale: Boolean = true,
) {
    val motion = LocalFrontendMotion.current
    val game = entry.netflixGame
    val scale by animateFloatAsState(
        targetValue = if (applyScale && isSelected) 1.07f else 1.0f,
        animationSpec = tween(motion.focusMillis),
        label = "netflix_card_scale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color(0xFF2B2B2B),
        animationSpec = tween(motion.focusMillis),
        label = "netflix_card_border",
    )
    val shape = RoundedCornerShape(6.dp)

    Box(
        modifier =
            Modifier
                .width(114.dp)
                .height(152.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (isSelected) {
                        Modifier.shadow(elevation = 14.dp, shape = shape, clip = false)
                    } else {
                        Modifier
                    },
                )
                .clip(shape)
                .background(NetflixCardBg)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = borderColor,
                    shape = shape,
                )
                .clickable(onClick = onClick),
    ) {
        LemuroidGameImage(
            game = game,
            contentScale = ContentScale.Fit,
            preserveAspect = true,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(6.dp),
        )

        // Subtle top-left Netflix "N" tag
        CineArcadeEmblem(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(width = 8.dp, height = 13.dp),
        )

        // Favorite indicator dot if favorited
        if (game.isFavorite) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(13.dp),
            )
        }

        // Bottom title gradient strip
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors =
                                listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.88f),
                                ),
                        ),
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Text(
                text = game.displayName(),
                color = if (isSelected) Color.White else Color(0xFFE0E0E0),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun Int.floorMod(modulus: Int): Int {
    if (modulus <= 0) return 0
    val rem = this % modulus
    return if (rem < 0) rem + modulus else rem
}
