package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GameDescriptionText
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GeneratedThemeBackdrop
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.themeArtwork


import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendLoop
import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.Game3DObject
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameImage
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ThemedGameGridItem
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.WoodenShelfDivider
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.app.shared.games.MultiDiscGrouper
import com.swordfish.lemuroid.app.shared.ui.FrontendSoundEngine
import com.swordfish.lemuroid.app.tv.shared.TVGameMetadataHelper
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val METADATA_DEBOUNCE_MS = 120L
private const val ARCADE_GROUP_KEY = "arcade"

private val ARCADE_SYSTEM_IDS =
    setOf(
        SystemID.FBNEO.dbname,
        SystemID.MAME2003PLUS.dbname,
    )

private fun isArcadeSystemId(systemId: String): Boolean = systemId in ARCADE_SYSTEM_IDS

private data class TVSystemGroup(
    val key: String,
    val systemIds: Set<String>,
    val count: Int,
    val isArcade: Boolean,
)

private sealed interface TVFilter {
    data object All : TVFilter
    data object Recents : TVFilter
    data object Favorites : TVFilter
    data object Arcade : TVFilter
    data class System(val systemId: String) : TVFilter
}

private fun TVFilter.selectionKey(): String = when (this) {
    TVFilter.All -> "all"
    TVFilter.Recents -> "recents"
    TVFilter.Favorites -> "favorites"
    TVFilter.Arcade -> "arcade"
    is TVFilter.System -> "system:$systemId"
}

private val GroupedGameEntry.displayGame: Game
    get() =
        when (this) {
            is GroupedGameEntry.Single -> game
            is GroupedGameEntry.MultiDisc -> representative
        }

private val GroupedGameEntry.versionCount: Int
    get() =
        when (this) {
            is GroupedGameEntry.Single -> 1
            is GroupedGameEntry.MultiDisc -> discs.size
        }

/**
 * Gamepad confirm action for game cards and dialog rows.
 * Only consumes A / DPAD_CENTER / ENTER / NUMPAD_ENTER so START, SELECT, Y, L1, and R1
 * propagate cleanly to the root console controller handler.
 */
private fun Modifier.tvConfirmKeyAction(onConfirm: () -> Unit): Modifier =
    this.onPreviewKeyEvent { keyEvent ->
        when (keyEvent.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            -> {
                if (keyEvent.type == KeyEventType.KeyUp) {
                    onConfirm()
                }
                true
            }
            else -> false
        }
    }

private fun Modifier.optionalFocusRequester(focusRequester: FocusRequester?): Modifier =
    if (focusRequester != null) this.focusRequester(focusRequester) else this

private fun Modifier.tvWrapAroundKeyAction(
    isFirst: Boolean,
    isLast: Boolean,
    enabled: Boolean,
    onWrapToLast: () -> Unit,
    onWrapToFirst: () -> Unit,
    vertical: Boolean = false,
): Modifier =
    if (!enabled || (!isFirst && !isLast)) {
        this
    } else {
        val prevKey = if (vertical) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_LEFT
        val nextKey = if (vertical) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_RIGHT
        this.onPreviewKeyEvent { keyEvent ->
            when {
                isFirst && keyEvent.nativeKeyEvent.keyCode == prevKey -> {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        onWrapToLast()
                    }
                    true
                }
                isLast && keyEvent.nativeKeyEvent.keyCode == nextKey -> {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        onWrapToFirst()
                    }
                    true
                }
                else -> false
            }
        }
    }

private fun consoleAccentColor(systemKey: String): Color =
    when (systemKey) {
        ARCADE_GROUP_KEY, SystemID.FBNEO.dbname, SystemID.MAME2003PLUS.dbname -> Color(0xFFFF9100)
        SystemID.SNES.dbname -> Color(0xFF8B6CE8)
        SystemID.NES.dbname -> Color(0xFFE53935)
        SystemID.GENESIS.dbname, SystemID.SEGACD.dbname -> Color(0xFF1E88E5)
        SystemID.PSX.dbname -> Color(0xFF0070D1)
        SystemID.N64.dbname -> Color(0xFF00BFA5)
        SystemID.GBA.dbname -> Color(0xFF5C6BC0)
        SystemID.GB.dbname, SystemID.GBC.dbname -> Color(0xFF7CB342)
        SystemID.PSP.dbname -> Color(0xFF00ACC1)
        SystemID.NDS.dbname, SystemID.NINTENDO_3DS.dbname -> Color(0xFF29B6F6)
        SystemID.SMS.dbname -> Color(0xFF1976D2)
        SystemID.GG.dbname -> Color(0xFF26A69A)
        SystemID.ATARI2600.dbname, SystemID.ATARI7800.dbname -> Color(0xFFFF7043)
        SystemID.PC_ENGINE.dbname -> Color(0xFFEC407A)
        else -> Color(0xFFF5B700)
    }

private fun themeAccentColor(theme: FrontendTheme): Color =
    when (theme) {
        FrontendTheme.NETFLIX -> Color(0xFFF04F5E)
        FrontendTheme.LOCADORA -> Color(0xFFF5B700)
        FrontendTheme.CONSOLE -> Color(0xFF00C2FF)
        FrontendTheme.TV -> Color(0xFF9ADF8D)
        FrontendTheme.RETRO_80_90 -> Color(0xFF00F0FF)
        FrontendTheme.ROCKSTAR -> Color(0xFFE7A252)
        FrontendTheme.THRASH_METAL -> Color(0xFFD84D5B)
        FrontendTheme.THREE_D, FrontendTheme.DEFAULT -> Color(0xFF00E5FF)
    }

/**
 * Full-screen 10-foot console-style TV frontend rendered in Jetpack Compose
 * when a non-default [FrontendTheme] is active.
 */
@Composable
fun TVThemedFrontend(
    retrogradeDb: RetrogradeDatabase,
    gameInteractor: GameInteractor,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    onRequestExit: () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalFrontendMotion provides rememberFrontendMotion(),
    ) {
        TVThemedFrontendContent(retrogradeDb, gameInteractor, onOpenSettings, onRescan, onRequestExit)
    }
}

