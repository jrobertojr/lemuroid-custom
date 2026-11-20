package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ConsoleRoom3DView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.Game3DObject
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocadoraRing3DView
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.app.shared.games.MultiDiscGrouper
import com.swordfish.lemuroid.app.shared.ui.FrontendSoundEngine
import com.swordfish.lemuroid.app.tv.main.NetflixCategoryRow
import com.swordfish.lemuroid.app.tv.main.TVThemedFrontend
import com.swordfish.lemuroid.app.tv.main.buildNetflixCategoryRows
import com.swordfish.lemuroid.app.tv.shared.TVGameMetadataHelper
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

private const val MOBILE_ARCADE_KEY = "arcade"
private val MOBILE_ARCADE_IDS = setOf(SystemID.FBNEO.dbname, SystemID.MAME2003PLUS.dbname)

private data class MobileSystemTab(
    val key: String,
    val label: String,
    val count: Int,
)

private val GroupedGameEntry.mobileGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

/**
 * Full-featured Mobile adaptation of all custom themes:
 * - In LANDSCAPE: mounts the complete 16:9 console/TV experience ([TVThemedFrontend]) with
 *   touch-accessible Settings/Search/Rescan controls (handheld console mode!).
 * - In PORTRAIT: renders touch-native vertical layouts adapted for every single custom theme
 *   (Locadora 3D Ring + Wooden Shelf, Console 3D Bedroom + Brand Dashboards, Netflix Mobile,
 *   Retro CRT Tube TV, Synthwave 80/90, GTA Rockstar, Thrash Metal Spinning Vinyl, and 3D Pedestal).
 */
@Composable
fun MobileThemedFrontend(
    retrogradeDb: RetrogradeDatabase,
    gameInteractor: GameInteractor,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onRescan: () -> Unit,
) {
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Box(modifier = Modifier.fillMaxSize()) {
            TVThemedFrontend(
                retrogradeDb = retrogradeDb,
                gameInteractor = gameInteractor,
                onOpenSettings = onOpenSettings,
                onRescan = onRescan,
                onRequestExit = { backDispatcher?.onBackPressed() },
            )
            // Compact touch quick-actions in top-right for touch-only landscape users
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 6.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MobileFloatingIconButton(Icons.Filled.Search, onClick = onOpenSearch)
                MobileFloatingIconButton(Icons.Filled.Refresh, onClick = onRescan)
                MobileFloatingIconButton(Icons.Filled.Settings, onClick = onOpenSettings)
            }
        }
        return
    }

    MobilePortraitThemedFrontend(
        retrogradeDb = retrogradeDb,
        gameInteractor = gameInteractor,
        onOpenSettings = onOpenSettings,
        onOpenSearch = onOpenSearch,
        onRescan = onRescan,
    )
}

