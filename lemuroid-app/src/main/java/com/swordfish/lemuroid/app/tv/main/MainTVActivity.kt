package com.swordfish.lemuroid.app.tv.main

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import coil.imageLoader
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.tv.channel.ChannelUpdateWork
import com.swordfish.lemuroid.app.tv.favorites.TVFavoritesFragment
import com.swordfish.lemuroid.app.tv.games.TVGamesFragment
import com.swordfish.lemuroid.app.tv.home.TVHomeFragment
import com.swordfish.lemuroid.app.tv.search.TVSearchFragment
import com.swordfish.lemuroid.app.tv.settings.TVSettingsActivity
import com.swordfish.lemuroid.app.tv.shared.BaseTVActivity
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.coroutines.safeCollect
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.injection.PerFragment
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import com.swordfish.lemuroid.lib.preferences.PerformanceProfileManager
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainTVActivity : BaseTVActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    var mainViewModel: MainTVViewModel? = null
    private var exitDialog: android.app.AlertDialog? = null
    private var themedComposeInitialized = false
    private val activeFrontendTheme = mutableStateOf(FrontendTheme.DEFAULT)
    private val isForegroundVisible = mutableStateOf(true)

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel?.inProgress?.value ?: false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Register before the NavHost so its inner navigation has first refusal.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmExit() }
        })
        setContentView(R.layout.activity_tv_main)

        val factory = MainTVViewModel.Factory(applicationContext)
        mainViewModel = ViewModelProvider(this, factory).get(MainTVViewModel::class.java)

        launchOnState(Lifecycle.State.CREATED) {
            mainViewModel?.inProgress?.safeCollect {
                findViewById<View>(R.id.tv_loading).isVisible = it
            }
        }

        applyFrontendThemeMode()
        ensureLegacyStoragePermissionsIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        isForegroundVisible.value = true
    }

    override fun onResume() {
        super.onResume()
        GameLauncher.pendingLaunchProfile = null
        isForegroundVisible.value = true
        applyFrontendThemeMode()
    }

    override fun onStop() {
        super.onStop()
        if ((GameLauncher.pendingLaunchProfile ?: PerformanceProfileManager.getActiveProfile(this)) != PerformanceProfile.QUALITY) {
            isForegroundVisible.value = false
            runCatching { imageLoader.memoryCache?.clear() }
        }
    }

    private fun confirmExit() {
        if (exitDialog?.isShowing == true || isFinishing) return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Sair do Lemuroid?")
            .setMessage("Deseja voltar à tela principal da TV?")
            .setNegativeButton("Continuar no aplicativo", null)
            .setPositiveButton("Sair") { _, _ -> finish() }
            .create()
        exitDialog = dialog
        dialog.setOnDismissListener { exitDialog = null }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == android.view.KeyEvent.ACTION_UP) dialog.dismiss()
                true
            } else false
        }
        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).apply {
            isFocusableInTouchMode = true
            requestFocus()
        }
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onDestroy() {
        exitDialog?.dismiss()
        super.onDestroy()
    }

    private fun applyFrontendThemeMode() {
        val prefs = SharedPreferencesHelper.getSharedPreferences(this)
        val themeKey = prefs.getString(getString(R.string.pref_key_frontend_theme), null)
        val theme = FrontendTheme.fromKey(themeKey)
        activeFrontendTheme.value = theme

        val navHostView = findViewById<View>(R.id.nav_host_fragment)
        val themedComposeView = findViewById<ComposeView>(R.id.tv_themed_compose)

        if (theme == FrontendTheme.DEFAULT) {
            val wasNavHostHidden = navHostView.visibility != View.VISIBLE
            if (themedComposeView.hasFocus()) {
                themedComposeView.clearFocus()
            }
            themedComposeView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            themedComposeView.isFocusable = false
            themedComposeView.isFocusableInTouchMode = false
            themedComposeView.visibility = View.GONE

            (navHostView as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            navHostView.visibility = View.VISIBLE
            if (wasNavHostHidden && !navHostView.hasFocus()) {
                navHostView.requestFocus()
            }
        } else {
            if (navHostView.hasFocus()) {
                navHostView.clearFocus()
            }
            (navHostView as? ViewGroup)?.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            navHostView.visibility = View.GONE

            themedComposeView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            themedComposeView.isFocusable = true
            themedComposeView.isFocusableInTouchMode = true
            themedComposeView.visibility = View.VISIBLE

            if (!themedComposeInitialized) {
                themedComposeInitialized = true
                themedComposeView.setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
                )
                themedComposeView.setContent {
                    val browserState = rememberSaveableStateHolder()
                    if (isForegroundVisible.value) {
                        browserState.SaveableStateProvider("game-browser") {
                            AppTheme {
                                CompositionLocalProvider(LocalFrontendTheme provides activeFrontendTheme.value) {
                                    TVThemedFrontend(
                                        retrogradeDb = retrogradeDb,
                                        gameInteractor = gameInteractor,
                                        onOpenSettings = {
                                            startActivity(Intent(this@MainTVActivity, TVSettingsActivity::class.java))
                                        },
                                        onRequestExit = { confirmExit() },
                                        onRescan = {
                                            LibraryIndexScheduler.scheduleLibrarySync(applicationContext)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!themedComposeView.hasFocus()) {
                themedComposeView.requestFocus()
            }
            themedComposeView.post {
                if (themedComposeView.visibility == View.VISIBLE && !themedComposeView.hasFocus()) {
                    themedComposeView.requestFocus()
                }
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(false, this@MainTVActivity, resultCode, data)
                    ChannelUpdateWork.enqueue(applicationContext)
                }
            }
        }
    }

    private fun ensureLegacyStoragePermissionsIfNeeded() {
        if (TVHelper.isSAFSupported(this) || hasLegacyPermissions()) {
            return
        }

        val requestPermission = ActivityResultContracts.RequestPermission()
        val requestPermissionLauncher =
            registerForActivityResult(requestPermission) { isGranted ->
                if (!isGranted) {
                    finish()
                }
            }
        requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun hasLegacyPermissions(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }

    @dagger.Module
    abstract class Module {
        @PerFragment
        @ContributesAndroidInjector(modules = [TVHomeFragment.Module::class])
        abstract fun tvHomeFragment(): TVHomeFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVGamesFragment.Module::class])
        abstract fun tvGamesFragment(): TVGamesFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVSearchFragment.Module::class])
        abstract fun tvSearchFragment(): TVSearchFragment

        @PerFragment
        @ContributesAndroidInjector(modules = [TVFavoritesFragment.Module::class])
        abstract fun tvFavoritesFragment(): TVFavoritesFragment

        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainTVActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                customCoverManager: CustomCoverManager,
            ) = GameInteractor(activity, retrogradeDb, true, shortcutsGenerator, gameLauncher, customCoverManager)
        }
    }
}