@Composable
private fun TVThemedFrontendContent(
    retrogradeDb: RetrogradeDatabase,
    gameInteractor: GameInteractor,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    onRequestExit: () -> Unit,
) {
    val motion = LocalFrontendMotion.current
    val theme = LocalFrontendTheme.current
    val context = LocalContext.current
    val gameDao = remember(retrogradeDb) { retrogradeDb.gameDao() }

    val systemCounts by remember(gameDao) { gameDao.selectSystemsWithCount() }.collectAsState(initial = emptyList())
    val systemIds = remember(systemCounts) { systemCounts.map { it.systemId } }

    val allGames by remember(systemIds) {
        if (systemIds.isEmpty()) flowOf(emptyList()) else gameDao.selectBySystemsList(systemIds)
    }.collectAsState(initial = emptyList())

    val recentGames by remember(gameDao) { gameDao.selectFirstUnfavoriteRecents(12) }.collectAsState(initial = emptyList())
    val favoriteGames by remember(gameDao) { gameDao.selectFirstFavorites(20) }.collectAsState(initial = emptyList())

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var selectedTabKey by rememberSaveable { mutableStateOf<String?>(null) }
    var filterSwitchToken by rememberSaveable { mutableIntStateOf(0) }
    var locadoraSelectedConsoleKey by rememberSaveable { mutableStateOf<String?>(null) }
    var locadoraRingSelectedIndex by rememberSaveable { mutableIntStateOf(0) }
    var consoleRoomSelectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var consoleRoomSelectedIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(theme) {
        if (theme != FrontendTheme.LOCADORA) {
            locadoraSelectedConsoleKey = null
        }
        if (theme != FrontendTheme.CONSOLE) {
            consoleRoomSelectedKey = null
        }
    }

    val groupedSystems =
        remember(systemCounts) {
            val arcadeCount = systemCounts.filter { isArcadeSystemId(it.systemId) }.sumOf { it.count }
            var arcadeAdded = false
            buildList {
                systemCounts.forEach { sc ->
                    if (isArcadeSystemId(sc.systemId)) {
                        if (!arcadeAdded && arcadeCount > 0) {
                            add(
                                TVSystemGroup(
                                    key = ARCADE_GROUP_KEY,
                                    systemIds = ARCADE_SYSTEM_IDS,
                                    count = arcadeCount,
                                    isArcade = true,
                                ),
                            )
                            arcadeAdded = true
                        }
                    } else {
                        add(
                            TVSystemGroup(
                                key = sc.systemId,
                                systemIds = setOf(sc.systemId),
                                count = sc.count,
                                isArcade = false,
                            ),
                        )
                    }
                }
            }
        }

    val availableFilters =
        remember(groupedSystems) {
            buildList<TVFilter> {
                add(TVFilter.All)
                add(TVFilter.Recents)
                add(TVFilter.Favorites)
                groupedSystems.forEach { group ->
                    if (group.isArcade) {
                        add(TVFilter.Arcade)
                    } else {
                        add(TVFilter.System(group.key))
                    }
                }
            }
        }

    val safeTabIndex =
        availableFilters.indexOfFirst { it.selectionKey() == selectedTabKey }.takeIf { it >= 0 }
            ?: if (availableFilters.isEmpty()) 0 else selectedTabIndex.coerceIn(0, availableFilters.lastIndex)
    val selectedFilter = availableFilters.getOrElse(safeTabIndex) { TVFilter.All }

    val locadoraConsoles =
        groupedSystems.map { group ->
            LocadoraConsoleItem(
                key = group.key,
                title = if (group.isArcade) "ARCADE" else resolveSystemTitle(group.key),
                gameCount = group.count,
                accentColor = consoleAccentColor(group.key),
            )
        }

    if (theme == FrontendTheme.LOCADORA && locadoraSelectedConsoleKey == null) {
        LocadoraConsoleRingScreen(
            consoles = locadoraConsoles,
            selectedIndex = locadoraRingSelectedIndex,
            onIndexChange = { locadoraRingSelectedIndex = it },
            onConsoleSelect = { console ->
                val targetFilter =
                    if (console.key == ARCADE_GROUP_KEY) {
                        TVFilter.Arcade
                    } else {
                        TVFilter.System(console.key)
                    }
                val targetIndex = availableFilters.indexOf(targetFilter)
                if (targetIndex >= 0) {
                    selectedTabIndex = targetIndex
                    selectedTabKey = availableFilters.getOrNull(targetIndex)?.selectionKey()
                }
                filterSwitchToken++
                locadoraSelectedConsoleKey = console.key
            },
            onOpenSettings = onOpenSettings,
            onRescan = onRescan,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    if (theme == FrontendTheme.CONSOLE && consoleRoomSelectedKey == null) {
        val roomConsoles =
            groupedSystems.map { group ->
                ConsoleRoomItem(
                    key = group.key,
                    title = if (group.isArcade) "ARCADE" else resolveSystemTitle(group.key),
                    company = resolveConsoleCompany(group.key),
                    gameCount = group.count,
                    accentColor = consoleAccentColor(group.key),
                )
            }
        ConsoleRoomScreen(
            consoles = roomConsoles,
            selectedIndex = consoleRoomSelectedIndex,
            onIndexChange = { consoleRoomSelectedIndex = it },
            onConsoleSelect = { console ->
                val targetFilter =
                    if (console.key == ARCADE_GROUP_KEY) {
                        TVFilter.Arcade
                    } else {
                        TVFilter.System(console.key)
                    }
                val targetIndex = availableFilters.indexOf(targetFilter)
                if (targetIndex >= 0) {
                    selectedTabIndex = targetIndex
                    selectedTabKey = availableFilters.getOrNull(targetIndex)?.selectionKey()
                }
                filterSwitchToken++
                consoleRoomSelectedKey = console.key
            },
            onOpenSettings = onOpenSettings,
            onRescan = onRescan,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val groupedAll = remember(allGames) { MultiDiscGrouper.group(allGames) }
    val groupedRecents = remember(recentGames) { MultiDiscGrouper.group(recentGames) }
    val groupedFavorites = remember(favoriteGames) { MultiDiscGrouper.group(favoriteGames) }

    val filteredEntries =
        remember(selectedFilter, groupedAll, groupedRecents, groupedFavorites) {
            when (selectedFilter) {
                TVFilter.All -> groupedAll
                TVFilter.Recents -> groupedRecents
                TVFilter.Favorites -> groupedFavorites
                TVFilter.Arcade -> groupedAll.filter { isArcadeSystemId(it.displayGame.systemId) }
                is TVFilter.System -> {
                    val sysId = selectedFilter.systemId
                    groupedAll.filter { it.displayGame.systemId == sysId }
                }
            }
        }

    val activeSystemKey =
        when (selectedFilter) {
            TVFilter.Arcade -> ARCADE_GROUP_KEY
            is TVFilter.System -> selectedFilter.systemId
            else -> filteredEntries.firstOrNull()?.displayGame?.systemId ?: "all"
        }
    val activeSystemTitle =
        when (selectedFilter) {
            TVFilter.All -> "Todos os Jogos"
            TVFilter.Recents -> "Recentes"
            TVFilter.Favorites -> "Favoritos"
            TVFilter.Arcade -> "ARCADE"
            is TVFilter.System -> resolveSystemTitle(selectedFilter.systemId)
        }

    val netflixRows =
        remember(theme, filteredEntries, groupedRecents, groupedFavorites, activeSystemTitle) {
            if (theme == FrontendTheme.NETFLIX) {
                buildNetflixCategoryRows(
                    games = filteredEntries,
                    recentGames = groupedRecents,
                    favoriteGames = groupedFavorites,
                    systemTitle = activeSystemTitle,
                )
            } else {
                emptyList()
            }
        }

    // State-driven selection indices: selector can NEVER disappear or lose focus!
    var selectedGameIndex by rememberSaveable { mutableIntStateOf(0) }
    var netflixRowIndex by rememberSaveable { mutableIntStateOf(0) }
    var netflixColIndex by rememberSaveable { mutableIntStateOf(0) }
    var returnToGameId by rememberSaveable { mutableStateOf<Int?>(null) }

    val navigation = listOf(selectedTabKey, filterSwitchToken, theme, locadoraSelectedConsoleKey, consoleRoomSelectedKey)
    var previousNavigation by remember { mutableStateOf(navigation) }
    LaunchedEffect(navigation) {
        // A remounted browser restores its selection; only a deliberate filter change resets it.
        if (previousNavigation != navigation) {
            returnToGameId = null
            selectedGameIndex = 0
            netflixRowIndex = 0
            netflixColIndex = 0
            previousNavigation = navigation
        }
    }
    LaunchedEffect(returnToGameId, filteredEntries, netflixRows) {
        val id = returnToGameId ?: return@LaunchedEffect
        if (theme == FrontendTheme.NETFLIX) {
            netflixRows.forEachIndexed { row, data ->
                val col = data.games.indexOfFirst { it.displayGame.id == id }
                if (col >= 0) {
                    netflixRowIndex = row
                    netflixColIndex = col
                    return@LaunchedEffect
                }
            }
        } else {
            val index = filteredEntries.indexOfFirst { it.displayGame.id == id }
            if (index >= 0) selectedGameIndex = index
        }
    }

    val safeGameIndex =
        if (filteredEntries.isEmpty()) 0 else selectedGameIndex.coerceIn(0, filteredEntries.lastIndex)
    val safeNetflixRow =
        if (netflixRows.isEmpty()) 0 else netflixRowIndex.coerceIn(0, netflixRows.lastIndex)
    val activeNetflixRowGames = netflixRows.getOrNull(safeNetflixRow)?.games.orEmpty()
    val safeNetflixCol =
        if (activeNetflixRowGames.isEmpty()) 0 else netflixColIndex.coerceIn(0, activeNetflixRowGames.lastIndex)

    val activeEntry: GroupedGameEntry? =
        if (theme == FrontendTheme.NETFLIX) {
            activeNetflixRowGames.getOrNull(safeNetflixCol) ?: filteredEntries.firstOrNull()
        } else {
            filteredEntries.getOrNull(safeGameIndex)
        }
    val activeGame = activeEntry?.displayGame

    val screenshots =
        remember(activeGame?.id) {
            activeGame?.let { TVGameMetadataHelper.screenshotGalleryUrlsFor(it) } ?: emptyList()
        }
    var screenshotIndex by remember(activeGame?.id) { mutableIntStateOf(0) }
    val sharedSlideshowMillis = if (theme == FrontendTheme.THREE_D || theme == FrontendTheme.DEFAULT) 0L else motion.slideshowMillis
    LaunchedEffect(activeGame?.id, screenshots.size, sharedSlideshowMillis) {
        screenshotIndex = 0
        if (screenshots.size > 1 && sharedSlideshowMillis > 0L) {
            while (true) {
                delay(sharedSlideshowMillis)
                screenshotIndex = (screenshotIndex + 1) % screenshots.size
            }
        }
    }

    // Debounce metadata fetch by 120ms so fast D-Pad scrolling stays locked at 60 FPS.
    var gameDescription by remember(activeGame?.id) { mutableStateOf("") }
    LaunchedEffect(activeGame?.id) {
        val target = activeGame ?: return@LaunchedEffect
        delay(METADATA_DEBOUNCE_MS)
        gameDescription = TVGameMetadataHelper.fetchGameDescription(context, target)
    }

    var activeMultiDiscGroup by remember { mutableStateOf<GroupedGameEntry.MultiDisc?>(null) }
    var actionDialogGame by remember { mutableStateOf<Game?>(null) }
    val isDialogOpen = activeMultiDiscGroup != null || actionDialogGame != null

    val rootFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    // Always keep keyboard/gamepad focus anchored on the root Surface whenever no dialog is open.
    LaunchedEffect(
        isDialogOpen,
        safeTabIndex,
        filteredEntries.size,
        filterSwitchToken,
        theme,
        locadoraSelectedConsoleKey,
        consoleRoomSelectedKey,
    ) {
        if (!isDialogOpen) {
            delay(40)
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    val onEntryConfirm: (GroupedGameEntry) -> Unit = { entry ->
        FrontendSoundEngine.playConfirm(context, theme)
        when (entry) {
            is GroupedGameEntry.Single -> {
                returnToGameId = entry.displayGame.id
                gameInteractor.onGamePlay(entry.game)
            }
            is GroupedGameEntry.MultiDisc -> activeMultiDiscGroup = entry
        }
    }

    val switchFilterByDelta: (Int) -> Unit = { delta ->
        if (availableFilters.size > 1) {
            val targetIdx = (safeTabIndex + delta + availableFilters.size) % availableFilters.size
            selectedTabIndex = targetIdx
            selectedTabKey = availableFilters.getOrNull(targetIdx)?.selectionKey()
            filterSwitchToken++
            FrontendSoundEngine.playSwitchSystem(context, theme)
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    val selectFilterDirectly: (TVFilter) -> Unit = { filter ->
        val targetIdx = availableFilters.indexOf(filter)
        if (targetIdx >= 0 && targetIdx != safeTabIndex) {
            selectedTabIndex = targetIdx
            selectedTabKey = availableFilters.getOrNull(targetIdx)?.selectionKey()
            filterSwitchToken++
            FrontendSoundEngine.playSwitchSystem(context, theme)
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    val moveLinearIndex: (Int) -> Unit = { delta ->
        val count = filteredEntries.size
        if (count > 0) {
            // Read live state: consecutive key events can arrive before the next composition.
            val current = selectedGameIndex.coerceIn(0, count - 1)
            val next = ((current + delta) % count + count) % count
            if (next != current) {
                returnToGameId = null
                selectedGameIndex = next
                FrontendSoundEngine.playNavigate(context, theme)
            }
        }
    }

    val moveGridVertical: (Int, Int) -> Unit = { rowDelta, columns ->
        val count = filteredEntries.size
        if (count > 0) {
            val target = safeGameIndex + rowDelta * columns
            val next =
                when {
                    target in 0 until count -> target
                    rowDelta > 0 -> safeGameIndex % columns
                    else -> {
                        val col = safeGameIndex % columns
                        val lastRowStart = ((count - 1) / columns) * columns
                        (lastRowStart + col).coerceAtMost(count - 1)
                    }
                }
            if (next != safeGameIndex) {
                returnToGameId = null
                selectedGameIndex = next
                FrontendSoundEngine.playNavigate(context, theme)
            }
        }
    }

    val canReturnToConsoleSelector =
        (theme == FrontendTheme.LOCADORA && locadoraSelectedConsoleKey != null) ||
            (theme == FrontendTheme.CONSOLE && consoleRoomSelectedKey != null)

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    if (isDialogOpen) return@onPreviewKeyEvent false

                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (theme == FrontendTheme.NETFLIX) {
                                    val rowSize = activeNetflixRowGames.size
                                    if (rowSize > 0) {
                                        returnToGameId = null
                                        netflixColIndex = (safeNetflixCol - 1 + rowSize) % rowSize
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    }
                                } else {
                                    moveLinearIndex(-1)
                                }
                            }
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                if (theme == FrontendTheme.NETFLIX) {
                                    val rowSize = activeNetflixRowGames.size
                                    if (rowSize > 0) {
                                        returnToGameId = null
                                        netflixColIndex = (safeNetflixCol + 1) % rowSize
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    }
                                } else {
                                    moveLinearIndex(+1)
                                }
                            }
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (theme) {
                                    FrontendTheme.NETFLIX -> {
                                        val rowCount = netflixRows.size
                                        if (rowCount > 0) {
                                            val nextRow = (safeNetflixRow - 1 + rowCount) % rowCount
                                            returnToGameId = null
                                            netflixRowIndex = nextRow
                                            val nextSize = netflixRows[nextRow].games.size
                                            returnToGameId = null
                                            netflixColIndex = if (nextSize > 0) safeNetflixCol.coerceAtMost(nextSize - 1) else 0
                                            FrontendSoundEngine.playNavigate(context, theme)
                                        }
                                    }
                                    FrontendTheme.RETRO_80_90 -> moveGridVertical(-1, RETRO_8090_GRID_COLUMNS)
                                    FrontendTheme.ROCKSTAR -> moveLinearIndex(-1)
                                    FrontendTheme.THRASH_METAL -> moveGridVertical(-1, THRASH_METAL_GRID_COLUMNS)
                                    FrontendTheme.LOCADORA -> moveGridVertical(-1, 6)
                                    else -> moveLinearIndex(-1)
                                }
                            }
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (theme) {
                                    FrontendTheme.NETFLIX -> {
                                        val rowCount = netflixRows.size
                                        if (rowCount > 0) {
                                            val nextRow = (safeNetflixRow + 1) % rowCount
                                            returnToGameId = null
                                            netflixRowIndex = nextRow
                                            val nextSize = netflixRows[nextRow].games.size
                                            returnToGameId = null
                                            netflixColIndex = if (nextSize > 0) safeNetflixCol.coerceAtMost(nextSize - 1) else 0
                                            FrontendSoundEngine.playNavigate(context, theme)
                                        }
                                    }
                                    FrontendTheme.RETRO_80_90 -> moveGridVertical(+1, RETRO_8090_GRID_COLUMNS)
                                    FrontendTheme.ROCKSTAR -> moveLinearIndex(+1)
                                    FrontendTheme.THRASH_METAL -> moveGridVertical(+1, THRASH_METAL_GRID_COLUMNS)
                                    FrontendTheme.LOCADORA -> moveGridVertical(+1, 6)
                                    else -> moveLinearIndex(+1)
                                }
                            }
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BUTTON_A,
                        -> {
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                activeEntry?.let { onEntryConfirm(it) }
                            }
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_B,
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE,
                        -> {
                            if (canReturnToConsoleSelector) {
                                if (keyEvent.type == KeyEventType.KeyUp) {
                                    FrontendSoundEngine.playBack(context, theme)
                                    if (theme == FrontendTheme.LOCADORA) {
                                        locadoraSelectedConsoleKey = null
                                    } else if (theme == FrontendTheme.CONSOLE) {
                                        consoleRoomSelectedKey = null
                                    }
                                }
                                true
                            } else {
                                if (keyEvent.type == KeyEventType.KeyUp) onRequestExit()
                                true
                            }
                        }

                        KeyEvent.KEYCODE_BUTTON_L1,
                        KeyEvent.KEYCODE_CHANNEL_DOWN,
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                switchFilterByDelta(-1)
                            }
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_R1,
                        KeyEvent.KEYCODE_CHANNEL_UP,
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        -> {
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                switchFilterByDelta(+1)
                            }
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_START,
                        KeyEvent.KEYCODE_MENU,
                        -> {
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                FrontendSoundEngine.playConfirm(context, theme)
                                onOpenSettings()
                            }
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_SELECT -> {
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                FrontendSoundEngine.playConfirm(context, theme)
                                onRescan()
                            }
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_Y -> {
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                activeGame?.let { target ->
                                    gameInteractor.onFavoriteToggle(target, !target.isFavorite)
                                    FrontendSoundEngine.playFavorite(context, theme)
                                }
                            }
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_X -> {
                            if (keyEvent.type == KeyEventType.KeyUp) {
                                (activeEntry as? GroupedGameEntry.Single)?.let { entry ->
                                    FrontendSoundEngine.playConfirm(context, theme)
                                    returnToGameId = entry.game.id
                                    gameInteractor.onGamePreferences(entry.game)
                                }
                            }
                            true
                        }

                        else -> false
                    }
                },
        color = Color.Black,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            TVThemeAmbientBackground(theme = theme)

            Column(modifier = Modifier.fillMaxSize()) {
                // Non-focusable Top System Bar (L1 / R1 switched)
                TVTopBar(
                    theme = theme,
                    groupedSystems = groupedSystems,
                    availableFilters = availableFilters,
                    selectedFilter = selectedFilter,
                    onSelectFilter = selectFilterDirectly,
                )

                // Main Content Area (100% state-driven selection — selector never disappears!)
                if (filteredEntries.isEmpty() || activeGame == null) {
                    TVEmptyState(
                        accentColor = themeAccentColor(theme),
                        focusAnchor = firstItemFocusRequester,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else {
                    val versionCount = activeEntry?.versionCount ?: 1
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (theme) {
                            FrontendTheme.NETFLIX ->
                                NetflixTVLayout(
                                    rows = netflixRows,
                                    selectedRowIndex = safeNetflixRow,
                                    selectedColIndex = safeNetflixCol,
                                    systemTitle = activeSystemTitle,
                                    screenshots = screenshots,
                                    screenshotIndex = screenshotIndex,
                                    description = gameDescription,
                                    onSelectPosition = { row, col ->
                                        returnToGameId = null
                                        netflixRowIndex = row
                                        returnToGameId = null
                                        netflixColIndex = col
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onConfirmGame = onEntryConfirm,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            FrontendTheme.LOCADORA ->
                                LocadoraShelfLayout(
                                    games = filteredEntries,
                                    selectedIndex = safeGameIndex,
                                    systemTitle = activeSystemTitle,
                                    systemKey = activeSystemKey,
                                    screenshots = screenshots,
                                    screenshotIndex = screenshotIndex,
                                    description = gameDescription,
                                    onSelectIndex = { idx ->
                                        returnToGameId = null
                                        selectedGameIndex = idx
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onConfirmGame = onEntryConfirm,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            FrontendTheme.CONSOLE ->
                                ConsoleDashboardLayout(
                                    games = filteredEntries,
                                    selectedIndex = safeGameIndex,
                                    systemTitle = activeSystemTitle,
                                    systemKey = activeSystemKey,
                                    screenshots = screenshots,
                                    screenshotIndex = screenshotIndex,
                                    description = gameDescription,
                                    onSelectIndex = { idx ->
                                        returnToGameId = null
                                        selectedGameIndex = idx
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onConfirmGame = onEntryConfirm,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            FrontendTheme.TV ->
                                RetroTubeTVLayout(
                                    games = filteredEntries,
                                    selectedIndex = safeGameIndex,
                                    systemTitle = activeSystemTitle,
                                    systemKey = activeSystemKey,
                                    screenshots = screenshots,
                                    screenshotIndex = screenshotIndex,
                                    description = gameDescription,
                                    onSelectIndex = { idx ->
                                        returnToGameId = null
                                        selectedGameIndex = idx
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onConfirmGame = onEntryConfirm,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            FrontendTheme.RETRO_80_90 ->
                                Retro8090TVLayout(
                                    games = filteredEntries,
                                    selectedIndex = safeGameIndex,
                                    systemTitle = activeSystemTitle,
                                    systemKey = activeSystemKey,
                                    screenshots = screenshots,
                                    screenshotIndex = screenshotIndex,
                                    description = gameDescription,
                                    onSelectIndex = { idx ->
                                        returnToGameId = null
                                        selectedGameIndex = idx
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onConfirmGame = onEntryConfirm,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            FrontendTheme.ROCKSTAR ->
                                RockstarTVLayout(
                                    games = filteredEntries,
                                    selectedIndex = safeGameIndex,
                                    systemTitle = activeSystemTitle,
                                    systemKey = activeSystemKey,
                                    screenshots = screenshots,
                                    screenshotIndex = screenshotIndex,
                                    description = gameDescription,
                                    onSelectIndex = { idx ->
                                        returnToGameId = null
                                        selectedGameIndex = idx
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onConfirmGame = onEntryConfirm,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            FrontendTheme.THRASH_METAL ->
                                ThrashMetalTVLayout(
                                    games = filteredEntries,
                                    selectedIndex = safeGameIndex,
                                    systemTitle = activeSystemTitle,
                                    systemKey = activeSystemKey,
                                    screenshots = screenshots,
                                    screenshotIndex = screenshotIndex,
                                    description = gameDescription,
                                    onSelectIndex = { idx ->
                                        returnToGameId = null
                                        selectedGameIndex = idx
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onConfirmGame = onEntryConfirm,
                                    modifier = Modifier.fillMaxSize(),
                                )

                            FrontendTheme.THREE_D,
                            FrontendTheme.DEFAULT,
                            ->
                                TVThreeDShowcaseLayout(
                                    focusedGame = activeGame,
                                    versionCount = versionCount,
                                    description = gameDescription,
                                    entries = filteredEntries,
                                    selectedIndex = safeGameIndex,
                                    onSelectIndex = { idx ->
                                        returnToGameId = null
                                        selectedGameIndex = idx
                                        FrontendSoundEngine.playNavigate(context, theme)
                                    },
                                    onEntryClick = onEntryConfirm,
                                )
                        }
                    }
                }

                // Non-focusable Console Footer Legend Bar (shown on EVERY theme)
                TVConsoleFooterBar(
                    theme = theme,
                    isFavorite = activeGame?.isFavorite == true,
                    showBackToConsoles = canReturnToConsoleSelector,
                )
            }
        }
    }

    activeMultiDiscGroup?.let { group ->
        TVGroupedGameSelectionDialog(
            group = group,
            theme = theme,
            accentColor = themeAccentColor(theme),
            onConfigure = { disc ->
                returnToGameId = group.displayGame.id
                activeMultiDiscGroup = null
                gameInteractor.onGamePreferences(disc)
            },
            onSelect = { disc ->
                FrontendSoundEngine.playConfirm(context, theme)
                activeMultiDiscGroup = null
                returnToGameId = group.displayGame.id
                gameInteractor.onGamePlay(disc)
            },
            onDismiss = {
                FrontendSoundEngine.playBack(context, theme)
                activeMultiDiscGroup = null
            },
        )
    }

    actionDialogGame?.let { target ->
        TVGameActionDialog(
            game = target,
            theme = theme,
            accentColor = themeAccentColor(theme),
            onPlay = {
                FrontendSoundEngine.playConfirm(context, theme)
                actionDialogGame = null
                returnToGameId = activeGame?.id ?: target.id
                gameInteractor.onGamePlay(target)
            },
            onRestart = {
                FrontendSoundEngine.playConfirm(context, theme)
                actionDialogGame = null
                returnToGameId = activeGame?.id ?: target.id
                gameInteractor.onGameRestart(target)
            },
            onConfigure = { actionDialogGame = null; gameInteractor.onGamePreferences(target) },
            onToggleFavorite = {
                FrontendSoundEngine.playFavorite(context, theme)
                actionDialogGame = null
                gameInteractor.onFavoriteToggle(target, !target.isFavorite)
            },
            onDismiss = {
                FrontendSoundEngine.playBack(context, theme)
                actionDialogGame = null
            },
        )
    }
}

// ---------------------------------------------------------------------------
// AMBIENT BACKGROUND
// ---------------------------------------------------------------------------
@Composable
private fun TVThemeAmbientBackground(theme: FrontendTheme) {
    if (theme == FrontendTheme.CONSOLE || theme == FrontendTheme.THREE_D) {
        GeneratedThemeBackdrop(theme, Modifier.fillMaxSize())
    }
}

// ---------------------------------------------------------------------------
// NON-FOCUSABLE TOP SYSTEM BAR (L1 / R1 CONTROLLED)
// ---------------------------------------------------------------------------
@Composable
private fun TVTopBar(
    theme: FrontendTheme,
    groupedSystems: List<TVSystemGroup>,
    availableFilters: List<TVFilter>,
    selectedFilter: TVFilter,
    onSelectFilter: (TVFilter) -> Unit,
) {
    val motion = LocalFrontendMotion.current
    val accent = themeAccentColor(theme)
    val listState = rememberLazyListState()
    val selectedIndex =
        remember(availableFilters, selectedFilter) {
            availableFilters.indexOf(selectedFilter).coerceAtLeast(0)
        }

    LaunchedEffect(selectedIndex) {
        runCatching { if (motion.animatedScroll) listState.animateScrollToItem(selectedIndex) else listState.scrollToItem(selectedIndex) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false },
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TVBumperBadge(label = "L1", accentColor = accent)

            LazyRow(
                state = listState,
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item(key = "filter_all") {
                    TVNonFocusableFilterPill(
                        label = "Todos",
                        selected = selectedFilter is TVFilter.All,
                        accentColor = accent,
                        onClick = { onSelectFilter(TVFilter.All) },
                    )
                }
                item(key = "filter_recents") {
                    TVNonFocusableFilterPill(
                        label = "Recentes",
                        selected = selectedFilter is TVFilter.Recents,
                        accentColor = accent,
                        onClick = { onSelectFilter(TVFilter.Recents) },
                    )
                }
                item(key = "filter_favorites") {
                    TVNonFocusableFilterPill(
                        label = "Favoritos",
                        selected = selectedFilter is TVFilter.Favorites,
                        accentColor = accent,
                        onClick = { onSelectFilter(TVFilter.Favorites) },
                    )
                }
                items(groupedSystems, key = { "filter_sys_${it.key}" }) { group ->
                    val label = if (group.isArcade) "ARCADE" else resolveSystemShortName(group.key)
                    val targetFilter = if (group.isArcade) TVFilter.Arcade else TVFilter.System(group.key)
                    TVNonFocusableFilterPill(
                        label = "$label (${group.count})",
                        selected = selectedFilter == targetFilter,
                        accentColor = accent,
                        onClick = { onSelectFilter(targetFilter) },
                    )
                }
            }

            TVBumperBadge(label = "R1", accentColor = accent)
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(accent.copy(alpha = 0.30f)),
        )
    }
}

@Composable
private fun TVBumperBadge(
    label: String,
    accentColor: Color,
) {
    val glyph =
        when (label.uppercase(java.util.Locale.ROOT).trim()) {
            "L1", "LB" -> com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.L1
            "R1", "RB" -> com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.R1
            else -> null
        }
    if (glyph != null) {
        com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadButtonIcon(
            glyph = glyph,
            size = 22.dp,
            tint = accentColor,
            modifier = Modifier.focusProperties { canFocus = false },
        )
    } else {
        Surface(
            modifier = Modifier.focusProperties { canFocus = false },
            color = accentColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.55f)),
        ) {
            Text(
                text = label,
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun TVNonFocusableFilterPill(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) accentColor else Color.White.copy(alpha = 0.08f),
        label = "filter_pill_bg",
    )
    val contentColor = if (selected) Color.Black else Color.White.copy(alpha = 0.78f)

    Surface(
        modifier =
            Modifier
                .focusProperties { canFocus = false }
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = bgColor,
        border =
            if (selected) {
                BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f))
            } else {
                BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
            },
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// NON-FOCUSABLE CONSOLE FOOTER LEGEND BAR
// ---------------------------------------------------------------------------
@Composable
private fun TVConsoleFooterBar(
    theme: FrontendTheme,
    isFavorite: Boolean,
    showBackToConsoles: Boolean = false,
) {
    val accent = themeAccentColor(theme)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(accent.copy(alpha = 0.25f)),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xE006080E))
                    .padding(horizontal = 20.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        ) {
            if (showBackToConsoles) {
                com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                    glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.B,
                    label = "Consoles",
                    textColor = Color.White.copy(alpha = 0.88f),
                )
            }
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.L1_R1,
                label = "Console",
                textColor = Color.White.copy(alpha = 0.88f),
            )
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.DPAD,
                label = "Selecionar",
                textColor = Color.White.copy(alpha = 0.88f),
            )
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.A,
                label = "Jogar",
                textColor = Color.White,
            )
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.X,
                label = "Núcleo / Modo",
                textColor = Color.White,
            )
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.Y,
                label = if (isFavorite) "★ Favorito" else "Favorito",
                textColor = Color.White.copy(alpha = 0.88f),
            )
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.SELECT,
                label = "Reescanear",
                textColor = Color.White.copy(alpha = 0.82f),
            )
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintPill(
                glyph = com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph.START,
                label = "Opções",
                textColor = Color.White.copy(alpha = 0.82f),
            )
        }
        Text(
            text = "Analógico direito ↑↓ · Rolar descrição",
            color = Color.White.copy(alpha = 0.70f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 3.dp),
        )
    }
}

@Composable
private fun TVLegendChip(
    keyBadge: String,
    label: String,
    accentColor: Color,
    highlight: Boolean = false,
) {
    Row(
        modifier = Modifier.focusProperties { canFocus = false },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = if (highlight) accentColor else accentColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(5.dp),
            border = BorderStroke(1.dp, if (highlight) Color.White.copy(alpha = 0.85f) else accentColor.copy(alpha = 0.55f)),
        ) {
            Text(
                text = keyBadge,
                color = if (highlight) Color.Black else accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Text(
            text = label,
            color = Color.White.copy(alpha = if (highlight) 0.96f else 0.78f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// EMPTY STATE (NON-TRAPPED FOCUS ANCHOR)
// ---------------------------------------------------------------------------
@Composable
private fun TVEmptyState(
    accentColor: Color,
    focusAnchor: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .focusRequester(focusAnchor)
                .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Nenhum jogo encontrado nesta seção",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Pressione [L1 / R1] para trocar de console, [SELECT] para reescanear ou [START] para abrir as opções.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TVLegendChip(keyBadge = "L1 / R1", label = "Trocar Console", accentColor = accentColor)
                TVLegendChip(keyBadge = "SELECT", label = "Reescanear", accentColor = accentColor, highlight = true)
                TVLegendChip(keyBadge = "START", label = "Opções", accentColor = accentColor)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// REUSABLE LARGE MULTI-SCREENSHOT SLIDESHOW + GAME INFO PANEL
// ---------------------------------------------------------------------------
@Composable
fun TVHeroMediaAndInfoPanel(
    game: Game?,
    description: String,
    theme: FrontendTheme,
    modifier: Modifier = Modifier,
    versionCount: Int = 1,
    monospaceStyle: Boolean = false,
    extraBadgeText: String? = null,
    showStarRating: Boolean = false,
    uppercaseTitle: Boolean = false,
) {
    val accent = themeAccentColor(theme)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .focusProperties { canFocus = false },
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TVScreenshotSlideshow(
            game = game,
            accentColor = accent,
            showScanlines = theme == FrontendTheme.TV,
            modifier =
                Modifier
                    .weight(0.54f)
                    .fillMaxHeight(),
        )

        TVGameInfoPanel(
            game = game,
            description = description,
            accentColor = accent,
            versionCount = versionCount,
            monospaceStyle = monospaceStyle,
            extraBadgeText = extraBadgeText,
            showStarRating = showStarRating,
            uppercaseTitle = uppercaseTitle,
            modifier =
                Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
        )
    }
}

@Composable
private fun TVScreenshotSlideshow(
    game: Game?,
    accentColor: Color,
    modifier: Modifier = Modifier,
    showScanlines: Boolean = false,
) {
    val motion = LocalFrontendMotion.current
    val galleryUrls =
        remember(game?.id, game?.coverFrontUrl, game?.customCoverUri) {
            game?.let { TVGameMetadataHelper.screenshotGalleryUrlsFor(it) } ?: emptyList()
        }

    var currentIndex by remember(game?.id) { mutableIntStateOf(0) }

    LaunchedEffect(game?.id, galleryUrls.size, motion.slideshowMillis) {
        currentIndex = 0
        if (galleryUrls.size > 1 && motion.slideshowMillis > 0L) {
            while (true) {
                delay(motion.slideshowMillis)
                currentIndex = (currentIndex + 1) % galleryUrls.size
            }
        }
    }

    val safeIndex = if (galleryUrls.isNotEmpty()) currentIndex % galleryUrls.size else 0
    val activeUrl = galleryUrls.getOrNull(safeIndex)
    var mediaFailed by remember(activeUrl) { mutableStateOf(false) }
    val stageShape = RoundedCornerShape(14.dp)

    Box(
        modifier =
            modifier
                .shadow(14.dp, stageShape, ambientColor = accentColor, spotColor = accentColor)
                .clip(stageShape)
                .background(Color(0xFF07090F))
                .border(1.5.dp, accentColor.copy(alpha = 0.45f), stageShape),
        contentAlignment = Alignment.Center,
    ) {
        if (activeUrl != null && !mediaFailed) {
            Crossfade(
                targetState = activeUrl,
                animationSpec = tween(durationMillis = motion.mediaMillis),
                label = "tv_slideshow_crossfade",
                modifier = Modifier.fillMaxSize(),
            ) { url ->
                AsyncImage(
                    model =
                        ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(false)
                            .build(),
                    onError = { mediaFailed = true },
                    contentDescription = game?.displayName(),
                    contentScale = ContentScale.Fit,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                )
            }
        } else if (game != null) {
            LemuroidGameImage(
                game = game,
                contentScale = ContentScale.Fit,
                preserveAspect = true,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(12.dp),
            )
        }

        if (showScanlines) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.22f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5f,
                    )
                    y += 5f
                }
            }
        }

        if (galleryUrls.size > 1) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                galleryUrls.indices.forEach { idx ->
                    val isActive = idx == safeIndex
                    Box(
                        modifier =
                            Modifier
                                .height(6.dp)
                                .width(if (isActive) 18.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) accentColor else Color.White.copy(alpha = 0.36f),
                                ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TVGameInfoPanel(
    game: Game?,
    description: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    versionCount: Int = 1,
    monospaceStyle: Boolean = false,
    extraBadgeText: String? = null,
    showStarRating: Boolean = false,
    uppercaseTitle: Boolean = false,
) {
    if (game == null) {
        Box(modifier = modifier)
        return
    }

    val fontFamily = if (monospaceStyle) FontFamily.Monospace else FontFamily.Default
    val systemTitle = resolveSystemTitle(game.systemId)
    val rawTitle = game.displayName()
    val displayTitle = if (uppercaseTitle) rawTitle.uppercase() else rawTitle
    val fallbackText =
        remember(game.id, systemTitle, game.developer) {
            buildString {
                append(systemTitle)
                game.developer?.takeIf { it.isNotBlank() }?.let {
                    append(" • ")
                    append(it)
                }
            }
        }

    Column(
        modifier =
            modifier
                .focusProperties { canFocus = false }
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        // Metadata Pills Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (extraBadgeText != null) {
                Surface(
                    color = accentColor,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = extraBadgeText,
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Surface(
                color = accentColor.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.70f)),
            ) {
                Text(
                    text = systemTitle.uppercase(),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }

            if (game.isFavorite) {
                Surface(
                    color = Color(0xFFFFB800).copy(alpha = 0.20f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color(0xFFFFB800).copy(alpha = 0.75f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "FAVORITO",
                            color = Color(0xFFFFD56B),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            if (versionCount > 1) {
                Surface(
                    color = Color.White.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = "$versionCount VERSÕES",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }

            if (showStarRating && game.isFavorite) {
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(1) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Game Title
        Text(
            text = displayTitle,
            color = if (monospaceStyle) accentColor else Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = fontFamily,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subtle Accent Divider
        Box(
            modifier =
                Modifier
                    .width(54.dp)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accentColor),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Full Description Text
        GameDescriptionText(
                    modifier = Modifier.weight(1f),
            text = description.ifBlank { fallbackText },
            color = if (monospaceStyle) Color(0xFFB6FF9E) else Color(0xFFD5DCE8),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            lineHeight = 20.sp,
            visibleLines = 6,
        )
    }
}

// ---------------------------------------------------------------------------
// SHARED FOCUSABLE GAME CARD
// ---------------------------------------------------------------------------
@Composable
private fun TVFocusableCard(
    entry: GroupedGameEntry,
    theme: FrontendTheme,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    popUpOnFocus: Boolean = false,
) {
    val motion = LocalFrontendMotion.current
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1f, animationSpec = tween(motion.focusMillis), label = "tv_card_scale")
    val offsetY by animateFloatAsState(if (isFocused && popUpOnFocus) -8f else 0f, animationSpec = tween(motion.focusMillis), label = "tv_card_y")
    val elevation by animateDpAsState(if (isFocused) 14.dp else 2.dp, animationSpec = tween(motion.focusMillis), label = "tv_card_elev")
    val accent = themeAccentColor(theme)
    val cardShape = RoundedCornerShape(12.dp)
    val targetGame = entry.displayGame

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = offsetY
                }
                .shadow(
                    elevation = elevation,
                    shape = cardShape,
                    ambientColor = if (isFocused) accent else Color.Black,
                    spotColor = if (isFocused) accent else Color.Black,
                )
                .optionalFocusRequester(focusRequester)
                .onFocusChanged { state ->
                    val nowFocused = state.isFocused
                    if (nowFocused && !isFocused) {
                        onFocusEntry(entry)
                    }
                    isFocused = nowFocused
                }
                .tvConfirmKeyAction { onEntryClick(entry) }
                .focusable()
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) accent else Color.White.copy(alpha = 0.10f),
                    shape = cardShape,
                )
                .padding(4.dp),
    ) {
        ThemedGameGridItem(
            game = targetGame,
            theme = theme,
            onClick = { onEntryClick(entry) },
            onLongClick = { onEntryClick(entry) },
            onFavoriteToggle = null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusProperties { canFocus = false },
        )

        if (targetGame.isFavorite) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 1. NETFLIX TV LAYOUT
// ---------------------------------------------------------------------------
@Composable
private fun TVNetflixLayout(
    focusedGame: Game,
    versionCount: Int,
    description: String,
    entries: List<GroupedGameEntry>,
    recents: List<GroupedGameEntry>,
    favorites: List<GroupedGameEntry>,
    groupedSystems: List<TVSystemGroup>,
    selectedFilter: TVFilter,
    firstItemFocusRequester: FocusRequester,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val firstSystemKeyWithEntries =
        remember(selectedFilter, recents, favorites, groupedSystems, entries) {
            if (selectedFilter is TVFilter.All && recents.isEmpty() && favorites.isEmpty()) {
                groupedSystems.firstOrNull { group ->
                    entries.any { it.displayGame.systemId in group.systemIds }
                }?.key
            } else {
                null
            }
        }

    Column(modifier = Modifier.fillMaxSize()) {
        // Fixed Cinema Hero Stage with Large Screenshot Slideshow + Game Info
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(265.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF161616), Color(0xFF0E0E0E)),
                        ),
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp),
        ) {
            TVHeroMediaAndInfoPanel(
                game = focusedGame,
                description = description,
                theme = FrontendTheme.NETFLIX,
                versionCount = versionCount,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Scrollable Cinema Rows
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (selectedFilter is TVFilter.All) {
                if (recents.isNotEmpty()) {
                    item(key = "netflix_recents") {
                        TVNetflixCarouselRow(
                            title = "Continuar Jogando",
                            entries = recents,
                            firstItemFocusRequester = firstItemFocusRequester,
                            onFocusEntry = onFocusEntry,
                            onEntryClick = onEntryClick,
                        )
                    }
                }
                if (favorites.isNotEmpty()) {
                    item(key = "netflix_favorites") {
                        TVNetflixCarouselRow(
                            title = "Minha Lista",
                            entries = favorites,
                            firstItemFocusRequester = if (recents.isEmpty()) firstItemFocusRequester else null,
                            onFocusEntry = onFocusEntry,
                            onEntryClick = onEntryClick,
                        )
                    }
                }
                items(groupedSystems, key = { "netflix_sys_${it.key}" }) { group ->
                    val sysEntries =
                        remember(entries, group.key) {
                            entries.filter { it.displayGame.systemId in group.systemIds }
                        }
                    if (sysEntries.isNotEmpty()) {
                        TVNetflixCarouselRow(
                            title = if (group.isArcade) "ARCADE" else resolveSystemTitle(group.key),
                            entries = sysEntries,
                            firstItemFocusRequester = if (group.key == firstSystemKeyWithEntries) firstItemFocusRequester else null,
                            onFocusEntry = onFocusEntry,
                            onEntryClick = onEntryClick,
                        )
                    }
                }
            } else {
                item(key = "netflix_filtered") {
                    TVNetflixCarouselRow(
                        title = "Catálogo",
                        entries = entries,
                        firstItemFocusRequester = firstItemFocusRequester,
                        onFocusEntry = onFocusEntry,
                        onEntryClick = onEntryClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun TVNetflixCarouselRow(
    title: String,
    entries: List<GroupedGameEntry>,
    firstItemFocusRequester: FocusRequester?,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val localFirstFocus = firstItemFocusRequester ?: remember { FocusRequester() }
    val lastFocusRequester = remember { FocusRequester() }
    val canWrap = entries.size > 1

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.displayGame.id }) { index, entry ->
                val isFirst = index == 0
                val isLast = index == entries.lastIndex
                TVFocusableCard(
                    entry = entry,
                    theme = FrontendTheme.NETFLIX,
                    onFocusEntry = onFocusEntry,
                    onEntryClick = onEntryClick,
                    focusRequester =
                        when {
                            isFirst -> localFirstFocus
                            isLast && canWrap -> lastFocusRequester
                            else -> null
                        },
                    modifier =
                        Modifier
                            .width(152.dp)
                            .tvWrapAroundKeyAction(
                                isFirst = isFirst,
                                isLast = isLast,
                                enabled = canWrap,
                                onWrapToLast = {
                                    coroutineScope.launch {
                                        listState.scrollToItem(entries.lastIndex)
                                        delay(48)
                                        runCatching { lastFocusRequester.requestFocus() }
                                    }
                                },
                                onWrapToFirst = {
                                    coroutineScope.launch {
                                        listState.scrollToItem(0)
                                        delay(48)
                                        runCatching { localFirstFocus.requestFocus() }
                                    }
                                },
                            ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 2. LOCADORA TV LAYOUT
// ---------------------------------------------------------------------------
@Composable
private fun TVLocadoraLayout(
    focusedGame: Game,
    versionCount: Int,
    description: String,
    entries: List<GroupedGameEntry>,
    firstItemFocusRequester: FocusRequester,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val rows = remember(entries) { entries.chunked(5) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lastFocusRequester = remember { FocusRequester() }
    val canWrap = entries.size > 1

    Column(modifier = Modifier.fillMaxSize()) {
        // Warm Wood Rental Counter Showcase
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(258.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF2B2017), Color(0xFF201711), Color(0xFF17100B)),
                        ),
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp),
        ) {
            TVHeroMediaAndInfoPanel(
                game = focusedGame,
                description = description,
                theme = FrontendTheme.LOCADORA,
                versionCount = versionCount,
                extraBadgeText = "#${focusedGame.id}",
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Wooden Shelves
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(rows, key = { rowIdx, row -> "shelf_${rowIdx}_${row.first().displayGame.id}" }) { rowIdx, rowEntries ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        rowEntries.forEachIndexed { colIdx, entry ->
                            val isFirst = rowIdx == 0 && colIdx == 0
                            val isLast = rowIdx == rows.lastIndex && colIdx == rowEntries.lastIndex
                            TVFocusableCard(
                                entry = entry,
                                theme = FrontendTheme.LOCADORA,
                                onFocusEntry = onFocusEntry,
                                onEntryClick = onEntryClick,
                                focusRequester =
                                    when {
                                        isFirst -> firstItemFocusRequester
                                        isLast && canWrap -> lastFocusRequester
                                        else -> null
                                    },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .tvWrapAroundKeyAction(
                                            isFirst = isFirst,
                                            isLast = isLast,
                                            enabled = canWrap,
                                            onWrapToLast = {
                                                coroutineScope.launch {
                                                    listState.scrollToItem(rows.lastIndex)
                                                    delay(48)
                                                    runCatching { lastFocusRequester.requestFocus() }
                                                }
                                            },
                                            onWrapToFirst = {
                                                coroutineScope.launch {
                                                    listState.scrollToItem(0)
                                                    delay(48)
                                                    runCatching { firstItemFocusRequester.requestFocus() }
                                                }
                                            },
                                        ),
                                popUpOnFocus = true,
                            )
                        }
                        repeat(5 - rowEntries.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    WoodenShelfDivider(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 3. CONSOLE (PS5 / SWITCH / BATOCERA) TV LAYOUT
// ---------------------------------------------------------------------------
@Composable
private fun TVConsoleLayout(
    focusedGame: Game,
    versionCount: Int,
    description: String,
    entries: List<GroupedGameEntry>,
    firstItemFocusRequester: FocusRequester,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lastFocusRequester = remember { FocusRequester() }
    val canWrap = entries.size > 1

    Column(modifier = Modifier.fillMaxSize()) {
        // Spacious Ambient Hero Stage
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp, vertical = 18.dp),
        ) {
            TVHeroMediaAndInfoPanel(
                game = focusedGame,
                description = description,
                theme = FrontendTheme.CONSOLE,
                versionCount = versionCount,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Bottom Console Dock
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF00C2FF).copy(alpha = 0.25f)),
            )
            LazyRow(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070C17).copy(alpha = 0.90f))
                        .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> e.displayGame.id }) { index, entry ->
                    val isFirst = index == 0
                    val isLast = index == entries.lastIndex
                    TVFocusableCard(
                        entry = entry,
                        theme = FrontendTheme.CONSOLE,
                        onFocusEntry = onFocusEntry,
                        onEntryClick = onEntryClick,
                        focusRequester =
                            when {
                                isFirst -> firstItemFocusRequester
                                isLast && canWrap -> lastFocusRequester
                                else -> null
                            },
                        modifier =
                            Modifier
                                .width(142.dp)
                                .tvWrapAroundKeyAction(
                                    isFirst = isFirst,
                                    isLast = isLast,
                                    enabled = canWrap,
                                    onWrapToLast = {
                                        coroutineScope.launch {
                                            listState.scrollToItem(entries.lastIndex)
                                            delay(48)
                                            runCatching { lastFocusRequester.requestFocus() }
                                        }
                                    },
                                    onWrapToFirst = {
                                        coroutineScope.launch {
                                            listState.scrollToItem(0)
                                            delay(48)
                                            runCatching { firstItemFocusRequester.requestFocus() }
                                        }
                                    },
                                ),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 4. TV (CRT CHANNEL GUIDE) LAYOUT
// ---------------------------------------------------------------------------
@Composable
private fun TVCrtGuideLayout(
    focusedGame: Game,
    versionCount: Int,
    description: String,
    entries: List<GroupedGameEntry>,
    firstItemFocusRequester: FocusRequester,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val phosphorGreen = Color(0xFF9ADF8D)
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val lastFocusRequester = remember { FocusRequester() }
    val canWrap = entries.size > 1
    val activeChannelIndex =
        remember(entries, focusedGame.id) {
            entries.indexOfFirst { it.displayGame.id == focusedGame.id }.coerceAtLeast(0)
        }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Left Column: CRT Channel Guide
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .border(2.dp, phosphorGreen.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A160C).copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.displayGame.id }) { index, entry ->
                val game = entry.displayGame
                val isFirst = index == 0
                val isLast = index == entries.lastIndex
                var isFocused by remember { mutableStateOf(false) }
                val channelNum = (index + 1).toString().padStart(2, '0')
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isFocused) phosphorGreen else Color(0xFF122314))
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) Color.White else phosphorGreen.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .optionalFocusRequester(
                                when {
                                    isFirst -> firstItemFocusRequester
                                    isLast && canWrap -> lastFocusRequester
                                    else -> null
                                },
                            )
                            .onFocusChanged { state ->
                                val nowFocused = state.isFocused
                                if (nowFocused && !isFocused) {
                                    onFocusEntry(entry)
                                }
                                isFocused = nowFocused
                            }
                            .tvWrapAroundKeyAction(
                                isFirst = isFirst,
                                isLast = isLast,
                                enabled = canWrap,
                                vertical = true,
                                onWrapToLast = {
                                    coroutineScope.launch {
                                        listState.scrollToItem(entries.lastIndex)
                                        delay(48)
                                        runCatching { lastFocusRequester.requestFocus() }
                                    }
                                },
                                onWrapToFirst = {
                                    coroutineScope.launch {
                                        listState.scrollToItem(0)
                                        delay(48)
                                        runCatching { firstItemFocusRequester.requestFocus() }
                                    }
                                },
                            )
                            .tvConfirmKeyAction { onEntryClick(entry) }
                            .focusable()
                            .clickable { onEntryClick(entry) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "CH $channelNum",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = if (isFocused) Color(0xFF041A00) else Color(0xFFFFB000),
                    )
                    Text(
                        text = game.displayName(),
                        color = if (isFocused) Color(0xFF041A00) else Color(0xFFD8F5DA),
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (game.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isFocused) Color(0xFF041A00) else Color(0xFFFFB000),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Right Side: CRT Monitor Frame with Large Slideshow + Monospace Info
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .shadow(16.dp, RoundedCornerShape(22.dp))
                    .border(3.dp, Color(0xFF2D4730), RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF040A05))
                    .padding(20.dp),
        ) {
            TVHeroMediaAndInfoPanel(
                game = focusedGame,
                description = description,
                theme = FrontendTheme.TV,
                versionCount = versionCount,
                monospaceStyle = true,
                extraBadgeText = "CH ${(activeChannelIndex + 1).toString().padStart(2, '0')}",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 5. RETRO 80/90 (SYNTHWAVE) TV LAYOUT
// ---------------------------------------------------------------------------
@Composable
private fun TVRetro8090Layout(
    focusedGame: Game,
    versionCount: Int,
    description: String,
    entries: List<GroupedGameEntry>,
    firstItemFocusRequester: FocusRequester,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val lastFocusRequester = remember { FocusRequester() }
    val canWrap = entries.size > 1

    Column(modifier = Modifier.fillMaxSize()) {
        // Neon Synthwave Showcase
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(258.dp)
                    .padding(horizontal = 32.dp, vertical = 12.dp)
                    .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = Color(0xFFFF75B5), spotColor = Color(0xFF00F0FF))
                    .border(2.dp, Brush.horizontalGradient(listOf(Color(0xFFFF75B5), Color(0xFF00F0FF))), RoundedCornerShape(16.dp))
                    .background(Color(0xFF190833).copy(alpha = 0.90f), RoundedCornerShape(16.dp))
                    .padding(14.dp),
        ) {
            TVHeroMediaAndInfoPanel(
                game = focusedGame,
                description = description,
                theme = FrontendTheme.RETRO_80_90,
                versionCount = versionCount,
                modifier = Modifier.fillMaxSize(),
            )
        }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 152.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.displayGame.id }) { index, entry ->
                val isFirst = index == 0
                val isLast = index == entries.lastIndex
                TVFocusableCard(
                    entry = entry,
                    theme = FrontendTheme.RETRO_80_90,
                    onFocusEntry = onFocusEntry,
                    onEntryClick = onEntryClick,
                    focusRequester =
                        when {
                            isFirst -> firstItemFocusRequester
                            isLast && canWrap -> lastFocusRequester
                            else -> null
                        },
                    modifier =
                        Modifier.tvWrapAroundKeyAction(
                            isFirst = isFirst,
                            isLast = isLast,
                            enabled = canWrap,
                            onWrapToLast = {
                                coroutineScope.launch {
                                    gridState.scrollToItem(entries.lastIndex)
                                    delay(48)
                                    runCatching { lastFocusRequester.requestFocus() }
                                }
                            },
                            onWrapToFirst = {
                                coroutineScope.launch {
                                    gridState.scrollToItem(0)
                                    delay(48)
                                    runCatching { firstItemFocusRequester.requestFocus() }
                                }
                            },
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 7. THRASH METAL (STEEL & CRIMSON #C41212) TV LAYOUT
// ---------------------------------------------------------------------------
@Composable
private fun TVThrashMetalLayout(
    focusedGame: Game,
    versionCount: Int,
    description: String,
    entries: List<GroupedGameEntry>,
    firstItemFocusRequester: FocusRequester,
    onFocusEntry: (GroupedGameEntry) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val crimson = Color(0xFFD84D5B)
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val lastFocusRequester = remember { FocusRequester() }
    val canWrap = entries.size > 1

    Column(modifier = Modifier.fillMaxSize()) {
        // Steel & Crimson Stage Showcase
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(258.dp)
                    .padding(horizontal = 32.dp, vertical = 12.dp)
                    .shadow(14.dp, RoundedCornerShape(8.dp), ambientColor = crimson, spotColor = crimson)
                    .border(2.dp, crimson, RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1C21).copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                    .padding(14.dp),
        ) {
            TVHeroMediaAndInfoPanel(
                game = focusedGame,
                description = description,
                theme = FrontendTheme.THRASH_METAL,
                versionCount = versionCount,
                uppercaseTitle = true,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Riveted Grid
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 152.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.displayGame.id }) { index, entry ->
                val isFirst = index == 0
                val isLast = index == entries.lastIndex
                TVFocusableCard(
                    entry = entry,
                    theme = FrontendTheme.THRASH_METAL,
                    onFocusEntry = onFocusEntry,
                    onEntryClick = onEntryClick,
                    focusRequester =
                        when {
                            isFirst -> firstItemFocusRequester
                            isLast && canWrap -> lastFocusRequester
                            else -> null
                        },
                    modifier =
                        Modifier.tvWrapAroundKeyAction(
                            isFirst = isFirst,
                            isLast = isLast,
                            enabled = canWrap,
                            onWrapToLast = {
                                coroutineScope.launch {
                                    gridState.scrollToItem(entries.lastIndex)
                                    delay(48)
                                    runCatching { lastFocusRequester.requestFocus() }
                                }
                            },
                            onWrapToFirst = {
                                coroutineScope.launch {
                                    gridState.scrollToItem(0)
                                    delay(48)
                                    runCatching { firstItemFocusRequester.requestFocus() }
                                }
                            },
                        ),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 8. 3D HARDWARE SHOWCASE STAGE TV LAYOUT
// ---------------------------------------------------------------------------
@Composable
private fun TVThreeDShowcaseLayout(
    focusedGame: Game,
    versionCount: Int,
    description: String,
    entries: List<GroupedGameEntry>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onEntryClick: (GroupedGameEntry) -> Unit,
) {
    val motion = LocalFrontendMotion.current
    val cyan = Color(0xFF00E5FF)
    val listState = rememberLazyListState()
    val safeIndex = if (entries.isEmpty()) 0 else selectedIndex.coerceIn(0, entries.lastIndex)

    LaunchedEffect(safeIndex, entries.size) {
        if (entries.isNotEmpty() && safeIndex in entries.indices) {
            if (motion.animatedScroll) listState.animateScrollToItem(safeIndex) else listState.scrollToItem(safeIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Showcase Stage:
        // Left = ONE spinning OpenGL Game3DObject on lit pedestal
        // Middle = Large Multi-Screenshot Slideshow
        // Right = Game Info & Description
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Dedicated 3D Pedestal (260.dp square stage)
            Box(
                modifier =
                    Modifier
                        .size(264.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF1A315A).copy(alpha = 0.85f), Color(0xFF091020).copy(alpha = 0.94f)),
                            ),
                        )
                        .border(1.5.dp, cyan.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                        .padding(12.dp)
                        .focusProperties { canFocus = false },
                contentAlignment = Alignment.Center,
            ) {
                // Lit Pedestal Glow Ring
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .size(width = 205.dp, height = 36.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(cyan.copy(alpha = 0.60f), cyan.copy(alpha = 0.14f), Color.Transparent),
                                ),
                                shape = CircleShape,
                            ),
                )
                // The ONLY spinning OpenGL 3D hardware instance on screen
                Game3DObject(
                    game = focusedGame,
                    onClick = {},
                    onLongClick = {},
                    onFavoriteToggle = null,
                    isSelected = true,
                    modifier =
                        Modifier
                            .width(205.dp)
                            .focusProperties { canFocus = false },
                )
            }

            // Middle: Large Multi-Screenshot Slideshow
            TVScreenshotSlideshow(
                game = focusedGame,
                accentColor = cyan,
                modifier =
                    Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
            )

            // Right: Game Info & Description
            TVGameInfoPanel(
                game = focusedGame,
                description = description,
                accentColor = cyan,
                versionCount = versionCount,
                modifier =
                    Modifier
                        .weight(0.46f)
                        .fillMaxHeight(),
            )
        }

        // Bottom 3D Selection Strip (ALL cards use isSelected = false -> zero extra GLSurfaceViews!)
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(cyan.copy(alpha = 0.25f)),
            )
            LazyRow(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF060A13).copy(alpha = 0.92f))
                        .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                itemsIndexed(entries, key = { _, e -> e.displayGame.id }) { index, entry ->
                    val targetGame = entry.displayGame
                    val isFocused = index == safeIndex
                    val scale = animateFloatAsState(if (isFocused) 1.04f else 1f, animationSpec = tween(motion.focusMillis), label = "3d_strip_scale")
                    val elevation = if (isFocused) 14.dp else 2.dp
                    val itemShape = RoundedCornerShape(14.dp)

                    Box(
                        modifier =
                            Modifier
                                .width(136.dp)
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                }
                                .shadow(
                                    elevation = elevation,
                                    shape = itemShape,
                                    ambientColor = if (isFocused) cyan else Color.Black,
                                    spotColor = if (isFocused) cyan else Color.Black,
                                )
                                .focusProperties { canFocus = false }
                                .clickable {
                                    if (isFocused) {
                                        onEntryClick(entry)
                                    } else {
                                        onSelectIndex(index)
                                    }
                                }
                                .border(
                                    width = if (isFocused) 2.5.dp else 1.dp,
                                    color = if (isFocused) cyan else Color.White.copy(alpha = 0.10f),
                                    shape = itemShape,
                                )
                                .padding(2.dp),
                    ) {
                        Game3DObject(
                            game = targetGame,
                            onClick = { onEntryClick(entry) },
                            onLongClick = { onEntryClick(entry) },
                            onFavoriteToggle = null,
                            isSelected = false,
                            modifier = Modifier.focusProperties { canFocus = false },
                        )

                        if (targetGame.isFavorite) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .padding(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TV GAMEPAD-READY DIALOGS
// ---------------------------------------------------------------------------
@Composable
private fun TVGroupedGameSelectionDialog(
    group: GroupedGameEntry.MultiDisc,
    theme: FrontendTheme,
    accentColor: Color,
    onSelect: (Game) -> Unit,
    onConfigure: (Game) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val firstDiscFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstDiscFocus.requestFocus() }
    }

    AlertDialog(
        modifier =
            Modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE
                ) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    true
                } else {
                    false
                }
            },
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141821),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = group.folderName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Selecione o disco ou versão • [X] Núcleo e desempenho • [B] Voltar",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(group.discs, key = { _, disc -> disc.id }) { index, disc ->
                    TVDialogOptionRow(
                        label = group.itemLabel(disc),
                        icon = Icons.Default.PlayArrow,
                        accentColor = accentColor,
                        onFocused = { FrontendSoundEngine.playNavigate(context, theme) },
                        onClick = { onSelect(disc) },
                        modifier = Modifier.optionalFocusRequester(if (index == 0) firstDiscFocus else null)
                            .onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_X) {
                                    if (event.type == KeyEventType.KeyUp) onConfigure(disc)
                                    true
                                } else false
                            },
                    )
                }
            }
        },
        confirmButton = {
            TVDialogOptionRow(
                label = stringResource(android.R.string.cancel),
                icon = null,
                accentColor = accentColor,
                onFocused = { FrontendSoundEngine.playNavigate(context, theme) },
                onClick = onDismiss,
                fillWidth = false,
            )
        },
    )
}

@Composable
private fun TVGameActionDialog(
    game: Game,
    theme: FrontendTheme,
    accentColor: Color,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onConfigure: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val firstActionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstActionFocus.requestFocus() }
    }

    AlertDialog(
        modifier =
            Modifier.onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE
                ) {
                    if (keyEvent.type == KeyEventType.KeyUp) {
                        onDismiss()
                    }
                    true
                } else {
                    false
                }
            },
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141821),
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Text(
                text = game.displayName(),
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = resolveSystemTitle(game.systemId),
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
                TVDialogOptionRow(
                    label = "Jogar / Continuar",
                    icon = Icons.Default.PlayArrow,
                    accentColor = accentColor,
                    onFocused = { FrontendSoundEngine.playNavigate(context, theme) },
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(firstActionFocus),
                )
                TVDialogOptionRow(
                    label = "Núcleo e desempenho deste jogo",
                    icon = null,
                    accentColor = accentColor,
                    onFocused = { FrontendSoundEngine.playNavigate(context, theme) },
                    onClick = onConfigure,
                )
                TVDialogOptionRow(
                    label = "Reiniciar do Zero",
                    icon = Icons.Default.Refresh,
                    accentColor = accentColor,
                    onFocused = { FrontendSoundEngine.playNavigate(context, theme) },
                    onClick = onRestart,
                )
                TVDialogOptionRow(
                    label = if (game.isFavorite) "Remover dos Favoritos" else "Adicionar aos Favoritos",
                    icon = if (game.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    accentColor = accentColor,
                    onFocused = { FrontendSoundEngine.playNavigate(context, theme) },
                    onClick = onToggleFavorite,
                )
            }
        },
        confirmButton = {
            TVDialogOptionRow(
                label = stringResource(android.R.string.cancel),
                icon = null,
                accentColor = accentColor,
                onFocused = { FrontendSoundEngine.playNavigate(context, theme) },
                onClick = onDismiss,
                fillWidth = false,
            )
        },
    )
}

@Composable
private fun TVDialogOptionRow(
    label: String,
    icon: ImageVector?,
    accentColor: Color,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) accentColor else Color.White.copy(alpha = 0.08f),
        label = "dialog_opt_bg",
    )
    val contentColor = if (isFocused) Color.Black else Color.White

    Surface(
        modifier =
            modifier
                .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                .onFocusChanged { state ->
                    if (state.isFocused && !isFocused) {
                        onFocused()
                    }
                    isFocused = state.isFocused
                }
                .tvConfirmKeyAction(onConfirm = onClick)
                .focusable()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        border = if (isFocused) BorderStroke(2.dp, Color.White) else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun resolveSystemShortName(systemId: String): String {
    if (systemId == ARCADE_GROUP_KEY || isArcadeSystemId(systemId)) return "ARCADE"
    val system = remember(systemId) { runCatching { GameSystem.findById(systemId) }.getOrNull() }
    return if (system != null) stringResource(system.shortTitleResId) else systemId.uppercase()
}

@Composable
private fun resolveSystemTitle(systemId: String): String {
    if (systemId == ARCADE_GROUP_KEY || isArcadeSystemId(systemId)) return "ARCADE"
    val system = remember(systemId) { runCatching { GameSystem.findById(systemId) }.getOrNull() }
    return if (system != null) stringResource(system.titleResId) else systemId.uppercase()
}

private fun resolveConsoleCompany(systemKey: String): String =
    when (systemKey.lowercase()) {
        "arcade", "fbneo", "mame2003plus" -> "ARCADE"
        "psx", "psp" -> "SONY"
        "snes", "n64", "nes", "gba", "gb", "gbc", "nds", "3ds" -> "NINTENDO"
        "md", "genesis", "sms", "segacd", "gg" -> "SEGA"
        "atari2600", "atari7800", "lynx" -> "ATARI"
        "pce" -> "NEC"
        "ngp", "ngc" -> "SNK"
        "ws", "wsc" -> "BANDAI"
        "dos" -> "PC / DOS"
        else -> "RETRO"
    }
