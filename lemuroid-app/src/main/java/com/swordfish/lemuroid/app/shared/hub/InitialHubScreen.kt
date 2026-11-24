package com.swordfish.lemuroid.app.shared.hub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.swordfish.lemuroid.R
import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadButtonIcon
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintBar
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GeneratedThemeBackdrop
import com.swordfish.lemuroid.app.shared.ui.FrontendSoundEngine

enum class HubModule {
    PLAY,
    LOCADORA,
    MULTIPLAYER,
}

private var videoGameSplashShownInProcess = false

private enum class HubOverlayState {
    NONE,
    SETTINGS_MENU,
    THEME_PICKER,
}

private data class HubCardSpec(
    val module: HubModule,
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
)

@Composable
fun InitialHubScreen(
    activeTheme: FrontendTheme,
    totalGames: Int,
    totalSystems: Int,
    folderDisplayPath: String,
    onSelectTheme: (FrontendTheme) -> Unit,
    onPickGlobalFolder: () -> Unit,
    onSyncLibrary: () -> Unit = {},
    isSyncingLibrary: Boolean = false,
    initialOpenSettings: Boolean = false,
    onConsumedInitialOpenSettings: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenPlay: () -> Unit,
    onOpenLocadora: () -> Unit,
    onOpenMultiplayer: () -> Unit,
    onRequestExit: (() -> Unit)? = null,
    isTvMode: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val visuals = remember(activeTheme) { HubConfigManager.visualsFor(activeTheme) }
    val activeServer = remember(context) { HubConfigManager.getSelectedMultiplayerServer(context) }

    var showSplash by remember { mutableStateOf(!videoGameSplashShownInProcess) }
    LaunchedEffect(showSplash) {
        if (showSplash) {
            kotlinx.coroutines.delay(1450L)
            videoGameSplashShownInProcess = true
            showSplash = false
        }
    }

    var availableRelease by remember { mutableStateOf(GitHubReleaseUpdater.cachedRelease) }
    var isUpdatingApp by remember { mutableStateOf(false) }
    var updateProgressPct by remember { mutableIntStateOf(0) }
    var updateProgressText by remember { mutableStateOf<String?>(null) }

    var selectedCardIndex by rememberSaveable { mutableIntStateOf(0) }
    var overlayState by rememberSaveable { mutableStateOf(HubOverlayState.NONE) }
    var settingsMenuIndex by rememberSaveable { mutableIntStateOf(0) }
    var themePickerIndex by rememberSaveable(activeTheme) {
        mutableIntStateOf(FrontendTheme.entries.indexOf(activeTheme).coerceAtLeast(0))
    }
    var localSyncTriggered by remember { mutableStateOf(false) }
    var sawSyncRunning by remember { mutableStateOf(false) }
    var syncCompletedNotice by remember { mutableStateOf<String?>(null) }

    // Silent background check for newer GitHub Release on cold start
    LaunchedEffect(Unit) {
        if (GitHubReleaseUpdater.cachedRelease == null) {
            GitHubReleaseUpdater.checkLatestRelease(context).onSuccess { info ->
                availableRelease = info
                if (info.isNewer) {
                    syncCompletedNotice =
                        "Nova versão ${info.tagName} disponível! Abra Configurações → Atualização."
                }
            }
        }
    }

    fun handleCheckOrInstallUpdate() {
        if (isUpdatingApp) return
        val cached = availableRelease
        if (cached != null && cached.isNewer) {
            isUpdatingApp = true
            updateProgressPct = 0
            updateProgressText = "Preparando atualização ${cached.tagName}..."
            scope.launch {
                val res =
                    GitHubReleaseUpdater.downloadAndLaunchInstaller(context, cached) { pct, msg ->
                        updateProgressPct = pct
                        updateProgressText = msg
                    }
                isUpdatingApp = false
                updateProgressText = null
                res.onFailure { err ->
                    syncCompletedNotice = "Erro ao atualizar: ${err.message ?: "tente novamente"}"
                }
            }
            return
        }
        isUpdatingApp = true
        updateProgressPct = 0
        updateProgressText = "Verificando atualizações no GitHub..."
        scope.launch {
            val res = GitHubReleaseUpdater.checkLatestRelease(context)
            isUpdatingApp = false
            updateProgressText = null
            res.onSuccess { info ->
                availableRelease = info
                if (info.isNewer) {
                    syncCompletedNotice = "Nova versão ${info.tagName} encontrada! Iniciando download..."
                    handleCheckOrInstallUpdate()
                } else {
                    syncCompletedNotice =
                        "Você já está na versão mais recente (v${GitHubReleaseUpdater.currentInstalledVersion()})."
                }
            }.onFailure { err ->
                syncCompletedNotice = "Não foi possível verificar atualizações (${err.message ?: "sem rede"})."
            }
        }
    }

    LaunchedEffect(isSyncingLibrary, localSyncTriggered) {
        if (isSyncingLibrary) {
            sawSyncRunning = true
            localSyncTriggered = true
            syncCompletedNotice = null
        } else if (localSyncTriggered) {
            if (!sawSyncRunning) {
                kotlinx.coroutines.delay(1600L)
            }
            if (!isSyncingLibrary) {
                val wasRunning = sawSyncRunning
                localSyncTriggered = false
                sawSyncRunning = false
                syncCompletedNotice = if (wasRunning) {
                    "Biblioteca atualizada • $totalGames jogos em $totalSystems sistemas."
                } else {
                    "Sincronização solicitada. A biblioteca será atualizada em segundo plano."
                }
            }
        }
    }

    LaunchedEffect(syncCompletedNotice) {
        if (syncCompletedNotice != null) {
            kotlinx.coroutines.delay(3000L)
            syncCompletedNotice = null
        }
    }

    BackHandler(enabled = overlayState != HubOverlayState.NONE) { overlayState = HubOverlayState.NONE }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(initialOpenSettings) {
        if (initialOpenSettings) {
            overlayState = HubOverlayState.SETTINGS_MENU
            onConsumedInitialOpenSettings()
        }
    }
    LaunchedEffect(overlayState) {
        runCatching { focusRequester.requestFocus() }
    }

    val cards =
        remember(totalGames, totalSystems, activeServer) {
            listOf(
                HubCardSpec(
                    module = HubModule.PLAY,
                    title = "JOGAR",
                    subtitle = "Sua biblioteca de consoles, saves e emuladores",
                    badge =
                        if (totalGames > 0) {
                            "$totalGames jogos • $totalSystems consoles"
                        } else {
                            "Biblioteca local"
                        },
                    icon = Icons.Default.SportsEsports,
                ),
                HubCardSpec(
                    module = HubModule.LOCADORA,
                    title = "LOCADORA",
                    subtitle = "Catálogo completo, capas e downloads diretos",
                    badge = "Catálogo & Downloads",
                    icon = Icons.Default.CloudDownload,
                ),
                HubCardSpec(
                    module = HubModule.MULTIPLAYER,
                    title = "MULTIPLAYER",
                    subtitle = "Salas públicas e jogos da sua coleção",
                    badge = "Comunidade",
                    icon = Icons.Default.Groups,
                ),
            )
        }

    fun openModule(module: HubModule) {
        FrontendSoundEngine.playConfirm(context, activeTheme)
        when (module) {
            HubModule.PLAY -> onOpenPlay()
            HubModule.LOCADORA -> onOpenLocadora()
            HubModule.MULTIPLAYER -> onOpenMultiplayer()
        }
    }

    fun executeSettingsMenuAction(index: Int) {
        FrontendSoundEngine.playConfirm(context, activeTheme)
        when (index) {
            0 -> {
                themePickerIndex = FrontendTheme.entries.indexOf(activeTheme).coerceAtLeast(0)
                overlayState = HubOverlayState.THEME_PICKER
            }
            1 -> {
                overlayState = HubOverlayState.NONE
                onPickGlobalFolder()
            }
            2 -> {
                overlayState = HubOverlayState.NONE
                localSyncTriggered = true
                syncCompletedNotice = null
                onSyncLibrary()
            }
            3 -> {
                overlayState = HubOverlayState.NONE
                onOpenSettings()
            }
            4 -> {
                overlayState = HubOverlayState.NONE
                handleCheckOrInstallUpdate()
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val actionKey = keyEvent.nativeKeyEvent.keyCode in setOf(
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A,
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_START,
                        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BUTTON_SELECT,
                    )
                    // Activate on release so opening a dialog/screen cannot also receive this press.
                    if (actionKey && keyEvent.type == KeyEventType.KeyDown) return@onPreviewKeyEvent true
                    if (!actionKey && keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (actionKey && keyEvent.nativeKeyEvent.isCanceled) return@onPreviewKeyEvent true
                    val code = keyEvent.nativeKeyEvent.keyCode

                    when (overlayState) {
                        HubOverlayState.THEME_PICKER -> {
                            val themes = FrontendTheme.entries
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    themePickerIndex = (themePickerIndex - 1 + themes.size) % themes.size
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    themePickerIndex = (themePickerIndex + 1) % themes.size
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    themePickerIndex = (themePickerIndex - 3 + themes.size) % themes.size
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    themePickerIndex = (themePickerIndex + 3) % themes.size
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_BUTTON_A,
                                -> {
                                    val chosen = themes[themePickerIndex.coerceIn(0, themes.lastIndex)]
                                    onSelectTheme(chosen)
                                    FrontendSoundEngine.playConfirm(context, chosen)
                                    overlayState = HubOverlayState.SETTINGS_MENU
                                    true
                                }
                                KeyEvent.KEYCODE_BACK,
                                KeyEvent.KEYCODE_BUTTON_B,
                                -> {
                                    overlayState = HubOverlayState.SETTINGS_MENU
                                    true
                                }
                                else -> false
                            }
                        }

                        HubOverlayState.SETTINGS_MENU -> {
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    settingsMenuIndex = (settingsMenuIndex - 1 + 5) % 5
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    settingsMenuIndex = (settingsMenuIndex + 1) % 5
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_BUTTON_A,
                                -> {
                                    executeSettingsMenuAction(settingsMenuIndex)
                                    true
                                }
                                KeyEvent.KEYCODE_BACK,
                                KeyEvent.KEYCODE_BUTTON_B,
                                KeyEvent.KEYCODE_BUTTON_START,
                                -> {
                                    overlayState = HubOverlayState.NONE
                                    true
                                }
                                else -> false
                            }
                        }

                        HubOverlayState.NONE -> {
                            when (code) {
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_UP,
                                -> {
                                    selectedCardIndex = (selectedCardIndex - 1 + cards.size) % cards.size
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                -> {
                                    selectedCardIndex = (selectedCardIndex + 1) % cards.size
                                    FrontendSoundEngine.playNavigate(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_BUTTON_A,
                                -> {
                                    openModule(cards[selectedCardIndex].module)
                                    true
                                }
                                KeyEvent.KEYCODE_BUTTON_SELECT -> {
                                    FrontendSoundEngine.playConfirm(context, activeTheme)
                                    onSyncLibrary()
                                    true
                                }
                                KeyEvent.KEYCODE_BUTTON_START,
                                KeyEvent.KEYCODE_MENU,
                                -> {
                                    settingsMenuIndex = 0
                                    overlayState = HubOverlayState.SETTINGS_MENU
                                    FrontendSoundEngine.playConfirm(context, activeTheme)
                                    true
                                }
                                KeyEvent.KEYCODE_BACK,
                                KeyEvent.KEYCODE_BUTTON_B,
                                -> {
                                    // Exit is only triggered via the explicit "Sair" button.
                                    true
                                }
                                else -> false
                            }
                        }
                    }
                },
    ) {
        // Theme-specific generated artwork backdrop (clipped cleanly)
        GeneratedThemeBackdrop(
            theme = activeTheme,
            modifier = Modifier.fillMaxSize(),
        )

        // Cinematic themed scrim
        val hubScrimBrush =
            remember(visuals) {
                Brush.verticalGradient(
                    colors = listOf(visuals.scrimTop, Color(0x88090C12), visuals.scrimBottom),
                )
            }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(hubScrimBrush),
        )

        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = if (isTvMode) 24.dp else 18.dp, vertical = if (isTvMode) 18.dp else 16.dp),
        ) {
            val isLandscape = maxWidth >= maxHeight

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // CLEAN CONSOLE-OS TOP BAR: Brand on left, single Configurações button on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HubDashboardHeader(
                        visuals = visuals,
                        title = "Sua central de jogos",
                        subtitle = "$totalGames jogos • $totalSystems sistemas • ${visuals.displayName}",
                        activeModule = null,
                        modifier = Modifier.weight(1f),
                    )

                    // Top-right actions: Configurações (Temas & Pastas inside) + Sair
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val cfgShape = RoundedCornerShape(10.dp)
                        Row(
                            modifier =
                                Modifier
                                    .clip(cfgShape)
                                    .background(Color(0xCC101520))
                                    .border(1.2.dp, visuals.accent.copy(alpha = 0.55f), cfgShape)
                                    .clickable {
                                        settingsMenuIndex = 0
                                        overlayState = HubOverlayState.SETTINGS_MENU
                                    }
                                    .padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurações",
                                tint = visuals.accent,
                                modifier = Modifier.size(17.dp),
                            )
                            if (isLandscape) {
                                Text(
                                    text = "Configurações",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }

                        if (onRequestExit != null) {
                            Row(
                                modifier =
                                    Modifier
                                        .clip(cfgShape)
                                        .background(Color(0xCC241014))
                                        .border(1.2.dp, Color(0x99FF5252), cfgShape)
                                        .clickable { onRequestExit() }
                                        .padding(horizontal = 13.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                    contentDescription = "Sair",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(17.dp),
                                )
                                Text(
                                    text = "Sair",
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // CENTER: 3 HERO CONSOLE CARDS (JOGAR · LOCADORA · MULTIPLAYER)
                if (isLandscape) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        cards.forEachIndexed { idx, spec ->
                            HubSystemCard(
                                spec = spec,
                                visuals = visuals,
                                isSelected = idx == selectedCardIndex,
                                onClick = {
                                    selectedCardIndex = idx
                                    openModule(spec.module)
                                },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(0.84f),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    ) {
                        cards.forEachIndexed { idx, spec ->
                            HubSystemCard(
                                spec = spec,
                                visuals = visuals,
                                isSelected = idx == selectedCardIndex,
                                onClick = {
                                    selectedCardIndex = idx
                                    openModule(spec.module)
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                            )
                        }
                    }
                }

                // BOTTOM CONSOLE BAR
                if (isTvMode || isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        GamepadHintBar(
                            hints =
                                listOf(
                                    GamepadGlyph.DPAD_LR to "Navegar",
                                    GamepadGlyph.A to "Entrar",
                                    GamepadGlyph.SELECT to "Sincronizar",
                                    GamepadGlyph.START to "Configurações",
                                ),
                            containerColor = visuals.cardSurface,
                            borderColor = visuals.cardBorder,
                        )
                    }
                }
            }
        }

        // FLOATING NOTIFICATION OVERLAY FOR LIBRARY SYNC & SYSTEM UPDATES (Zero layout shift!)
        val activeFloatingProgress = isSyncingLibrary || localSyncTriggered || isUpdatingApp
        val floatingMessage =
            updateProgressText
                ?: syncCompletedNotice
                ?: "Sincronizando biblioteca (Jogar, Locadora e Multiplayer)..."
        if (activeFloatingProgress || syncCompletedNotice != null) {
            val toastShape = RoundedCornerShape(14.dp)
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .displayCutoutPadding()
                        .padding(top = 14.dp, start = 20.dp, end = 20.dp)
                        .fillMaxWidth(if (isTvMode) 0.56f else 0.90f)
                        .clip(toastShape)
                        .background(Color(0xEB0D1320))
                        .border(1.4.dp, visuals.accent.copy(alpha = 0.85f), toastShape)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (activeFloatingProgress) {
                        CircularProgressIndicator(
                            color = visuals.accent,
                            strokeWidth = 2.2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = floatingMessage,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (activeFloatingProgress) {
                    if (isUpdatingApp && updateProgressPct > 0) {
                        LinearProgressIndicator(
                            progress = { updateProgressPct / 100f },
                            color = visuals.accent,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth().height(3.5.dp).clip(RoundedCornerShape(4.dp)),
                        )
                    } else {
                        LinearProgressIndicator(
                            color = visuals.accent,
                            trackColor = Color.White.copy(alpha = 0.16f),
                            modifier = Modifier.fillMaxWidth().height(3.5.dp).clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }

        // HUB SETTINGS OVERLAY (Temas, Pastas Globais, Sincronização, Emulador, Atualização)
        if (overlayState == HubOverlayState.SETTINGS_MENU) {
            HubSettingsMenuOverlay(
                visuals = visuals,
                folderDisplayPath = folderDisplayPath,
                availableRelease = availableRelease,
                focusedIndex = settingsMenuIndex,
                onSelectOption = { idx ->
                    settingsMenuIndex = idx
                    executeSettingsMenuAction(idx)
                },
                onDismiss = { overlayState = HubOverlayState.NONE },
            )
        }

        // LIVE 9-THEME PICKER OVERLAY
        if (overlayState == HubOverlayState.THEME_PICKER) {
            HubThemePickerOverlay(
                activeTheme = activeTheme,
                focusedIndex = themePickerIndex,
                onSelect = { theme ->
                    onSelectTheme(theme)
                    overlayState = HubOverlayState.SETTINGS_MENU
                },
                onDismiss = { overlayState = HubOverlayState.SETTINGS_MENU },
            )
        }

        // CINEMATIC COLD-START SPLASH SCREEN (Once per app launch, skippable on tap)
        if (showSplash) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF05070E))
                        .clickable {
                            videoGameSplashShownInProcess = true
                            showSplash = false
                        },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.splash_videogame),
                    contentDescription = "VideoGame",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun HubSystemCard(
    spec: HubCardSpec,
    visuals: HubThemeVisuals,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) visuals.accent else Color.White.copy(alpha = 0.14f)
    val shape = remember { RoundedCornerShape(12.dp) }
    val bgBrush =
        remember(visuals, isSelected) {
            Brush.verticalGradient(
                colors =
                    if (isSelected) {
                        listOf(
                            visuals.accent.copy(alpha = 0.26f),
                            visuals.cardSurface,
                            Color(0xEE0B0E14),
                        )
                    } else {
                        listOf(visuals.cardSurface.copy(alpha = 0.78f), Color(0xDD0B0E14))
                    },
            )
        }
    val overlayScrim =
        remember {
            Brush.verticalGradient(
                0f to Color(0x22090D16),
                0.45f to Color.Transparent,
                0.68f to Color(0xDF0B101B),
                1f to Color(0xFF0B101B),
            )
        }

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(bgBrush)
                .border(width = if (isSelected) 2.5.dp else 1.dp, color = borderColor, shape = shape)
                .clickable(onClick = onClick),
    ) {
        val artwork = when (spec.module) {
            HubModule.PLAY -> R.drawable.dashboard_module_play
            HubModule.LOCADORA -> R.drawable.dashboard_module_locadora
            HubModule.MULTIPLAYER -> R.drawable.dashboard_module_multiplayer
        }
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        )
        Box(
            Modifier.matchParentSize().background(overlayScrim),
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) visuals.accent else visuals.accent.copy(alpha = 0.18f),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = spec.title,
                        tint = if (isSelected) Color(0xFF0A0D12) else visuals.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.45f)),
                ) {
                    Text(
                        text = spec.badge,
                        color = if (isSelected) visuals.accent else Color(0xFFC5D0E0),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = spec.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                )
                Text(
                    text = spec.subtitle,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        GamepadButtonIcon(glyph = GamepadGlyph.A, size = 18.dp)
                        Text(
                            text = "ENTRAR",
                            color = visuals.accent,
                            fontSize = 11.5.sp,
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
private fun HubSettingsMenuOverlay(
    visuals: HubThemeVisuals,
    folderDisplayPath: String,
    availableRelease: GitHubReleaseInfo?,
    focusedIndex: Int,
    onSelectOption: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val updateSubtitle =
        if (availableRelease?.isNewer == true) {
            "Nova versão ${availableRelease.tagName} disponível! Pressione A para baixar e atualizar"
        } else {
            "Versão instalada v${GitHubReleaseUpdater.currentInstalledVersion()} • Verificar no GitHub"
        }
    val items =
        listOf(
            Triple(Icons.Default.Palette, "Temas do Sistema", "Tema ativo: ${visuals.displayName} (aplica nos 3 sistemas)"),
            Triple(Icons.Default.FolderOpen, "Pastas Globais de Jogos", "Pasta atual: $folderDisplayPath"),
            Triple(Icons.Default.Refresh, "Sincronizar Biblioteca", "Reescaneia jogos para Jogar, Locadora e Multiplayer"),
            Triple(Icons.Default.Tune, "Emulação, Núcleos e Controles", "Perfis de desempenho, molduras, cores Libretro e gamepad"),
            Triple(Icons.Default.CloudDownload, "Atualização do Sistema", updateSubtitle),
        )

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xD905070C))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.74f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF5111622))
                    .border(1.5.dp, visuals.accent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .clickable(enabled = false, onClick = {})
                    .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CONFIGURAÇÕES",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = "FECHAR",
                    color = visuals.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            items.forEachIndexed { idx, (icon, title, subtitle) ->
                val isFocused = idx == focusedIndex
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(if (isFocused) visuals.accent.copy(alpha = 0.22f) else Color(0xFF1A2232))
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) visuals.accent else Color.White.copy(alpha = 0.12f),
                                shape = shape,
                            )
                            .clickable { onSelectOption(idx) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isFocused) visuals.accent else Color(0xFFCBD5E1),
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = subtitle,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (isFocused) visuals.accent else Color(0xFF64748B),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HubThemePickerOverlay(
    activeTheme: FrontendTheme,
    focusedIndex: Int,
    onSelect: (FrontendTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val themes = FrontendTheme.entries
    val currentVisuals = HubConfigManager.visualsFor(activeTheme)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xD905070C))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xF5111622))
                    .border(1.5.dp, currentVisuals.accent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                    .clickable(enabled = false, onClick = {})
                    .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "TEMA DO SISTEMA (3-EM-1)",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "Aplica automaticamente na Tela Inicial, Jogar, Locadora e Multiplayer",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                    )
                }
                Text(
                    text = "VOLTAR",
                    color = currentVisuals.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(276.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(2.dp),
            ) {
                itemsIndexed(themes) { idx, theme ->
                    val tv = HubConfigManager.visualsFor(theme)
                    val isFocused = idx == focusedIndex
                    val isCurrent = theme == activeTheme
                    val tileShape = RoundedCornerShape(12.dp)
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(tileShape)
                                .background(
                                    if (isFocused) tv.accent.copy(alpha = 0.22f)
                                    else Color(0xFF1A2232),
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 1.dp,
                                    color =
                                        when {
                                            isFocused -> tv.accent
                                            isCurrent -> Color.White.copy(alpha = 0.55f)
                                            else -> Color.White.copy(alpha = 0.12f)
                                        },
                                    shape = tileShape,
                                )
                                .clickable { onSelect(theme) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(tv.accent)
                                    .border(2.dp, tv.secondaryAccent, CircleShape),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tv.displayName,
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (isCurrent) "Ativo" else tv.subtitle,
                                color = if (isCurrent) tv.accent else Color(0xFF94A3B8),
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
