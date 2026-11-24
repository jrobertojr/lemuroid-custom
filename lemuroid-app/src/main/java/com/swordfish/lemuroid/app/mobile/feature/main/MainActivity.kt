package com.swordfish.lemuroid.app.mobile.feature.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.DescriptionScrollHost
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.DescriptionScrollInput
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesScreen
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesViewModel
import com.swordfish.lemuroid.app.mobile.feature.games.GamesScreen
import com.swordfish.lemuroid.app.mobile.feature.games.GamesViewModel
import com.swordfish.lemuroid.app.mobile.feature.home.HomeScreen
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.search.SearchScreen
import com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsScreen
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import de.charlex.compose.material3.HtmlText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity, DescriptionScrollHost {
    override val descriptionScroll = DescriptionScrollInput()

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean =
        descriptionScroll.dispatch(event) || super.dispatchGenericMotionEvent(event)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) descriptionScroll.stop()
        if (hasFocus) applyImmersiveFullScreen()
        super.onWindowFocusChanged(hasFocus)
    }

    private fun applyImmersiveFullScreen() {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
    }

    override fun onPause() {
        descriptionScroll.stop()
        super.onPause()
    }

    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    private val reviewManager = ReviewManager()

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveFullScreen()

        GlobalScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            MainScreen(navController)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val onGameClick = { game: Game ->
                gameInteractor.onGamePlay(game)
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val onHelpPressed = {
                infoDialogDisplayed.value = true
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            val activeFrontendTheme = com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendTheme.current
            var activeHubSection by rememberSaveable { mutableStateOf("HUB") }
            var pendingRouteAfterHub by remember { mutableStateOf<MainRoute?>(null) }
            var returnToHubSettingsOnBack by rememberSaveable { mutableStateOf(false) }
            var openHubSettingsOnEnter by rememberSaveable { mutableStateOf(false) }
            var visitedHubSettings by rememberSaveable { mutableStateOf(false) }
            val allSystemIds = remember { SystemID.entries.map { it.dbname } }
            val systemCounts by remember {
                retrogradeDb.gameDao().selectSystemsWithCount()
            }.collectAsState(initial = emptyList())
            val allGames by remember {
                retrogradeDb.gameDao().selectBySystemsList(allSystemIds)
            }.collectAsState(initial = emptyList())

            LaunchedEffect(allGames) {
                com.swordfish.lemuroid.app.shared.hub.HubConfigManager
                    .updateIndexedGamesSnapshot(allGames)
            }

            var showDashboardExit by remember { mutableStateOf(false) }
            if (showDashboardExit) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDashboardExit = false },
                    title = { androidx.compose.material3.Text("Sair do VideoGame?") },
                    text = { androidx.compose.material3.Text("Deseja fechar o aplicativo?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { showDashboardExit = false }) {
                            androidx.compose.material3.Text("Continuar no aplicativo")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { finishAndRemoveTask() }) {
                            androidx.compose.material3.Text("Sair")
                        }
                    },
                )
            }
            BackHandler(enabled = activeHubSection == "HUB") { showDashboardExit = true }
            BackHandler(enabled = activeHubSection != "HUB" && currentRoute == MainRoute.HOME) {
                activeHubSection = "HUB"
            }

            if (activeHubSection == "HUB") {
                com.swordfish.lemuroid.app.shared.hub.InitialHubScreen(
                    activeTheme = activeFrontendTheme,
                    totalGames = systemCounts.sumOf { it.count },
                    totalSystems = systemCounts.count { it.count > 0 },
                    folderDisplayPath =
                        com.swordfish.lemuroid.app.shared.hub.HubConfigManager
                            .getReadableGlobalRomFolder(this@MainActivity),
                    onSelectTheme = { chosen ->
                        com.swordfish.lemuroid.app.shared.hub.HubConfigManager
                            .setActiveTheme(this@MainActivity, chosen)
                    },
                    onPickGlobalFolder = {
                        runCatching { settingsInteractor.changeLocalStorageFolder() }
                    },
                    onSyncLibrary = {
                        runCatching {
                            com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
                                .scheduleLibrarySync(applicationContext)
                        }
                    },
                    isSyncingLibrary = mainUIState.operationInProgress,
                    initialOpenSettings = openHubSettingsOnEnter,
                    onConsumedInitialOpenSettings = { openHubSettingsOnEnter = false },
                    onOpenSettings = {
                        returnToHubSettingsOnBack = true
                        visitedHubSettings = false
                        pendingRouteAfterHub = MainRoute.SETTINGS
                        activeHubSection = "PLAY"
                    },
                    onOpenPlay = {
                        returnToHubSettingsOnBack = false
                        activeHubSection = "PLAY"
                    },
                    onOpenLocadora = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                com.swordfish.lemuroid.app.shared.hub.LocadoraWebActivity::class.java,
                            ),
                        )
                    },
                    onOpenMultiplayer = {
                        activeHubSection = "MULTIPLAYER"
                    },
                    onRequestExit = { showDashboardExit = true },
                    isTvMode = false,
                )
                return@AppTheme
            }

            if (activeHubSection == "MULTIPLAYER") {
                com.swordfish.lemuroid.app.shared.hub.MultiplayerHubScreen(
                    activeTheme = activeFrontendTheme,
                    localGames = allGames,
                    onLaunchGame = onGameClick,
                    onBackToHub = { activeHubSection = "HUB" },
                    isTvMode = false,
                )
                return@AppTheme
            }

            val useFullThemedHome =
                activeFrontendTheme != com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme.DEFAULT &&
                    currentRoute == MainRoute.HOME

            LaunchedEffect(pendingRouteAfterHub) {
                val target = pendingRouteAfterHub ?: return@LaunchedEffect
                pendingRouteAfterHub = null
                runCatching { navController.navigateToRoute(target) }
            }

            LaunchedEffect(currentRoute, returnToHubSettingsOnBack) {
                if (returnToHubSettingsOnBack && currentRoute != MainRoute.HOME) visitedHubSettings = true
            }
            // Return to the hub only after the Settings destination has been observed.
            LaunchedEffect(activeHubSection, currentRoute, returnToHubSettingsOnBack, pendingRouteAfterHub) {
                if (activeHubSection == "PLAY" &&
                    returnToHubSettingsOnBack && visitedHubSettings &&
                    pendingRouteAfterHub == null &&
                    currentRoute == MainRoute.HOME
                ) {
                    returnToHubSettingsOnBack = false
                    openHubSettingsOnEnter = true
                    activeHubSection = "HUB"
                }
            }

            Scaffold(
                topBar = {
                    if (!useFullThemedHome) {
                        MainTopBar(
                            currentRoute = currentRoute,
                            navController = navController,
                            onHelpPressed = onHelpPressed,
                            mainUIState = mainUIState,
                            onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                        )
                    }
                },
                bottomBar = {
                    if (!useFullThemedHome) {
                        MainNavigationBar(currentRoute, navController)
                    }
                },
            ) { padding ->
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = MainRoute.HOME.route,
                ) {
                    composable(MainRoute.HOME) {
                        if (useFullThemedHome) {
                            MobileThemedFrontend(
                                retrogradeDb = retrogradeDb,
                                gameInteractor = gameInteractor,
                                onOpenSettings = { navController.navigateToRoute(MainRoute.SETTINGS) },
                                onOpenSearch = { navController.navigateToRoute(MainRoute.SEARCH) },
                                onRescan = {
                                    com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
                                        .scheduleLibrarySync(applicationContext)
                                },
                            )
                        } else {
                            HomeScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            HomeViewModel.Factory(
                                                applicationContext,
                                                retrogradeDb,
                                                coresSelection,
                                            ),
                                    ),
                                onGameClick = onGameClick,
                                onGameLongClick = onGameLongClick,
                                onOpenCoreSelection = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
                            )
                        }
                    }
                    composable(MainRoute.FAVORITES) {
                        FavoritesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = FavoritesViewModel.Factory(retrogradeDb),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                        )
                    }
                    composable(MainRoute.SEARCH) {
                        SearchScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = SearchViewModel.Factory(retrogradeDb),
                                ),
                            searchQuery = mainUIState.searchQuery,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                            onResetSearchQuery = { mainViewModel.changeQueryString("") },
                        )
                    }
                    composable(MainRoute.SYSTEMS) {
                        MetaSystemsScreen(
                            modifier = Modifier.padding(padding),
                            navController = navController,
                            viewModel =
                                viewModel(
                                    factory =
                                        MetaSystemsViewModel.Factory(
                                            retrogradeDb,
                                            applicationContext,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SYSTEM_GAMES) { entry ->
                        val metaSystemId = entry.arguments?.getString("metaSystemId")
                        GamesScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        GamesViewModel.Factory(
                                            retrogradeDb,
                                            MetaSystemID.valueOf(metaSystemId!!),
                                        ),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                        )
                    }
                    composable(MainRoute.SETTINGS) {
                        SettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            saveSyncManager,
                                            FlowSharedPreferences(
                                                SharedPreferencesHelper.getLegacySharedPreferences(
                                                    applicationContext,
                                                ),
                                            ),
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_ADVANCED) {
                        AdvancedSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        AdvancedSettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_BIOS) {
                        BiosScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = BiosSettingsViewModel.Factory(biosManager),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_CORES_SELECTION) {
                        CoresSelectionScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        CoresSelectionViewModel.Factory(
                                            applicationContext,
                                            coresSelection,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                        InputDevicesSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        InputDevicesSettingsViewModel.Factory(
                                            applicationContext,
                                            inputDeviceManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                        SaveSyncSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SaveSyncSettingsViewModel.Factory(
                                            application,
                                            saveSyncManager,
                                        ),
                                ),
                        )
                    }
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                shortcutSupported = gameInteractor.supportShortcuts(),
                onGamePlay = { gameInteractor.onGamePlay(it) },
                onGameRestart = { gameInteractor.onGameRestart(it) },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                onSetCustomName = { game, name -> gameInteractor.onSetCustomName(game, name) },
                onClearCustomName = { game -> gameInteractor.onClearCustomName(game) },
                onSetCustomCoverUri = { game, uri -> gameInteractor.onSetCustomCoverUri(game, uri) },
                onClearCustomCoverUri = { game -> gameInteractor.onClearCustomCoverUri(game) },
            )

            if (infoDialogDisplayed.value) {
                val message =
                    remember {
                        val systemFolders =
                            SystemID.values()
                                .joinToString(", ") { "<i>${it.dbname}</i>" }

                        getString(R.string.lemuroid_help_content)
                            .replace("\$SYSTEMS", systemFolders)
                    }

                AlertDialog(
                    text = { HtmlText(text = message) },
                    onDismissRequest = { infoDialogDisplayed.value = false },
                    confirmButton = { },
                )
            }
        }
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        true,
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                }
            }
        }
    }

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                customCoverManager: CustomCoverManager,
            ) = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher, customCoverManager)
        }
    }
}
