package com.swordfish.lemuroid.app.shared.hub

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadButtonIcon
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintBar
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GeneratedThemeBackdrop
import com.swordfish.lemuroid.app.shared.ui.FrontendSoundEngine
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class MultiplayerTab(val label: String, val icon: ImageVector) {
    ROOMS("Salas", Icons.Default.Groups),
    CREATE("Criar Sala", Icons.Default.AddCircle),
    SERVER_SETTINGS("Servidores", Icons.Default.Dns),
}

@Composable
fun MultiplayerHubScreen(
    activeTheme: FrontendTheme,
    localGames: List<Game>,
    onLaunchGame: (Game) -> Unit,
    onBackToHub: () -> Unit,
    isTvMode: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visuals = remember(activeTheme) { HubConfigManager.visualsFor(activeTheme) }

    var activeTab by remember { mutableStateOf(MultiplayerTab.ROOMS) }
    var activeServer by remember { mutableStateOf(HubConfigManager.getSelectedMultiplayerServer(context)) }
    var rooms by remember { mutableStateOf<List<MultiplayerRoom>>(emptyList()) }
    var matchingRoomIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(localGames, rooms) {
        matchingRoomIds =
            withContext(Dispatchers.Default) {
                MultiplayerRomSyncManager.matchingRoomIds(localGames, rooms)
            }
    }
    var isLoadingRooms by remember { mutableStateOf(false) }
    var roomError by remember { mutableStateOf<String?>(null) }
    var selectedRowIndex by remember { mutableIntStateOf(0) }

    var pendingDownloadRoom by remember { mutableStateOf<MultiplayerRoom?>(null) }
    var pendingCatalogMatch by remember { mutableStateOf<CatalogRomMatch?>(null) }
    var isSearchingCatalog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<RomDownloadProgress?>(null) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(statusBanner) {
        if (statusBanner != null) {
            kotlinx.coroutines.delay(3500L)
            statusBanner = null
        }
    }

    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var roomsJob by remember { mutableStateOf<Job?>(null) }
    var catalogJob by remember { mutableStateOf<Job?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    roomsJob?.cancel()
                    catalogJob?.cancel()
                    downloadJob?.cancel()
                    isLoadingRooms = false
                    isSearchingCatalog = false
                    downloadProgress = null
                    pendingDownloadRoom = null
                    pendingCatalogMatch = null
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            roomsJob?.cancel()
            catalogJob?.cancel()
            downloadJob?.cancel()
        }
    }

    fun refreshRooms() {
        roomsJob?.cancel()
        val requestedServer = activeServer
        roomsJob =
            scope.launch {
                isLoadingRooms = true
                roomError = null
                val res = MultiplayerRomSyncManager.fetchRooms(requestedServer)
                res.onSuccess { list ->
                    rooms = list
                    selectedRowIndex = 0
                }.onFailure { err ->
                    roomError = err.message ?: "Não foi possível carregar as salas."
                    rooms = emptyList()
                }
                isLoadingRooms = false
            }
    }

    LaunchedEffect(activeServer, activeTab) {
        if (activeTab == MultiplayerTab.ROOMS) {
            refreshRooms()
        } else {
            roomsJob?.cancel()
            isLoadingRooms = false
        }
        selectedRowIndex = 0
        listState.scrollToItem(0)
        runCatching { focusRequester.requestFocus() }
    }

    val currentItemCount =
        when (activeTab) {
            MultiplayerTab.ROOMS -> rooms.size
            MultiplayerTab.CREATE -> localGames.size
            MultiplayerTab.SERVER_SETTINGS -> MultiplayerServerPreset.entries.size
        }

    LaunchedEffect(selectedRowIndex, activeTab, currentItemCount) {
        if (currentItemCount <= 0) return@LaunchedEffect
        val target = selectedRowIndex.coerceIn(0, currentItemCount - 1)
        val layoutInfo = listState.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            listState.scrollToItem(target)
            return@LaunchedEffect
        }
        val viewportStart = layoutInfo.viewportStartOffset
        val viewportEnd = layoutInfo.viewportEndOffset
        val targetItem = visible.firstOrNull { it.index == target }
        val fullyVisible =
            targetItem != null &&
                targetItem.offset >= viewportStart &&
                targetItem.offset + targetItem.size <= viewportEnd
        if (!fullyVisible) {
            val firstVisibleIdx = visible.first().index
            if (target < firstVisibleIdx) {
                listState.scrollToItem(target)
            } else {
                val visibleCount = (visible.size - 1).coerceAtLeast(1)
                listState.scrollToItem((target - visibleCount + 1).coerceAtLeast(0))
            }
        }
    }

    fun handleSelectRoom(room: MultiplayerRoom) {
        if (room.hasPassword || room.hasSpectatePassword) {
            statusBanner = "A sala de ${room.hostName} é protegida por senha. Escolha uma sala aberta."
            return
        }
        val localMatch = MultiplayerRomSyncManager.findMatchingLocalGame(localGames, room)
        if (localMatch != null) {
            com.swordfish.lemuroid.app.shared.game.GameLauncher.pendingMultiplayerSession =
                MultiplayerSessionConfig(
                    isHost = false,
                    hostIp = room.ip,
                    port = room.port,
                    mitmIp = room.mitmIp,
                    mitmPort = room.mitmPort,
                    mitmSession = room.mitmSession,
                    hostMethod = room.hostMethod,
                    hostFrontend = room.frontend,
                    mitmHandle = activeServer.mitmHandle,
                    relayHost = activeServer.relayHost,
                    relayPort = activeServer.relayPort,
                    hostDisplayName = if (isTvMode) "Smart TV" else "Celular",
                    gameTitle = room.gameName,
                    romFileName = room.romVersionName,
                    gameCrcHex = room.gameCrc,
                    consoleId = room.consoleId,
                    coreName = room.coreName,
                    coreVersion = room.coreVersion,
                )
            statusBanner = "Conectando à sala de ${room.hostName}..."
            onLaunchGame(localMatch)
            return
        }
        pendingDownloadRoom = room
        pendingCatalogMatch = null
        isSearchingCatalog = true
        catalogJob?.cancel()
        catalogJob =
            scope.launch {
                val match = MultiplayerRomSyncManager.findCatalogMatch(context, room)
                if (pendingDownloadRoom?.id == room.id) {
                    pendingCatalogMatch = match
                    isSearchingCatalog = false
                }
            }
    }

    fun handleCreateRoom(game: Game) {
        com.swordfish.lemuroid.app.shared.game.GameLauncher.pendingMultiplayerSession =
            MultiplayerSessionConfig(
                isHost = true,
                mitmHandle = activeServer.mitmHandle,
                relayHost = if (activeServer.isLan) "" else activeServer.relayHost,
                relayPort = activeServer.relayPort,
                hostDisplayName = if (isTvMode) "Smart TV" else "Celular",
                gameTitle = game.title,
                romFileName = game.fileName,
                consoleId = game.systemId,
                coreName = game.systemId.uppercase(Locale.ROOT),
            )
        statusBanner =
            if (activeServer.isLan) {
                "Criando sala LAN de ${game.title} (Host P1)..."
            } else {
                "Criando sala pública (${activeServer.mitmHandle}) de ${game.title}..."
            }
        onLaunchGame(game)
    }

    fun startRomVersionDownload(match: CatalogRomMatch) {
        if (downloadJob?.isActive == true) return
        pendingDownloadRoom = null
        pendingCatalogMatch = null
        downloadJob =
            scope.launch {
                downloadProgress = RomDownloadProgress(match.filename, 0L, 0L, "Preparando download...")
                val result =
                    MultiplayerRomSyncManager.downloadRomToGlobalFolder(context, match) { progress ->
                        downloadProgress = progress
                    }
                result.onSuccess { savedFile ->
                    statusBanner =
                        "Versão '$savedFile' salva na pasta global (${HubConfigManager.mapConsoleToFolderName(
                            match.consoleId,
                        )})!"
                    downloadProgress = null
                }.onFailure { err ->
                    statusBanner = "Falha ao baixar ROM: ${err.message}"
                    downloadProgress = null
                }
            }
    }

    fun activateServer(preset: MultiplayerServerPreset) {
        HubConfigManager.setSelectedMultiplayerServer(context, preset)
        activeServer = preset
        statusBanner = "Servidor ativo: ${preset.displayName}"
    }

    fun restoreDefaultServer() {
        HubConfigManager.resetMultiplayerDefaults(context)
        activeServer = MultiplayerServerPreset.DEFAULT
        statusBanner = "Configurações restauradas para o servidor padrão."
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    val code = ev.nativeKeyEvent.keyCode
                    val releaseAction =
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_BUTTON_A,
                            KeyEvent.KEYCODE_BUTTON_B,
                            KeyEvent.KEYCODE_BACK,
                            KeyEvent.KEYCODE_BUTTON_Y,
                            -> true
                            else -> false
                        }
                    if (releaseAction) {
                        // Keep both halves of the key here; changing screen on DOWN leaks UP to its parent.
                        if (ev.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        if (ev.type != KeyEventType.KeyUp || ev.nativeKeyEvent.isCanceled) return@onPreviewKeyEvent true
                    } else if (ev.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }

                    if (pendingDownloadRoom != null) {
                        when (code) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_BUTTON_A,
                            -> {
                                val m = pendingCatalogMatch
                                if (!isSearchingCatalog) {
                                    if (m != null) startRomVersionDownload(m) else pendingDownloadRoom = null
                                }
                                true
                            }
                            KeyEvent.KEYCODE_BACK,
                            KeyEvent.KEYCODE_BUTTON_B,
                            -> {
                                catalogJob?.cancel()
                                pendingDownloadRoom = null
                                pendingCatalogMatch = null
                                true
                            }
                            else -> false
                        }
                    } else {
                        when (code) {
                            KeyEvent.KEYCODE_BUTTON_L1,
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            -> {
                                val tabs = MultiplayerTab.entries
                                activeTab = tabs[(tabs.indexOf(activeTab) - 1 + tabs.size) % tabs.size]
                                FrontendSoundEngine.playNavigate(context, activeTheme)
                                true
                            }
                            KeyEvent.KEYCODE_BUTTON_R1,
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            -> {
                                val tabs = MultiplayerTab.entries
                                activeTab = tabs[(tabs.indexOf(activeTab) + 1) % tabs.size]
                                FrontendSoundEngine.playNavigate(context, activeTheme)
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                if (currentItemCount > 0) {
                                    selectedRowIndex = (selectedRowIndex - 1 + currentItemCount) % currentItemCount
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (currentItemCount > 0) {
                                    selectedRowIndex = (selectedRowIndex + 1) % currentItemCount
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_BUTTON_A,
                            -> {
                                when (activeTab) {
                                    MultiplayerTab.ROOMS ->
                                        rooms.getOrNull(
                                            selectedRowIndex,
                                        )?.let { handleSelectRoom(it) }
                                    MultiplayerTab.CREATE ->
                                        localGames.getOrNull(selectedRowIndex)?.let { g ->
                                            handleCreateRoom(g)
                                        }
                                    MultiplayerTab.SERVER_SETTINGS -> {
                                        val preset =
                                            MultiplayerServerPreset.entries[
                                                selectedRowIndex.coerceIn(0, MultiplayerServerPreset.entries.lastIndex),
                                            ]
                                        activateServer(preset)
                                    }
                                }
                                FrontendSoundEngine.playConfirm(context, activeTheme)
                                true
                            }
                            KeyEvent.KEYCODE_BUTTON_Y -> {
                                if (activeTab == MultiplayerTab.SERVER_SETTINGS) {
                                    restoreDefaultServer()
                                } else if (activeTab == MultiplayerTab.ROOMS) {
                                    refreshRooms()
                                }
                                true
                            }
                            KeyEvent.KEYCODE_BACK,
                            KeyEvent.KEYCODE_BUTTON_B,
                            -> {
                                onBackToHub()
                                true
                            }
                            else -> false
                        }
                    }
                },
    ) {
        val scrimBrush =
            remember(visuals) {
                Brush.verticalGradient(listOf(visuals.scrimTop, Color(0xC2090C14), visuals.scrimBottom))
            }
        GeneratedThemeBackdrop(theme = activeTheme, modifier = Modifier.fillMaxSize())
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(scrimBrush),
        )

        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .navigationBarsPadding(),
        ) {
            // Auto-detect TV/Console mode vs Mobile portrait mode (same rule as Jogar & Locadora!)
            val useTvConsoleMode = isTvMode || maxWidth >= maxHeight

            if (useTvConsoleMode) {
                MultiplayerTvConsoleLayout(
                    visuals = visuals,
                    activeTab = activeTab,
                    activeServer = activeServer,
                    rooms = rooms,
                    matchingRoomIds = matchingRoomIds,
                    localGames = localGames,
                    isLoadingRooms = isLoadingRooms,
                    roomError = roomError,
                    selectedRowIndex = selectedRowIndex,
                    listState = listState,
                    statusBanner = statusBanner,
                    downloadProgress = downloadProgress,
                    onChangeTab = { activeTab = it },
                    onSelectRow = { selectedRowIndex = it },
                    onConfirmRoom = { handleSelectRoom(it) },
                    onConfirmCreate = { g -> handleCreateRoom(g) },
                    onActivateServer = { activateServer(it) },
                    onRestoreDefaults = { restoreDefaultServer() },
                    onRefreshRooms = { refreshRooms() },
                    onBack = onBackToHub,
                )
            } else {
                MultiplayerMobileTouchLayout(
                    visuals = visuals,
                    activeTab = activeTab,
                    activeServer = activeServer,
                    rooms = rooms,
                    matchingRoomIds = matchingRoomIds,
                    localGames = localGames,
                    isLoadingRooms = isLoadingRooms,
                    roomError = roomError,
                    listState = listState,
                    statusBanner = statusBanner,
                    downloadProgress = downloadProgress,
                    onBack = onBackToHub,
                    onChangeTab = { activeTab = it },
                    onConfirmRoom = { handleSelectRoom(it) },
                    onConfirmCreate = { g -> handleCreateRoom(g) },
                    onActivateServer = { activateServer(it) },
                    onRestoreDefaults = { restoreDefaultServer() },
                    onRefreshRooms = { refreshRooms() },
                )
            }
        }

        // FLOATING AUTO-DISMISS NOTIFICATION & DOWNLOAD PROGRESS (Zero layout deformation!)
        if (!statusBanner.isNullOrBlank() || downloadProgress != null) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .displayCutoutPadding()
                        .padding(top = 14.dp, start = 20.dp, end = 20.dp)
                        .fillMaxWidth(if (isTvMode) 0.58f else 0.92f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MultiplayerStatusAndProgress(statusBanner, downloadProgress, visuals)
            }
        }

        // VERSION MISMATCH / AUTO-DOWNLOAD CONFIRMATION MODAL (works in both TV & Mobile)
        pendingDownloadRoom?.let { room ->
            MultiplayerVersionConfirmModal(
                room = room,
                visuals = visuals,
                isSearchingCatalog = isSearchingCatalog,
                catalogMatch = pendingCatalogMatch,
                onConfirmDownload = { match -> startRomVersionDownload(match) },
                onDismiss = {
                    catalogJob?.cancel()
                    pendingDownloadRoom = null
                    pendingCatalogMatch = null
                },
            )
        }
    }
}