@Composable
private fun MobileFloatingIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        modifier = Modifier.size(34.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun MobilePortraitThemedFrontend(
    retrogradeDb: RetrogradeDatabase,
    gameInteractor: GameInteractor,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onRescan: () -> Unit,
) {
    val theme = LocalFrontendTheme.current
    val context = LocalContext.current
    val gameDao = remember(retrogradeDb) { retrogradeDb.gameDao() }

    val systemCounts by gameDao.selectSystemsWithCount().collectAsState(initial = emptyList())
    val systemIds = remember(systemCounts) { systemCounts.map { it.systemId } }
    val allGames by remember(systemIds) {
        if (systemIds.isEmpty()) flowOf(emptyList()) else gameDao.selectBySystemsList(systemIds)
    }.collectAsState(initial = emptyList())
    val recentGames by gameDao.selectFirstUnfavoriteRecents(12).collectAsState(initial = emptyList())
    val favoriteGames by gameDao.selectFirstFavorites(20).collectAsState(initial = emptyList())

    val groupedAll = remember(allGames) { MultiDiscGrouper.group(allGames) }
    val groupedRecents = remember(recentGames) { MultiDiscGrouper.group(recentGames) }
    val groupedFavorites = remember(favoriteGames) { MultiDiscGrouper.group(favoriteGames) }

    val consoleTabs =
        remember(systemCounts) {
            val arcadeCount = systemCounts.filter { it.systemId in MOBILE_ARCADE_IDS }.sumOf { it.count }
            var arcadeAdded = false
            buildList {
                systemCounts.forEach { sc ->
                    if (sc.systemId in MOBILE_ARCADE_IDS) {
                        if (!arcadeAdded && arcadeCount > 0) {
                            add(MobileSystemTab(MOBILE_ARCADE_KEY, "ARCADE", arcadeCount))
                            arcadeAdded = true
                        }
                    } else {
                        val title =
                            runCatching { GameSystem.findById(sc.systemId).libretroFullName }
                                .getOrElse { sc.systemId.uppercase() }
                        add(MobileSystemTab(sc.systemId, title, sc.count))
                    }
                }
            }
        }

    val allTabs =
        remember(consoleTabs, groupedAll.size, groupedRecents.size, groupedFavorites.size) {
            buildList {
                add(MobileSystemTab("all", "Todos", groupedAll.size))
                if (groupedRecents.isNotEmpty()) add(MobileSystemTab("recents", "Recentes", groupedRecents.size))
                if (groupedFavorites.isNotEmpty()) add(MobileSystemTab("favorites", "Favoritos", groupedFavorites.size))
                addAll(consoleTabs)
            }
        }

    var selectedTabKey by rememberSaveable { mutableStateOf("all") }
    var selectedGameIndex by rememberSaveable { mutableIntStateOf(0) }
    var locadoraConsoleEntered by rememberSaveable { mutableStateOf(false) }
    var locadoraRingIndex by rememberSaveable { mutableIntStateOf(0) }
    var consoleRoomEntered by rememberSaveable { mutableStateOf(false) }
    var consoleRoomIndex by rememberSaveable { mutableIntStateOf(0) }
    var multiDiscDialog by remember { mutableStateOf<GroupedGameEntry.MultiDisc?>(null) }
    var returnToGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var previousTheme by remember { mutableStateOf(theme) }

    LaunchedEffect(theme) {
        if (previousTheme != theme) {
            locadoraConsoleEntered = false
            consoleRoomEntered = false
            selectedGameIndex = 0
            returnToGameId = null
            previousTheme = theme
        }
    }

    // SCREEN 1: 3D Console Selector for LOCADORA (Ring) & CONSOLE (80s/90s Bedroom)
    if (theme == FrontendTheme.LOCADORA && !locadoraConsoleEntered && consoleTabs.isNotEmpty()) {
        val safeIdx = locadoraRingIndex.coerceIn(0, consoleTabs.lastIndex)
        val currentConsole = consoleTabs[safeIdx]
        MobilePortrait3DConsolePicker(
            title = "LOCADORA RETRÔ",
            subtitle = "ESCOLHA O CONSOLE EM 3D",
            accentColor = Color(0xFFFFC107),
            buttonLabel = "ENTRAR NA PRATELEIRA",
            consoleName = currentConsole.label,
            gameCount = currentConsole.count,
            onPrev = {
                locadoraRingIndex = (safeIdx - 1 + consoleTabs.size) % consoleTabs.size
                FrontendSoundEngine.playNavigate(context, theme)
            },
            onNext = {
                locadoraRingIndex = (safeIdx + 1) % consoleTabs.size
                FrontendSoundEngine.playNavigate(context, theme)
            },
            onEnter = {
                FrontendSoundEngine.playConfirm(context, theme)
                selectedTabKey = currentConsole.key
                selectedGameIndex = 0
                locadoraConsoleEntered = true
            },
            onOpenSettings = onOpenSettings,
            onOpenSearch = onOpenSearch,
            onRescan = onRescan,
            scene3D = {
                LocadoraRing3DView(
                    consoleKeys = consoleTabs.map { it.key },
                    selectedIndex = safeIdx,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
        return
    }

    if (theme == FrontendTheme.CONSOLE && !consoleRoomEntered && consoleTabs.isNotEmpty()) {
        val safeIdx = consoleRoomIndex.coerceIn(0, consoleTabs.lastIndex)
        val currentConsole = consoleTabs[safeIdx]
        MobilePortrait3DConsolePicker(
            title = "QUARTO GAMER 80/90",
            subtitle = "SELECIONE SEU VIDEOGAME",
            accentColor = Color(0xFF00C2FF),
            buttonLabel = "LIGAR CONSOLE",
            consoleName = currentConsole.label,
            gameCount = currentConsole.count,
            onPrev = {
                consoleRoomIndex = (safeIdx - 1 + consoleTabs.size) % consoleTabs.size
                FrontendSoundEngine.playNavigate(context, theme)
            },
            onNext = {
                consoleRoomIndex = (safeIdx + 1) % consoleTabs.size
                FrontendSoundEngine.playNavigate(context, theme)
            },
            onEnter = {
                FrontendSoundEngine.playConfirm(context, theme)
                selectedTabKey = currentConsole.key
                selectedGameIndex = 0
                consoleRoomEntered = true
            },
            onOpenSettings = onOpenSettings,
            onOpenSearch = onOpenSearch,
            onRescan = onRescan,
            scene3D = {
                ConsoleRoom3DView(
                    consoleKeys = consoleTabs.map { it.key },
                    selectedIndex = safeIdx,
                    modifier = Modifier.fillMaxSize(),
                )
            },
        )
        return
    }

    val filteredGames =
        remember(selectedTabKey, groupedAll, groupedRecents, groupedFavorites) {
            when (selectedTabKey) {
                "all" -> groupedAll
                "recents" -> groupedRecents
                "favorites" -> groupedFavorites
                MOBILE_ARCADE_KEY -> groupedAll.filter { it.mobileGame.systemId in MOBILE_ARCADE_IDS }
                else -> groupedAll.filter { it.mobileGame.systemId == selectedTabKey }
            }
        }

    LaunchedEffect(filteredGames, returnToGameId) {
        val id = returnToGameId ?: return@LaunchedEffect
        val index = filteredGames.indexOfFirst { it.mobileGame.id == id }
        if (index >= 0) selectedGameIndex = index
    }
    var previousTab by remember { mutableStateOf(selectedTabKey) }
    LaunchedEffect(selectedTabKey) {
        if (previousTab != selectedTabKey) {
            returnToGameId = null
            previousTab = selectedTabKey
        }
    }
    val safeGameIdx = if (filteredGames.isEmpty()) 0 else selectedGameIndex.coerceIn(0, filteredGames.lastIndex)
    val selectedEntry = filteredGames.getOrNull(safeGameIdx)
    val selectedGame = selectedEntry?.mobileGame
    val activeSystemTitle = allTabs.firstOrNull { it.key == selectedTabKey }?.label ?: "Jogos"

    val screenshots =
        remember(selectedGame?.id) {
            selectedGame?.let { TVGameMetadataHelper.screenshotGalleryUrlsFor(it) } ?: emptyList()
        }
    var screenshotIdx by remember(selectedGame?.id) { mutableIntStateOf(0) }
    LaunchedEffect(selectedGame?.id, screenshots.size) {
        screenshotIdx = 0
        if (screenshots.size > 1) {
            while (true) {
                delay(3500L)
                screenshotIdx = (screenshotIdx + 1) % screenshots.size
            }
        }
    }

    var description by remember(selectedGame?.id) { mutableStateOf("") }
    LaunchedEffect(selectedGame?.id) {
        val target = selectedGame ?: return@LaunchedEffect
        delay(120L)
        description = TVGameMetadataHelper.fetchGameDescription(context, target)
    }

    val onConfirmGame: (GroupedGameEntry) -> Unit = { entry ->
        FrontendSoundEngine.playConfirm(context, theme)
        when (entry) {
            is GroupedGameEntry.Single -> {
                returnToGameId = entry.mobileGame.id
                gameInteractor.onGamePlay(entry.game)
            }
            is GroupedGameEntry.MultiDisc -> multiDiscDialog = entry
        }
    }

    val onSelectOrPlay: (Int) -> Unit = { idx ->
        if (idx == safeGameIdx && idx in filteredGames.indices) {
            onConfirmGame(filteredGames[idx])
        } else {
            returnToGameId = null
            selectedGameIndex = idx
            FrontendSoundEngine.playNavigate(context, theme)
        }
    }

    val accentColor = mobileThemeAccent(theme)
    val canGoBackTo3D = (theme == FrontendTheme.LOCADORA && locadoraConsoleEntered) ||
        (theme == FrontendTheme.CONSOLE && consoleRoomEntered)

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0E))) {
        MobileThemeBackdrop(theme = theme)

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
        ) {
            // Top bar with optional Back to 3D, Theme Brand, Search, Rescan, Settings
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canGoBackTo3D) {
                    IconButton(
                        onClick = {
                            FrontendSoundEngine.playBack(context, theme)
                            if (theme == FrontendTheme.LOCADORA) locadoraConsoleEntered = false
                            if (theme == FrontendTheme.CONSOLE) consoleRoomEntered = false
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = accentColor)
                    }
                }
                Text(
                    text = mobileThemeHeader(theme, activeSystemTitle),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White)
                }
                IconButton(onClick = onRescan) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = null, tint = accentColor)
                }
            }

            // Horizontal Console / Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                itemsIndexed(allTabs) { _, tab ->
                    val active = tab.key == selectedTabKey
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (active) accentColor else Color.White.copy(alpha = 0.10f),
                        modifier =
                            Modifier.clickable {
                                selectedTabKey = tab.key
                                selectedGameIndex = 0
                                FrontendSoundEngine.playSwitchSystem(context, theme)
                            },
                    ) {
                        Text(
                            text = "${tab.label} (${tab.count})",
                            color = if (active) Color.Black else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Main Portrait Theme Content
            if (filteredGames.isEmpty() || selectedGame == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Nenhum jogo encontrado", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                when (theme) {
                    FrontendTheme.NETFLIX -> {
                        val rows =
                            remember(filteredGames, groupedRecents, groupedFavorites, activeSystemTitle) {
                                buildNetflixCategoryRows(
                                    games = filteredGames,
                                    recentGames = groupedRecents,
                                    favoriteGames = groupedFavorites,
                                    systemTitle = activeSystemTitle,
                                )
                            }
                        MobileNetflixPortrait(
                            heroEntry = selectedEntry,
                            rows = rows,
                            systemTitle = activeSystemTitle,
                            screenshots = screenshots,
                            screenshotIdx = screenshotIdx,
                            description = description,
                            onSelectGame = { entry ->
                                val idx = filteredGames.indexOfFirst { it.mobileGame.id == entry.mobileGame.id }
                                if (idx >= 0) onSelectOrPlay(idx) else onConfirmGame(entry)
                            },
                            onPlayHero = { onConfirmGame(selectedEntry) },
                            onToggleFavorite = {
                                gameInteractor.onFavoriteToggle(selectedGame, !selectedGame.isFavorite)
                                FrontendSoundEngine.playFavorite(context, theme)
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }

                    FrontendTheme.TV -> {
                        MobileRetroTubePortrait(
                            games = filteredGames,
                            selectedIndex = safeGameIdx,
                            screenshots = screenshots,
                            screenshotIdx = screenshotIdx,
                            description = description,
                            onSelectOrPlay = onSelectOrPlay,
                            onPlaySelected = { onConfirmGame(selectedEntry) },
                            onToggleFavorite = {
                                gameInteractor.onFavoriteToggle(selectedGame, !selectedGame.isFavorite)
                                FrontendSoundEngine.playFavorite(context, theme)
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }

                    FrontendTheme.LOCADORA -> {
                        MobileLocadoraShelfPortrait(
                            games = filteredGames,
                            selectedIndex = safeGameIdx,
                            screenshots = screenshots,
                            screenshotIdx = screenshotIdx,
                            description = description,
                            onSelectOrPlay = onSelectOrPlay,
                            onPlaySelected = { onConfirmGame(selectedEntry) },
                            onToggleFavorite = {
                                gameInteractor.onFavoriteToggle(selectedGame, !selectedGame.isFavorite)
                                FrontendSoundEngine.playFavorite(context, theme)
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }

                    else -> {
                        // Portrait showcase + 3-column multi-row grid tailored to CONSOLE,
                        // RETRO_80_90, ROCKSTAR, THRASH_METAL (spinning vinyl!), and THREE_D
                        MobileShowcaseAndGridPortrait(
                            theme = theme,
                            games = filteredGames,
                            selectedIndex = safeGameIdx,
                            systemTitle = activeSystemTitle,
                            screenshots = screenshots,
                            screenshotIdx = screenshotIdx,
                            description = description,
                            accentColor = accentColor,
                            onSelectOrPlay = onSelectOrPlay,
                            onPlaySelected = { onConfirmGame(selectedEntry) },
                            onToggleFavorite = {
                                gameInteractor.onFavoriteToggle(selectedGame, !selectedGame.isFavorite)
                                FrontendSoundEngine.playFavorite(context, theme)
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    multiDiscDialog?.let { group ->
        AlertDialog(
            onDismissRequest = { multiDiscDialog = null },
            title = { Text(group.representative.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.discs.forEach { disc ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        multiDiscDialog = null
                                        returnToGameId = selectedEntry?.mobileGame?.id ?: disc.id
                                        gameInteractor.onGamePlay(disc)
                                    },
                        ) {
                            Text(
                                text = disc.fileName,
                                modifier = Modifier.padding(12.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { multiDiscDialog = null }) { Text("Fechar") }
            },
        )
    }
}

@Composable
private fun MobilePortrait3DConsolePicker(
    title: String,
    subtitle: String,
    accentColor: Color,
    buttonLabel: String,
    consoleName: String,
    gameCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEnter: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onRescan: () -> Unit,
    scene3D: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        scene3D()

        // Top HUD
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = accentColor, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Text(subtitle, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            }
            MobileFloatingIconButton(Icons.Filled.Search, onClick = onOpenSearch)
            Spacer(Modifier.width(6.dp))
            MobileFloatingIconButton(Icons.Filled.Refresh, onClick = onRescan)
            Spacer(Modifier.width(6.dp))
            MobileFloatingIconButton(Icons.Filled.Settings, onClick = onOpenSettings)
        }

        // Bottom Console Selector Card with Touch Arrows + Enter Button
        Surface(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xE0101016),
            border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.85f)),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.22f),
                        modifier = Modifier.size(44.dp).clickable(onClick = onPrev),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = Color.White)
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = consoleName,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "$gameCount jogos disponíveis",
                            color = accentColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.22f),
                        modifier = Modifier.size(44.dp).clickable(onClick = onNext),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentColor,
                    modifier = Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onEnter),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = buttonLabel,
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileNetflixPortrait(
    heroEntry: GroupedGameEntry,
    rows: List<NetflixCategoryRow>,
    systemTitle: String,
    screenshots: List<String>,
    screenshotIdx: Int,
    description: String,
    onSelectGame: (GroupedGameEntry) -> Unit,
    onPlayHero: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val heroGame = heroEntry.mobileGame

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Netflix Mobile Hero Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(250.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF181818),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val activeUrl = screenshots.getOrNull(screenshotIdx % screenshots.size.coerceAtLeast(1))
                    if (activeUrl != null) {
                        Crossfade(targetState = activeUrl, label = "nf_mob_hero") { url ->
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                                contentDescription = heroGame.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        LemuroidGameImage(
                            game = heroGame,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xCC0E0E0E), Color(0xF5141414)),
                                    ),
                                ),
                    )
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "N  SÉRIE • ${systemTitle.uppercase()}",
                            color = Color(0xFFE50914),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                        )
                        Text(
                            text = heroGame.title,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (description.isNotBlank()) {
                            Text(
                                text = description,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White,
                                modifier = Modifier.weight(1f).height(38.dp).clickable(onClick = onPlayHero),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Jogar", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.weight(1f).height(38.dp).clickable(onClick = onToggleFavorite),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        if (heroGame.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Minha Lista", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Categorized Horizontal Carousels (including Top 10!)
        itemsIndexed(rows, key = { _, r -> r.id }) { _, row ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = row.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(row.games) { idx, entry ->
                        val g = entry.mobileGame
                        val isSelected = g.id == heroGame.id
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (row.isTopTen) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color(0xFFE50914),
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(end = 2.dp),
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1F1F1F),
                                border =
                                    BorderStroke(
                                        if (isSelected) 2.5.dp else 1.dp,
                                        if (isSelected) Color.White else Color.White.copy(alpha = 0.12f),
                                    ),
                                modifier =
                                    Modifier
                                        .size(width = 104.dp, height = 136.dp)
                                        .clickable { onSelectGame(entry) },
                            ) {
                                Column(Modifier.fillMaxSize().padding(4.dp)) {
                                    LemuroidGameImage(
                                        game = g,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                    )
                                    Text(
                                        text = g.title,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileRetroTubePortrait(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    screenshots: List<String>,
    screenshotIdx: Int,
    description: String,
    onSelectOrPlay: (Int) -> Unit,
    onPlaySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safeSelectedIdx = if (games.isEmpty()) 0 else selectedIndex.coerceIn(0, games.lastIndex)
    val selectedGame = games.getOrNull(safeSelectedIdx)?.mobileGame ?: return

    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 3D Angled Retro CRT TV on top
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .graphicsLayer {
                        rotationY = 6f
                        cameraDistance = 14f * density
                    },
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF3E2723),
            border = BorderStroke(3.dp, Color(0xFF5D4037)),
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // CRT Glass Screen
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black)
                            .border(2.dp, Color(0xFF1C1C1C), RoundedCornerShape(14.dp)),
                ) {
                    val activeUrl = screenshots.getOrNull(screenshotIdx % screenshots.size.coerceAtLeast(1))
                    if (activeUrl != null) {
                        Crossfade(targetState = activeUrl, label = "crt_mob") { url ->
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                                contentDescription = selectedGame.title,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        LemuroidGameImage(game = selectedGame, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                    // Scanlines
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        var y = 0f
                        while (y < size.height) {
                            drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 1.5f)
                            y += 5f
                        }
                    }
                    Text(
                        text = "CH ${(selectedIndex + 1).toString().padStart(2, '0')}",
                        color = Color(0xFF39FF14),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }

                // Right CRT Rotary Dials & Speaker Panel
                Column(
                    modifier = Modifier.width(50.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF212121)).border(2.dp, Color(0xFFBDBDBD), CircleShape))
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF212121)).border(2.dp, Color(0xFFBDBDBD), CircleShape))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF1744)))
                }
            }
        }

        // Teletext Play/Description Strip
        MobilePlayActionStrip(
            game = selectedGame,
            description = description,
            accentColor = Color(0xFF39FF14),
            onPlay = onPlaySelected,
            onToggleFavorite = onToggleFavorite,
        )

        // Text Channel-Guide Game List
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(games) { index, entry ->
                val isSelected = index == selectedIndex
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) Color(0xFF1B3B1E) else Color(0xCC101412),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF39FF14) else Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectOrPlay(index) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CH ${(index + 1).toString().padStart(2, '0')}",
                            color = if (isSelected) Color(0xFF39FF14) else Color(0xFFFFB74D),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(56.dp),
                        )
                        Text(
                            text = entry.mobileGame.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (entry.mobileGame.isFavorite) {
                            Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MobileLocadoraShelfPortrait(
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    screenshots: List<String>,
    screenshotIdx: Int,
    description: String,
    onSelectOrPlay: (Int) -> Unit,
    onPlaySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safeSelectedIdx = if (games.isEmpty()) 0 else selectedIndex.coerceIn(0, games.lastIndex)
    val selectedGame = games.getOrNull(safeSelectedIdx)?.mobileGame ?: return
    val density = LocalDensity.current.density

    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top Screenshot + Description + Play
        Row(
            modifier = Modifier.fillMaxWidth().height(155.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier.weight(0.55f).fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF140B08),
                border = BorderStroke(1.5.dp, Color(0xFFFFC107)),
            ) {
                val activeUrl = screenshots.getOrNull(screenshotIdx % screenshots.size.coerceAtLeast(1))
                if (activeUrl != null) {
                    Crossfade(targetState = activeUrl, label = "loc_mob_snap") { url ->
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                            contentDescription = selectedGame.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    LemuroidGameImage(game = selectedGame, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }

            // Pulled-out 3D turned Box Showcase
            Surface(
                modifier =
                    Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            rotationY = -10f
                            cameraDistance = 12f * density
                        },
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1D100A),
                border = BorderStroke(2.dp, Color(0xFFFFD54F)),
            ) {
                LemuroidGameImage(
                    game = selectedGame,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        }

        MobilePlayActionStrip(
            game = selectedGame,
            description = description,
            accentColor = Color(0xFFFFC107),
            onPlay = onPlaySelected,
            onToggleFavorite = onToggleFavorite,
        )

        // 3D Wooden Bookshelf rows of spines (tap to pull out & turn!)
        val rows = remember(games) { games.chunked(5) }
        LazyColumn(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2B170E))
                    .border(2.dp, Color(0xFF6D4C41), RoundedCornerShape(12.dp))
                    .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(rows) { rowIdx, rowItems ->
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(112.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        rowItems.forEachIndexed { colIdx, entry ->
                            val globalIdx = rowIdx * 5 + colIdx
                            val isSelected = globalIdx == selectedIndex
                            val rotation by animateFloatAsState(
                                targetValue = if (isSelected) 0f else 18f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                label = "mob_spine_rot",
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) Color(0xFF3E2723) else Color(0xFF1B100C),
                                border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) Color(0xFFFFC107) else Color(0xFF8D6E63)),
                                modifier =
                                    Modifier
                                        .weight(if (isSelected) 1.7f else 1f)
                                        .fillMaxHeight(if (isSelected) 1f else 0.90f)
                                        .graphicsLayer {
                                            rotationY = rotation
                                            cameraDistance = 12f * density
                                        }
                                        .clickable { onSelectOrPlay(globalIdx) },
                            ) {
                                if (isSelected) {
                                    LemuroidGameImage(
                                        game = entry.mobileGame,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize().padding(3.dp),
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().padding(3.dp), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = entry.mobileGame.title,
                                            color = Color(0xFFFFE082),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Wooden shelf plank
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xFF8D6E63), Color(0xFF4E342E))),
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileShowcaseAndGridPortrait(
    theme: FrontendTheme,
    games: List<GroupedGameEntry>,
    selectedIndex: Int,
    systemTitle: String,
    screenshots: List<String>,
    screenshotIdx: Int,
    description: String,
    accentColor: Color,
    onSelectOrPlay: (Int) -> Unit,
    onPlaySelected: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val safeSelectedIdx = if (games.isEmpty()) 0 else selectedIndex.coerceIn(0, games.lastIndex)
    val selectedGame = games.getOrNull(safeSelectedIdx)?.mobileGame ?: return
    val infiniteTransition = rememberInfiniteTransition(label = "mob_vinyl")
    val vinylAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4800, easing = LinearEasing), RepeatMode.Restart),
        label = "mob_vinyl_angle",
    )

    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Top Theme-Specific Showcase Card
        Surface(
            modifier = Modifier.fillMaxWidth().height(172.dp),
            shape =
                if (theme == FrontendTheme.THRASH_METAL) {
                    CutCornerShape(topStart = 12.dp, bottomEnd = 12.dp)
                } else {
                    RoundedCornerShape(12.dp)
                },
            color = Color(0xCC111218),
            border = BorderStroke(2.dp, accentColor.copy(alpha = 0.85f)),
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left Hero visual (3D object for THREE_D, Spinning Vinyl for THRASH_METAL, Screenshot for others)
                when (theme) {
                    FrontendTheme.THREE_D -> {
                        Game3DObject(
                            game = selectedGame,
                            onClick = onPlaySelected,
                            onLongClick = {},
                            onFavoriteToggle = null,
                            isSelected = true,
                            modifier = Modifier.width(136.dp).fillMaxHeight(),
                        )
                    }

                    FrontendTheme.THRASH_METAL -> {
                        Box(modifier = Modifier.width(156.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                            Canvas(modifier = Modifier.align(Alignment.CenterEnd).size(112.dp)) {
                                val r = size.minDimension / 2f
                                val c = Offset(size.width / 2f, size.height / 2f)
                                rotate(vinylAngle, c) {
                                    drawCircle(Color(0xFF0A0A0D), r, c)
                                    drawCircle(Color.White.copy(alpha = 0.10f), r * 0.72f, c, style = Stroke(1.5f))
                                    drawCircle(Color(0xFFCC0000), r * 0.34f, c)
                                    drawCircle(Color.Black, r * 0.08f, c)
                                }
                            }
                            Surface(
                                modifier = Modifier.size(112.dp),
                                color = Color.Black,
                                border = BorderStroke(1.5.dp, Color(0xFFE0E0E0)),
                            ) {
                                LemuroidGameImage(game = selectedGame, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                            }
                        }
                    }

                    else -> {
                        Box(
                            modifier =
                                Modifier
                                    .width(160.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black),
                        ) {
                            val activeUrl = screenshots.getOrNull(screenshotIdx % screenshots.size.coerceAtLeast(1))
                            if (activeUrl != null) {
                                Crossfade(targetState = activeUrl, label = "mob_snap") { url ->
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                                        contentDescription = selectedGame.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                LemuroidGameImage(game = selectedGame, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }

                // Right Info + Wanted Stars (Rockstar) + Description
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (theme == FrontendTheme.ROCKSTAR) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                val stars = if (selectedGame.isFavorite) 5 else 3
                                for (s in 1..5) {
                                    Icon(
                                        Icons.Filled.Star,
                                        null,
                                        tint = if (s <= stars) Color(0xFFF5A623) else Color.White.copy(alpha = 0.2f),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = selectedGame.title,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = if (theme == FrontendTheme.RETRO_80_90) FontStyle.Italic else FontStyle.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text =
                                description.takeIf { it.isNotBlank() }
                                    ?: "$systemTitle • Toque em Jogar para iniciar.",
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor,
                            modifier = Modifier.weight(1f).height(32.dp).clickable(onClick = onPlaySelected),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Text("JOGAR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.14f),
                            modifier = Modifier.size(32.dp).clickable(onClick = onToggleFavorite),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (selectedGame.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    null,
                                    tint = if (selectedGame.isFavorite) Color(0xFFFF2A85) else Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom 3-Column Multi-Row Game Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(games, key = { idx, e -> "${e.mobileGame.id}_$idx" }) { index, entry ->
                val isSelected = index == selectedIndex
                val cardBorder by animateColorAsState(
                    targetValue = if (isSelected) accentColor else Color.White.copy(alpha = 0.14f),
                    label = "mob_card_border",
                )
                Surface(
                    shape =
                        if (theme == FrontendTheme.THRASH_METAL) {
                            CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                        } else {
                            RoundedCornerShape(8.dp)
                        },
                    color = if (isSelected) accentColor.copy(alpha = 0.18f) else Color(0xCC14141C),
                    border = BorderStroke(if (isSelected) 2.5.dp else 1.dp, cardBorder),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .clickable { onSelectOrPlay(index) },
                ) {
                    Column(Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LemuroidGameImage(
                            game = entry.mobileGame,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = entry.mobileGame.title,
                            color = if (isSelected) accentColor else Color.White,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MobilePlayActionStrip(
    game: Game,
    description: String,
    accentColor: Color,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xCC121218),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp).clickable(onClick = onToggleFavorite),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (game.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        null,
                        tint = if (game.isFavorite) Color(0xFFFF2A85) else Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor,
                modifier = Modifier.height(36.dp).clickable(onClick = onPlay),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Text("JOGAR", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun MobileThemeBackdrop(theme: FrontendTheme) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        when (theme) {
            FrontendTheme.RETRO_80_90 -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0318), Color(0xFF2B0846), Color(0xFF090214))))
                val sunR = w * 0.28f
                val sunCenter = Offset(w * 0.5f, h * 0.24f)
                val sunPath = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(sunCenter.x - sunR, sunCenter.y - sunR, sunCenter.x + sunR, sunCenter.y + sunR))
                }
                clipPath(sunPath) {
                    drawRect(
                        Brush.verticalGradient(listOf(Color(0xFFFFE64D), Color(0xFFFF6E27), Color(0xFFFF1F8E))),
                        Offset(sunCenter.x - sunR, sunCenter.y - sunR),
                        Size(sunR * 2f, sunR * 2f),
                    )
                }
            }
            FrontendTheme.ROCKSTAR -> {
                drawRect(Brush.linearGradient(listOf(Color(0xFF0B0C12), Color(0xFF1D1128), Color(0xFF110F14))))
            }
            FrontendTheme.THRASH_METAL -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0406), Color(0xFF0A0A0C), Color(0xFF120506))))
            }
            FrontendTheme.LOCADORA -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF1B0E0A), Color(0xFF28150E), Color(0xFF120906))))
            }
            FrontendTheme.NETFLIX -> {
                drawRect(Color(0xFF141414))
            }
            FrontendTheme.CONSOLE -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF08142B), Color(0xFF0D1D3B), Color(0xFF060C1A))))
            }
            else -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0F1A), Color(0xFF050810))))
            }
        }
    }
}

