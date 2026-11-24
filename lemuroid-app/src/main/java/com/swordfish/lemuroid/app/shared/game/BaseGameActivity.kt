package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.app.mobile.feature.game.GameActivity
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOption
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSideEffects
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.tv.game.TVGameActivity
import com.swordfish.lemuroid.common.animationDuration
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.common.dump
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.ExposedSetting
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.Variable
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import dagger.Lazy
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
abstract class BaseGameActivity : ImmersiveActivity() {
    protected lateinit var game: Game
    private lateinit var system: GameSystem
    protected lateinit var systemCoreConfig: SystemCoreConfig

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var savesManager: SavesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    @Inject
    lateinit var coreVariablesManager: CoreVariablesManager

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var gameLoader: GameLoader

    @Inject
    lateinit var controllerConfigsManager: ControllerConfigsManager

    @Inject
    lateinit var rumbleManager: RumbleManager

    @Inject
    lateinit var sharedPreferences: Lazy<SharedPreferences>

    private lateinit var baseGameScreenViewModel: BaseGameScreenViewModel

    private val startGameTime = System.currentTimeMillis()
    private var finishTriggered = false

    private var isNdsScreenPeekActive = false
    private var ndsPeekVariableKey: String? = null
    private var ndsPrimaryScreenLayout: String? = null
    private var isNdsPeekHeldByKey = false
    private var isNdsPeekHeldByAxis = false

    private var multiplayerSession: com.swordfish.lemuroid.app.shared.hub.MultiplayerNetplaySession? = null
    private val floatingNoticeState = androidx.compose.runtime.mutableStateOf<String?>(null)

    @Volatile
    private var isEmulationReady = false

    @Volatile
    private var isActivityResumed = false

    private var mpLastHatUp = false
    private var mpLastHatDown = false
    private var mpLastHatLeft = false
    private var mpLastHatRight = false
    private var mpLastL2 = false
    private var mpLastR2 = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpExceptionsHandler()
        GameService.startService(applicationContext, intent)
        game = intent.getSerializableExtra(EXTRA_GAME) as Game
        systemCoreConfig = intent.getSerializableExtra(EXTRA_SYSTEM_CORE_CONFIG) as SystemCoreConfig
        system = GameSystem.findById(game.systemId)

        val viewModel by viewModels<BaseGameScreenViewModel> {
            BaseGameScreenViewModel.Factory(
                applicationContext,
                game,
                settingsManager,
                inputDeviceManager,
                controllerConfigsManager,
                system,
                systemCoreConfig,
                sharedPreferences.get(),
                savesManager,
                statesManager,
                statesPreviewManager,
                coreVariablesManager,
                rumbleManager,
            )
        }

        baseGameScreenViewModel = viewModel

        lifecycle.addObserver(baseGameScreenViewModel)