// =================================================================================================
// 1. MODO TV / CONSOLE (10-FOOT WIDESCREEN SPLIT-PANEL DASHBOARD)
// =================================================================================================

@Composable
private fun MultiplayerTvConsoleLayout(
    visuals: HubThemeVisuals,
    activeTab: MultiplayerTab,
    activeServer: MultiplayerServerPreset,
    rooms: List<MultiplayerRoom>,
    matchingRoomIds: Set<String>,
    localGames: List<Game>,
    isLoadingRooms: Boolean,
    roomError: String?,
    selectedRowIndex: Int,
    listState: LazyListState,
    statusBanner: String?,
    downloadProgress: RomDownloadProgress?,
    onChangeTab: (MultiplayerTab) -> Unit,
    onSelectRow: (Int) -> Unit,
    onConfirmRoom: (MultiplayerRoom) -> Unit,
    onConfirmCreate: (Game) -> Unit,
    onActivateServer: (MultiplayerServerPreset) -> Unit,
    onRestoreDefaults: () -> Unit,
    onRefreshRooms: () -> Unit,
    onBack: () -> Unit,
) {
    val inspectorScroll = rememberScrollState()
    LaunchedEffect(activeTab, selectedRowIndex) { inspectorScroll.scrollTo(0) }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HubDashboardHeader(
            visuals = visuals,
            title = "Multiplayer",
            subtitle = "Salas online, rede local (LAN) e partidas em dupla",
            activeModule = HubModule.MULTIPLAYER,
            onBack = onBack,
            compact = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GamepadButtonIcon(glyph = GamepadGlyph.L1, size = 18.dp)
                MultiplayerTab.entries.forEach { tab ->
                    val selected = tab == activeTab
                    Surface(
                        color = if (selected) visuals.accent else Color(0xBB111827),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, if (selected) Color.White else visuals.accent.copy(alpha = 0.45f)),
                        modifier = Modifier.clickable { onChangeTab(tab) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (selected) Color(0xFF0A0D12) else Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = tab.label,
                                color = if (selected) Color(0xFF0A0D12) else Color.White,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
                GamepadButtonIcon(glyph = GamepadGlyph.R1, size = 18.dp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    activeServer.displayName,
                    color = visuals.accent,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (activeTab == MultiplayerTab.ROOMS || activeTab == MultiplayerTab.SERVER_SETTINGS) {
                    Surface(
                        color = Color(0xCC111827),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, visuals.secondaryAccent.copy(alpha = 0.65f)),
                        modifier =
                            Modifier.clickable {
                                if (activeTab == MultiplayerTab.SERVER_SETTINGS) {
                                    onRestoreDefaults()
                                } else {
                                    onRefreshRooms()
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            GamepadButtonIcon(glyph = GamepadGlyph.Y, size = 16.dp)
                            Text(
                                text = if (activeTab == MultiplayerTab.SERVER_SETTINGS) "Padrão" else "Atualizar",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // 2-COLUMN CONSOLE SPLIT-SCREEN (Left 56% List | Right 44% Inspector Card)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // LEFT COLUMN: Scrollable Console List
            Box(
                modifier =
                    Modifier
                        .weight(0.56f)
                        .fillMaxHeight(),
            ) {
                when (activeTab) {
                    MultiplayerTab.ROOMS -> {
                        if (isLoadingRooms) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = visuals.accent)
                            }
                        } else if (rooms.isEmpty()) {
                            EmptyRoomsNotice(roomError, activeServer, visuals, onRefreshRooms)
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(rooms, key = { _, r -> r.id }) { idx, room ->
                                    val isSel = idx == selectedRowIndex
                                    val hasExact = room.id in matchingRoomIds
                                    ConsoleListRow(
                                        title = room.gameName,
                                        subtitle = "Host: ${room.hostName} • ${room.coreName}",
                                        badge =
                                            when {
                                                room.isLan -> "REDE LOCAL"
                                                room.hasPassword -> "SENHA"
                                                hasExact -> "PRONTO"
                                                else -> "BAIXAR"
                                            },
                                        badgePositive = !room.hasPassword && (room.isLan || hasExact),
                                        isSelected = isSel,
                                        visuals = visuals,
                                        onClick = {
                                            if (isSel) onConfirmRoom(room) else onSelectRow(idx)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    MultiplayerTab.CREATE -> {
                        if (localGames.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "Nenhum jogo local encontrado. Baixe jogos na Locadora.",
                                    color = Color(0xFFCBD5E1),
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(localGames, key = { _, g -> g.id }) { idx, game ->
                                    val isSel = idx == selectedRowIndex
                                    ConsoleListRow(
                                        title = game.title,
                                        subtitle = "${game.systemId.uppercase(Locale.ROOT)} • ${game.fileName}",
                                        badge = "LOCAL",
                                        badgePositive = true,
                                        isSelected = isSel,
                                        visuals = visuals,
                                        onClick = {
                                            if (isSel) onConfirmCreate(game) else onSelectRow(idx)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    MultiplayerTab.SERVER_SETTINGS -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(MultiplayerServerPreset.entries, key = {
                                    _,
                                    preset,
                                ->
                                preset.id
                            }) { idx, preset ->
                                val isSel = idx == selectedRowIndex
                                val isActive = preset == activeServer
                                ConsoleListRow(
                                    title = preset.displayName,
                                    subtitle = preset.subtitle,
                                    badge =
                                        if (isActive) {
                                            "ATIVO"
                                        } else if (preset == MultiplayerServerPreset.DEFAULT) {
                                            "PADRÃO"
                                        } else {
                                            ""
                                        },
                                    badgePositive = isActive,
                                    isSelected = isSel,
                                    visuals = visuals,
                                    onClick = {
                                        onSelectRow(idx)
                                        onActivateServer(preset)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // RIGHT COLUMN: Console Detail & Primary Action Card
            Column(
                modifier =
                    Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .background(visuals.cardSurface)
                        .border(1.5.dp, visuals.cardBorder, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (activeTab) {
                    MultiplayerTab.ROOMS -> {
                        val room = rooms.getOrNull(selectedRowIndex)
                        if (room == null) {
                            InspectorEmptyContent(
                                "Selecione uma sala",
                                "Use o D-Pad ou clique em Atualizar para buscar partidas ativas.",
                                visuals,
                            )
                        } else {
                            val hasExact = room.id in matchingRoomIds
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(inspectorScroll),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "SALA SELECIONADA",
                                    color = visuals.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                )
                                Text(
                                    room.gameName,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text("Criador: ${room.hostName}", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                                Text(
                                    "Núcleo: ${room.coreName} • Região: ${room.country.uppercase(Locale.ROOT)}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text =
                                        if (hasExact) {
                                            if (room.isLan) {
                                                "Sala VideoGame encontrada na sua rede local (${room.ip}:${room.port}). Pronto para entrar como Jogador 2."
                                            } else {
                                                "Jogo encontrado na sua biblioteca! Pressione A para tentar conectar à sala."
                                            }
                                        } else {
                                            "Você ainda não possui esta ROM. Pressione A para baixar do catálogo diretamente na pasta global."
                                        },
                                    color = if (hasExact) Color(0xFF6EE7B7) else visuals.secondaryAccent,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                )
                            }
                            ConsolePrimaryActionButton(
                                label = if (hasExact) "ENTRAR NA SALA" else "BAIXAR VERSÃO DA SALA",
                                accent = visuals.accent,
                                onClick = { onConfirmRoom(room) },
                            )
                        }
                    }

                    MultiplayerTab.CREATE -> {
                        val game = localGames.getOrNull(selectedRowIndex)
                        if (game == null) {
                            InspectorEmptyContent(
                                "Jogos locais",
                                "Escolha um jogo da biblioteca para abrir localmente.",
                                visuals,
                            )
                        } else {
                            Column(
                                modifier = Modifier.weight(1f).verticalScroll(inspectorScroll),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "JOGO DA BIBLIOTECA",
                                    color = visuals.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                )
                                Text(
                                    game.title,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Console: ${game.systemId.uppercase(Locale.ROOT)}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 13.sp,
                                )
                                Text(
                                    "Arquivo: ${game.fileName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "Servidor: ${activeServer.displayName}",
                                    color = visuals.secondaryAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            ConsolePrimaryActionButton(
                                label = "CRIAR SALA (HOST P1)",
                                accent = visuals.accent,
                                onClick = { onConfirmCreate(game) },
                            )
                        }
                    }

                    MultiplayerTab.SERVER_SETTINGS -> {
                        val preset =
                            MultiplayerServerPreset.entries[
                                selectedRowIndex.coerceIn(0, MultiplayerServerPreset.entries.lastIndex),
                            ]
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(inspectorScroll),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "SERVIDOR DE PARTIDAS",
                                color = visuals.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                            )
                            Text(
                                preset.displayName,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                            )
                            Text(preset.subtitle, color = Color(0xFFCBD5E1), fontSize = 12.5.sp, lineHeight = 18.sp)
                            Text(
                                text =
                                    if (preset == MultiplayerServerPreset.DEFAULT) {
                                        "Servidor padrão ativo. Salas da sua rede local (LAN) também aparecem automaticamente no topo."
                                    } else {
                                        "Você pode voltar ao padrão a qualquer momento pressionando Y."
                                    },
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ConsolePrimaryActionButton(
                                label = if (preset == activeServer) "SERVIDOR ATIVO" else "USAR ESTE SERVIDOR",
                                accent = visuals.accent,
                                onClick = { onActivateServer(preset) },
                            )
                            Surface(
                                color = Color(0x33FFFFFF),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, visuals.secondaryAccent.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth().clickable { onRestoreDefaults() },
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    GamepadButtonIcon(glyph = GamepadGlyph.Y, size = 18.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "RESTAURAR PADRÃO",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Standardized Gamepad Bar
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GamepadHintBar(
                hints =
                    listOfNotNull(
                        GamepadGlyph.L1_R1 to "Abas",
                        GamepadGlyph.DPAD_UD to "Navegar",
                        GamepadGlyph.A to "Confirmar",
                        GamepadGlyph.Y to
                            if (activeTab == MultiplayerTab.SERVER_SETTINGS) {
                                "Restaurar Padrão"
                            } else {
                                "Atualizar"
                            },
                        GamepadGlyph.B to "Início",
                    ),
                containerColor = visuals.cardSurface,
                borderColor = visuals.cardBorder,
            )
        }
    }
}

// =================================================================================================
// 2. MODO MOBILE (PORTRAIT TOUCH-FIRST LAYOUT WITH BOTTOM NAV BAR)
// =================================================================================================

@Composable
private fun MultiplayerMobileTouchLayout(
    visuals: HubThemeVisuals,
    activeTab: MultiplayerTab,
    activeServer: MultiplayerServerPreset,
    rooms: List<MultiplayerRoom>,
    matchingRoomIds: Set<String>,
    localGames: List<Game>,
    isLoadingRooms: Boolean,
    roomError: String?,
    listState: LazyListState,
    statusBanner: String?,
    downloadProgress: RomDownloadProgress?,
    onBack: () -> Unit,
    onChangeTab: (MultiplayerTab) -> Unit,
    onConfirmRoom: (MultiplayerRoom) -> Unit,
    onConfirmCreate: (Game) -> Unit,
    onActivateServer: (MultiplayerServerPreset) -> Unit,
    onRestoreDefaults: () -> Unit,
    onRefreshRooms: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        HubDashboardHeader(
            visuals = visuals,
            title = "Multiplayer",
            subtitle = "Salas online e rede local (LAN)",
            activeModule = HubModule.MULTIPLAYER,
            onBack = onBack,
            modifier = Modifier.padding(16.dp),
            compact = true,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (activeTab) {
                MultiplayerTab.ROOMS -> {
                    if (isLoadingRooms) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = visuals.accent)
                        }
                    } else if (rooms.isEmpty()) {
                        EmptyRoomsNotice(roomError, activeServer, visuals, onRefreshRooms)
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            itemsIndexed(rooms, key = { _, r -> r.id }) { _, room ->
                                val hasExact = room.id in matchingRoomIds
                                MobileTouchCard(
                                    icon = if (hasExact) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                                    iconTint = if (hasExact) Color(0xFF34D399) else visuals.secondaryAccent,
                                    title = room.gameName,
                                    subtitle = "Host: ${room.hostName} • ${room.coreName}",
                                    actionLabel = if (hasExact) "Abrir local" else "Consultar",
                                    accent = if (hasExact) visuals.accent else visuals.secondaryAccent,
                                    surface = visuals.cardSurface,
                                    onClick = { onConfirmRoom(room) },
                                )
                            }
                        }
                    }
                }

                MultiplayerTab.CREATE -> {
                    if (localGames.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Nenhum jogo na pasta global. Baixe jogos na Locadora.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            itemsIndexed(localGames, key = { _, g -> g.id }) { _, game ->
                                MobileTouchCard(
                                    icon = Icons.Default.SportsEsports,
                                    iconTint = visuals.accent,
                                    title = game.title,
                                    subtitle = "${game.systemId.uppercase(Locale.ROOT)} • ${game.fileName}",
                                    actionLabel = "Abrir local",
                                    accent = visuals.accent,
                                    surface = visuals.cardSurface,
                                    onClick = { onConfirmCreate(game) },
                                )
                            }
                        }
                    }
                }

                MultiplayerTab.SERVER_SETTINGS -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Escolha o servidor (o padrão já vem pronto para jogar):",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.5.sp,
                            )
                            MultiplayerServerPreset.entries.forEach { preset ->
                                val isSelected = preset == activeServer
                                MobileTouchCard(
                                    icon = Icons.Default.Dns,
                                    iconTint = if (isSelected) visuals.accent else Color(0xFF94A3B8),
                                    title = preset.displayName,
                                    subtitle = preset.subtitle,
                                    actionLabel = if (isSelected) "Ativo" else "Usar",
                                    accent = visuals.accent,
                                    surface =
                                        if (isSelected) {
                                            visuals.accent.copy(
                                                alpha = 0.22f,
                                            )
                                        } else {
                                            visuals.cardSurface
                                        },
                                    onClick = { onActivateServer(preset) },
                                )
                            }
                        }

                        Surface(
                            color = visuals.secondaryAccent.copy(alpha = 0.22f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.2.dp, visuals.secondaryAccent),
                            modifier =
                                Modifier.fillMaxWidth().padding(
                                    bottom = 6.dp,
                                ).clickable(onClick = onRestoreDefaults),
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 13.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.Restore,
                                    null,
                                    tint = visuals.secondaryAccent,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Restaurar Servidor Padrão",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mobile Bottom Navigation Bar (Touch-Native)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xEE0A0E16))
                    .border(width = 0.5.dp, color = Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MultiplayerTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) visuals.accent.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onChangeTab(tab) }
                            .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) visuals.accent else Color(0xFF94A3B8),
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        text = tab.label,
                        color = if (selected) visuals.accent else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// =================================================================================================
// SHARED BUILDING BLOCKS
// =================================================================================================

@Composable
private fun ConsoleListRow(
    title: String,
    subtitle: String,
    badge: String,
    badgePositive: Boolean,
    isSelected: Boolean,
    visuals: HubThemeVisuals,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (isSelected) visuals.accent.copy(alpha = 0.24f) else visuals.cardSurface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) visuals.accent else Color.White.copy(alpha = 0.12f),
                    shape = shape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = Color(0xFFB4C2D6), fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge.isNotEmpty()) {
            Surface(
                color = if (badgePositive) Color(0xFF065F46) else visuals.secondaryAccent.copy(alpha = 0.22f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = badge,
                    color = if (badgePositive) Color(0xFFA7F3D0) else visuals.secondaryAccent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun MobileTouchCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    actionLabel: String,
    accent: Color,
    surface: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(surface)
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                color = Color(0xFFB4C2D6),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            color = accent.copy(alpha = 0.22f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, accent),
        ) {
            Text(
                text = actionLabel,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ConsolePrimaryActionButton(
    label: String,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GamepadButtonIcon(glyph = GamepadGlyph.A, size = 18.dp)
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            label,
            color = Color(0xFF0A0D12),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
        )
    }
}

@Composable
private fun InspectorEmptyContent(
    title: String,
    body: String,
    visuals: HubThemeVisuals,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(Locale.ROOT), color = visuals.accent, fontSize = 12.sp, fontWeight = FontWeight.Black)
        Text(body, color = Color(0xFFCBD5E1), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun EmptyRoomsNotice(
    roomError: String?,
    activeServer: MultiplayerServerPreset,
    visuals: HubThemeVisuals,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = roomError ?: "Nenhuma sala ativa encontrada em ${activeServer.displayName}.",
            color = Color(0xFFCBD5E1),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            color = visuals.accent.copy(alpha = 0.22f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, visuals.accent),
            modifier = Modifier.clickable(onClick = onRefresh),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Default.Refresh, null, tint = visuals.accent, modifier = Modifier.size(16.dp))
                Text("Atualizar Salas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }
}

@Composable
private fun MultiplayerStatusAndProgress(
    statusBanner: String?,
    downloadProgress: RomDownloadProgress?,
    visuals: HubThemeVisuals,
) {
    if (!statusBanner.isNullOrBlank()) {
        Surface(
            color = Color(0xEB0D1320),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.3.dp, visuals.accent.copy(alpha = 0.85f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = statusBanner,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    downloadProgress?.let { prog ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xDD111827))
                    .border(1.5.dp, visuals.accent, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${prog.status} ${prog.filename}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (prog.totalBytes > 0L) "${prog.percent}%" else "...",
                    color = visuals.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            if (prog.totalBytes > 0L) {
                LinearProgressIndicator(
                    progress = { prog.percent / 100f },
                    color = visuals.accent,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                )
            } else {
                LinearProgressIndicator(
                    color = visuals.accent,
                    trackColor = Color.White.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
        }
    }
}

@Composable
private fun MultiplayerVersionConfirmModal(
    room: MultiplayerRoom,
    visuals: HubThemeVisuals,
    isSearchingCatalog: Boolean,
    catalogMatch: CatalogRomMatch?,
    onConfirmDownload: (CatalogRomMatch) -> Unit,
    onDismiss: () -> Unit,
) {
    val systemLabel = room.consoleId.uppercase(Locale.ROOT)
    val folderName = catalogMatch?.let { HubConfigManager.mapConsoleToFolderName(it.consoleId) }.orEmpty()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val modalFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { modalFocus.requestFocus() }
        Box(
            Modifier.fillMaxWidth().padding(24.dp).focusRequester(modalFocus).focusable().onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                        if (event.type == KeyEventType.KeyUp && !event.nativeKeyEvent.isCanceled) onDismiss()
                        true
                    }
                    else -> false
                }
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth(0.84f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xF5111827))
                        .border(1.5.dp, visuals.accent, RoundedCornerShape(12.dp))
                        .clickable(enabled = false, onClick = {})
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "JOGO NÃO LOCALIZADO NA BIBLIOTECA",
                    color = visuals.accent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "O anfitrião (${room.hostName}) anuncia «${room.romVersionName}» ($systemLabel).",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                if (isSearchingCatalog) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            color = visuals.accent,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            "Verificando versão no catálogo da Locadora...",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                    }
                } else if (catalogMatch != null) {
                    Text(
                        text =
                            "Arquivo localizado por nome: «${catalogMatch.filename}». " +
                                "CRC e compatibilidade não verificados. " +
                                "Deseja baixar para a pasta global ($folderName)?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "CANCELAR",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier =
                                Modifier.clickable(
                                    onClick = onDismiss,
                                ).padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = visuals.accent,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable { onConfirmDownload(catalogMatch) },
                        ) {
                            Text(
                                text = "BAIXAR VERSÃO",
                                color = Color(0xFF0A0D12),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text =
                            "Não foi localizado um arquivo com este nome no catálogo offline. " +
                                "Você pode buscá-lo manualmente na Locadora.",
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(
                            color = Color(0xFF334155),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable(onClick = onDismiss),
                        ) {
                            Text(
                                "FECHAR",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