private fun mobileThemeAccent(theme: FrontendTheme): Color =
    when (theme) {
        FrontendTheme.NETFLIX -> Color(0xFFE50914)
        FrontendTheme.LOCADORA -> Color(0xFFFFC107)
        FrontendTheme.CONSOLE -> Color(0xFF00C2FF)
        FrontendTheme.TV -> Color(0xFF39FF14)
        FrontendTheme.RETRO_80_90 -> Color(0xFF00F0FF)
        FrontendTheme.ROCKSTAR -> Color(0xFFF5A623)
        FrontendTheme.THRASH_METAL -> Color(0xFFFF5252)
        FrontendTheme.THREE_D, FrontendTheme.DEFAULT -> Color(0xFF00E5FF)
    }

private fun mobileThemeHeader(theme: FrontendTheme, systemTitle: String): String =
    when (theme) {
        FrontendTheme.NETFLIX -> "NETFLIX • $systemTitle"
        FrontendTheme.LOCADORA -> "LOCADORA • $systemTitle"
        FrontendTheme.CONSOLE -> "CONSOLE • $systemTitle"
        FrontendTheme.TV -> "TV RETRÔ • $systemTitle"
        FrontendTheme.RETRO_80_90 -> "80/90 • $systemTitle"
        FrontendTheme.ROCKSTAR -> "ROCKSTAR • $systemTitle"
        FrontendTheme.THRASH_METAL -> "THRASH METAL • $systemTitle"
        FrontendTheme.THREE_D, FrontendTheme.DEFAULT -> "3D • $systemTitle"
    }