        setContent {
            AppTheme {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                ) {
                    BaseGameScreen(viewModel = baseGameScreenViewModel) {
                        GameScreen(viewModel)
                    }
                    val notice = floatingNoticeState.value
                    androidx.compose.runtime.LaunchedEffect(notice) {
                        if (notice != null) {
                            delay(3800L)
                            if (floatingNoticeState.value == notice) {
                                floatingNoticeState.value = null
                            }
                        }
                    }
                    if (notice != null) {
                        androidx.compose.material3.Surface(
                            color = androidx.compose.ui.graphics.Color(0xE80D1320),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.3.dp,
                                    androidx.compose.ui.graphics.Color(0xFF38BDF8),
                                ),
                            modifier =
                                androidx.compose.ui.Modifier
                                    .align(androidx.compose.ui.Alignment.TopCenter)
                                    .padding(top = 18.dp, start = 24.dp, end = 24.dp),
                        ) {
                            androidx.compose.material3.Text(
                                text = notice,
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier =
                                    androidx.compose.ui.Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 9.dp,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        val mpConfig =
            intent.serializable<com.swordfish.lemuroid.app.shared.hub.MultiplayerSessionConfig>(
                EXTRA_MULTIPLAYER_SESSION,
            )
        if (mpConfig != null) {
            val session =
                com.swordfish.lemuroid.app.shared.hub.MultiplayerNetplaySession(
                    config = mpConfig,
                    onStatusNotice = { msg -> runOnUiThread { floatingNoticeState.value = msg } },
                    onRemoteKeyEvent = { action, keyCode, port ->
                        if (isEmulationReady && !finishTriggered) {
                            baseGameScreenViewModel.retroGameView.retroGameView?.sendKeyEvent(action, keyCode, port)
                        }
                    },
                    onRemoteMotionEvent = { source, x, y, port ->
                        if (isEmulationReady && !finishTriggered) {
                            baseGameScreenViewModel.retroGameView.retroGameView?.sendMotionEvent(source, x, y, port)
                        }
                    },
                    onRequestSerializeState = {
                        if (!isEmulationReady || !isActivityResumed || finishTriggered || isFinishing || baseGameScreenViewModel.loadingState.value) {
                            null
                        } else {
                            runCatching {
                                baseGameScreenViewModel.retroGameView.retroGameView?.serializeState()
                            }.getOrNull()
                        }
                    },
                    onApplyRemoteState = { stateBytes ->
                        if (isEmulationReady && isActivityResumed && !finishTriggered && !isFinishing && !baseGameScreenViewModel.loadingState.value) {
                            runCatching {
                                baseGameScreenViewModel.retroGameView.retroGameView?.unserializeState(stateBytes)
                            }
                        }
                    },
                )
            multiplayerSession = session
            lifecycleScope.launch {
                baseGameScreenViewModel.retroGameView.waitGLEvent<com.swordfish.libretrodroid.GLRetroView.GLRetroEvents.FrameRendered>()
                isEmulationReady = true
                if (!finishTriggered && !isFinishing && !isDestroyed) {
                    session.start()
                }
            }
        }

        lifecycleScope.launch {
            val shouldLoadSave =
                intent.getBooleanExtra(EXTRA_LOAD_SAVE, false) && (mpConfig == null || mpConfig.isHost)
            baseGameScreenViewModel.loadGame(
                applicationContext,
                game,
                systemCoreConfig,
                gameLoader,
                shouldLoadSave,
            )
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    baseGameScreenViewModel.requestFinish()
                }
            },
        )

        initialiseFlows()
    }

    @Composable
    abstract fun GameScreen(viewModel: BaseGameScreenViewModel)

    private fun initialiseFlows() {
        launchOnState(Lifecycle.State.CREATED) {
            initializeViewModelsEffectsFlow()
        }
    }

    private fun setUpExceptionsHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            performUnexpectedErrorFinish(exception)
        }
    }

    private fun transformExposedSetting(
        exposedSetting: ExposedSetting,
        coreOptions: List<CoreOption>,
    ): LemuroidCoreOption? {
        return coreOptions
            .firstOrNull { it.variable.key == exposedSetting.key }
            ?.let { LemuroidCoreOption(exposedSetting, it) }
    }

    private fun displayOptionsDialog(
        currentTiltConfiguration: TiltConfiguration,
        tiltConfigurations: List<TiltConfiguration>,
    ) {
        stopNdsScreenPeek()

        if (baseGameScreenViewModel.loadingState.value) {
            return
        }

        val coreOptions = getCoreOptions()

        val options =
            systemCoreConfig.exposedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val advancedOptions =
            systemCoreConfig.exposedAdvancedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val intent =
            Intent(this, getDialogClass()).apply {
                this.putExtra(GameMenuContract.EXTRA_CORE_OPTIONS, options.toTypedArray())
                this.putExtra(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS, advancedOptions.toTypedArray())
                this.putExtra(
                    GameMenuContract.EXTRA_CURRENT_DISK,
                    baseGameScreenViewModel.retroGameView.retroGameView?.getCurrentDisk() ?: 0,
                )
                this.putExtra(
                    GameMenuContract.EXTRA_DISKS,
                    baseGameScreenViewModel.retroGameView.retroGameView?.getAvailableDisks() ?: 0,
                )
                this.putExtra(GameMenuContract.EXTRA_GAME, game)
                this.putExtra(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                this.putExtra(
                    GameMenuContract.EXTRA_AUDIO_ENABLED,
                    baseGameScreenViewModel.retroGameView.retroGameView?.audioEnabled,
                )
                this.putExtra(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, system.fastForwardSupport)
                this.putExtra(
                    GameMenuContract.EXTRA_FAST_FORWARD,
                    (baseGameScreenViewModel.retroGameView.retroGameView?.frameSpeed ?: 1) > 1,
                )
                this.putExtra(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG, currentTiltConfiguration)
                // TODO PADS... Make sure to avoid passing this if a physical pad is connected.
                this.putExtra(GameMenuContract.EXTRA_TILT_ALL_CONFIGS, tiltConfigurations.toTypedArray())
            }
        startActivityForResult(intent, DIALOG_REQUEST)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    protected abstract fun getDialogClass(): Class<out Activity>

    private fun getCoreOptions(): List<CoreOption> {
        return baseGameScreenViewModel.retroGameView.retroGameView?.getVariables()
            ?.mapNotNull {
                val coreOptionResult =
                    runCatching {
                        CoreOption.fromLibretroDroidVariable(it)
                    }
                coreOptionResult.getOrNull()
            } ?: listOf()
    }

    private suspend fun initializeViewModelsEffectsFlow() {
        baseGameScreenViewModel.getSideEffects()
            .collect {
                when (it) {
                    is GameViewModelSideEffects.UiEffect.ShowMenu ->
                        displayOptionsDialog(
                            it.currentTiltConfiguration,
                            it.tiltConfigurations,
                        )
                    is GameViewModelSideEffects.UiEffect.ShowToast -> displayToast(it.message)
                    is GameViewModelSideEffects.UiEffect.SuccessfulFinish -> performSuccessfulActivityFinish()
                    is GameViewModelSideEffects.UiEffect.FailureFinish -> performErrorFinish(it.message)
                    is GameViewModelSideEffects.UiEffect.SaveQuickSave -> performSaveQuickSave()
                    is GameViewModelSideEffects.UiEffect.LoadQuickSave -> performLoadQuickSave()
                    is GameViewModelSideEffects.UiEffect.ToggleFastForward -> performToggleFastForward()
                }
            }
    }

    private fun performSaveQuickSave() {
        baseGameScreenViewModel.saveQuickSave()
    }

    private fun performLoadQuickSave() {
        baseGameScreenViewModel.loadQuickSave()
    }

    private fun performToggleFastForward() {
        baseGameScreenViewModel.toggleFastForward()
    }

    private fun resolveNdsSingleScreenLayout(): Triple<String, String, String>? {
        if (system.id != SystemID.NDS) {
            return null
        }

        val (variableKey, topLayout, bottomLayout) =
            when (systemCoreConfig.coreID) {
                CoreID.DESMUME ->
                    Triple(
                        DESMUME_SCREEN_LAYOUT_KEY,
                        DESMUME_LAYOUT_TOP_ONLY,
                        DESMUME_LAYOUT_BOTTOM_ONLY,
                    )
                CoreID.MELONDS ->
                    Triple(
                        MELONDS_SCREEN_LAYOUT_KEY,
                        MELONDS_LAYOUT_TOP_ONLY,
                        MELONDS_LAYOUT_BOTTOM_ONLY,
                    )
                CoreID.NOODS -> Triple("noods_screenLayout", "top", "bottom")
                CoreID.DSPERATE -> Triple("dsperate_screen_layout", "top", "bottom")
                CoreID.DESMUME_2015 ->
                    Triple("desmume2015_screens_layout", DESMUME_LAYOUT_TOP_ONLY, DESMUME_LAYOUT_BOTTOM_ONLY)
                else -> return null
            }

        val currentLayout =
            baseGameScreenViewModel.retroGameView.retroGameView
                ?.getVariables()
                ?.firstOrNull { it.key == variableKey }
                ?.value
                ?: sharedPreferences.get().getString(
                    CoreVariablesManager.computeSharedPreferenceKey(variableKey, system.id.dbname),
                    null,
                )

        return when (currentLayout) {
            topLayout -> Triple(variableKey, topLayout, bottomLayout)
            bottomLayout -> Triple(variableKey, bottomLayout, topLayout)
            else -> null
        }
    }

    private fun startNdsScreenPeek(): Boolean {
        if (isNdsScreenPeekActive) {
            return true
        }

        val (key, primary, opposite) = resolveNdsSingleScreenLayout() ?: return false
        ndsPeekVariableKey = key
        ndsPrimaryScreenLayout = primary
        isNdsScreenPeekActive = true
        baseGameScreenViewModel.retroGameView.retroGameView?.updateVariables(Variable(key, opposite))
        return true
    }

    private fun stopNdsScreenPeek() {
        if (!isNdsScreenPeekActive) {
            return
        }

        val key = ndsPeekVariableKey
        val primary = ndsPrimaryScreenLayout
        isNdsScreenPeekActive = false
        isNdsPeekHeldByKey = false
        isNdsPeekHeldByAxis = false
        ndsPeekVariableKey = null
        ndsPrimaryScreenLayout = null

        if (key != null && primary != null) {
            baseGameScreenViewModel.retroGameView.retroGameView?.updateVariables(Variable(key, primary))
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (system.id == SystemID.NDS && (event.source and InputDevice.SOURCE_JOYSTICK) != 0) {
            val rTrigger =
                maxOf(
                    event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_GAS),
                )
            if (rTrigger >= NDS_PEEK_TRIGGER_PRESS_THRESHOLD) {
                if (!isNdsScreenPeekActive) {
                    if (startNdsScreenPeek()) {
                        isNdsPeekHeldByAxis = true
                    }
                } else {
                    isNdsPeekHeldByAxis = true
                }
            } else if (rTrigger < NDS_PEEK_TRIGGER_RELEASE_THRESHOLD && isNdsPeekHeldByAxis) {
                isNdsPeekHeldByAxis = false
                if (!isNdsPeekHeldByKey) {
                    stopNdsScreenPeek()
                }
            }
        }

        val mp = multiplayerSession
        if (mp != null && mp.isPeerConnected && (event.source and InputDevice.SOURCE_JOYSTICK) != 0) {
            val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val stickX = event.getAxisValue(MotionEvent.AXIS_X)
            val stickY = event.getAxisValue(MotionEvent.AXIS_Y)
            val upNow = hatY < -0.5f || stickY < -0.5f
            val downNow = hatY > 0.5f || stickY > 0.5f
            val leftNow = hatX < -0.5f || stickX < -0.5f
            val rightNow = hatX > 0.5f || stickX > 0.5f
            val l2Now = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.5f
            val r2Now = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f

            fun syncDigitalAxis(was: Boolean, now: Boolean, keyCode: Int): Boolean {
                if (was != now) {
                    val act = if (now) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
                    if (mp.isPlayingLocally) {
                        mp.sendLocalKey(act, keyCode)
                        if (mp.localPortIndex > 0) {
                            baseGameScreenViewModel.retroGameView.retroGameView?.sendKeyEvent(act, keyCode, mp.localPortIndex)
                        }
                    }
                }
                return now
            }

            mpLastHatUp = syncDigitalAxis(mpLastHatUp, upNow, KeyEvent.KEYCODE_DPAD_UP)
            mpLastHatDown = syncDigitalAxis(mpLastHatDown, downNow, KeyEvent.KEYCODE_DPAD_DOWN)
            mpLastHatLeft = syncDigitalAxis(mpLastHatLeft, leftNow, KeyEvent.KEYCODE_DPAD_LEFT)
            mpLastHatRight = syncDigitalAxis(mpLastHatRight, rightNow, KeyEvent.KEYCODE_DPAD_RIGHT)
            mpLastL2 = syncDigitalAxis(mpLastL2, l2Now, KeyEvent.KEYCODE_BUTTON_L2)
            mpLastR2 = syncDigitalAxis(mpLastR2, r2Now, KeyEvent.KEYCODE_BUTTON_R2)

            if (!mp.isPlayingLocally || mp.localPortIndex > 0) {
                return true
            }
        }

        val handled = baseGameScreenViewModel.sendMotionEvent(event)
        if (handled) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (system.id == SystemID.NDS && keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            if (event.repeatCount > 0 && isNdsScreenPeekActive) {
                return true
            }
            if (startNdsScreenPeek()) {
                isNdsPeekHeldByKey = true
                return true
            }
        }

        val mp = multiplayerSession
        if (mp != null && mp.isPeerConnected && mp.isGameplayKey(keyCode)) {
            if (!mp.isPlayingLocally) {
                return true
            }
            if (event.repeatCount == 0) {
                mp.sendLocalKey(KeyEvent.ACTION_DOWN, keyCode)
                if (mp.localPortIndex > 0) {
                    baseGameScreenViewModel.retroGameView.retroGameView?.sendKeyEvent(
                        KeyEvent.ACTION_DOWN,
                        keyCode,
                        mp.localPortIndex,
                    )
                }
            }
            if (mp.localPortIndex > 0) {
                return true
            }
        }

        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (system.id == SystemID.NDS && keyCode == KeyEvent.KEYCODE_BUTTON_R2 && isNdsScreenPeekActive) {
            isNdsPeekHeldByKey = false
            if (!isNdsPeekHeldByAxis) {
                stopNdsScreenPeek()
            }
            return true
        }

        val mp = multiplayerSession
        if (mp != null && mp.isPeerConnected && mp.isGameplayKey(keyCode)) {
            if (!mp.isPlayingLocally) {
                return true
            }
            mp.sendLocalKey(KeyEvent.ACTION_UP, keyCode)
            if (mp.localPortIndex > 0) {
                baseGameScreenViewModel.retroGameView.retroGameView?.sendKeyEvent(
                    KeyEvent.ACTION_UP,
                    keyCode,
                    mp.localPortIndex,
                )
                return true
            }
        }

        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun performSuccessfulActivityFinish() {
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_SESSION_DURATION, System.currentTimeMillis() - startGameTime)
                putExtra(PLAY_GAME_RESULT_GAME, intent.getSerializableExtra(EXTRA_GAME))
                putExtra(PLAY_GAME_RESULT_LEANBACK, intent.getBooleanExtra(EXTRA_LEANBACK, false))
            }

        setResult(RESULT_OK, resultIntent)
        finishAndExitProcess()
    }

    private fun performUnexpectedErrorFinish(exception: Throwable) {
        Timber.e(exception, "Handling java exception in BaseGameActivity")
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, exception.message)
            }

        setResult(RESULT_UNEXPECTED_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun performErrorFinish(message: String) {
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, message)
            }

        setResult(RESULT_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun finishAndExitProcess() {
        onFinishTriggered()
        GlobalScope.launch {
            delay(animationDuration().toLong())
            GameService.requestTermination()
        }
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    open fun onFinishTriggered() {
        finishTriggered = true
        multiplayerSession?.close()
        multiplayerSession = null
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
    }

    override fun onPause() {
        isActivityResumed = false
        stopNdsScreenPeek()
        super.onPause()
    }

    override fun onStop() {
        if (!finishTriggered && !isFinishing && !isChangingConfigurations) {
            baseGameScreenViewModel.requestBackgroundSave()
        }
        super.onStop()
    }

    override fun onDestroy() {
        multiplayerSession?.close()
        multiplayerSession = null
        if (!isChangingConfigurations) {
            GameService.requestTermination()
        }
        super.onDestroy()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_REQUEST) {
            Timber.i("Game menu dialog response: ${data?.extras.dump()}")
            if (data?.getBooleanExtra(GameMenuContract.RESULT_RESET, false) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.reset()
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_SAVE) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.saveSlot(data.getIntExtra(GameMenuContract.RESULT_SAVE, 0))
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_LOAD) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.loadSlot(data.getIntExtra(GameMenuContract.RESULT_LOAD, 0))
                }
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_QUIT, false) == true) {
                baseGameScreenViewModel.requestFinish()
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_DISK) == true) {
                val index = data.getIntExtra(GameMenuContract.RESULT_CHANGE_DISK, 0)
                baseGameScreenViewModel.retroGameView.retroGameView?.changeDisk(index)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_AUDIO) == true) {
                baseGameScreenViewModel.retroGameView.retroGameView?.apply {
                    this.audioEnabled =
                        data.getBooleanExtra(
                            GameMenuContract.RESULT_ENABLE_AUDIO,
                            true,
                        )
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD) == true) {
                baseGameScreenViewModel.retroGameView.retroGameView?.apply {
                    val fastForwardEnabled =
                        data.getBooleanExtra(
                            GameMenuContract.RESULT_ENABLE_FAST_FORWARD,
                            false,
                        )
                    this.frameSpeed = if (fastForwardEnabled) 2 else 1
                }
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, false) == true) {
                baseGameScreenViewModel.showEditControls(true)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_TILT_CONFIG) == true) {
                val tiltConfig = data.serializable<TiltConfiguration>(GameMenuContract.RESULT_CHANGE_TILT_CONFIG)
                baseGameScreenViewModel.changeTiltConfiguration(tiltConfig!!)
            }
        }
    }

    companion object {
        const val DIALOG_REQUEST = 100

        private const val EXTRA_GAME = "GAME"
        private const val EXTRA_LOAD_SAVE = "LOAD_SAVE"
        private const val EXTRA_LEANBACK = "LEANBACK"
        private const val EXTRA_SYSTEM_CORE_CONFIG = "EXTRA_SYSTEM_CORE_CONFIG"
        private const val EXTRA_MULTIPLAYER_SESSION = "EXTRA_MULTIPLAYER_SESSION"

        private const val DESMUME_SCREEN_LAYOUT_KEY = "desmume_screens_layout"
        private const val DESMUME_LAYOUT_TOP_ONLY = "top only"
        private const val DESMUME_LAYOUT_BOTTOM_ONLY = "bottom only"
        private const val MELONDS_SCREEN_LAYOUT_KEY = "melonds_screen_layout1"
        private const val MELONDS_LAYOUT_TOP_ONLY = "top"
        private const val MELONDS_LAYOUT_BOTTOM_ONLY = "bottom"
        private const val NDS_PEEK_TRIGGER_PRESS_THRESHOLD = 0.5f
        private const val NDS_PEEK_TRIGGER_RELEASE_THRESHOLD = 0.35f

        const val REQUEST_PLAY_GAME = 1001
        const val PLAY_GAME_RESULT_SESSION_DURATION = "PLAY_GAME_RESULT_SESSION_DURATION"
        const val PLAY_GAME_RESULT_GAME = "PLAY_GAME_RESULT_GAME"
        const val PLAY_GAME_RESULT_LEANBACK = "PLAY_GAME_RESULT_LEANBACK"
        const val PLAY_GAME_RESULT_ERROR = "PLAY_GAME_RESULT_ERROR"

        const val RESULT_ERROR = Activity.RESULT_FIRST_USER + 2
        const val RESULT_UNEXPECTED_ERROR = Activity.RESULT_FIRST_USER + 3

        fun launchGame(
            activity: Activity,
            systemCoreConfig: SystemCoreConfig,
            game: Game,
            loadSave: Boolean,
            useLeanback: Boolean,
            multiplayerSession: com.swordfish.lemuroid.app.shared.hub.MultiplayerSessionConfig? = null,
        ) {
            val gameActivity =
                if (useLeanback) {
                    TVGameActivity::class.java
                } else {
                    GameActivity::class.java
                }
            activity.startActivityForResult(
                Intent(activity, gameActivity).apply {
                    putExtra(EXTRA_GAME, game)
                    putExtra(EXTRA_LOAD_SAVE, loadSave)
                    putExtra(EXTRA_LEANBACK, useLeanback)
                    putExtra(EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                    if (multiplayerSession != null) {
                        putExtra(EXTRA_MULTIPLAYER_SESSION, multiplayerSession)
                    }
                },
                REQUEST_PLAY_GAME,
            )
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
