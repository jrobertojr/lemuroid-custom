package com.swordfish.lemuroid.app.shared.game

import android.app.Activity
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GameLauncher(
    private val coresSelection: CoresSelection,
    private val gameLaunchTaskHandler: GameLaunchTaskHandler,
    private val coreUpdater: com.swordfish.lemuroid.lib.core.CoreUpdater,
) {
    @OptIn(DelicateCoroutinesApi::class)
    fun launchGameAsync(
        activity: Activity,
        game: Game,
        loadSave: Boolean,
        leanback: Boolean,
        onPreparationFailed: () -> Unit = {},
    ): Boolean {
        if (GameProcessLock.isHeldByAnotherProcess(activity.applicationContext)) {
            activity.displayToast(R.string.game_process_another_game_running)
            return false
        }

        GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                val coreConfig = coresSelection.getCoreConfigForGame(game)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    coreUpdater.downloadCores(activity.applicationContext, listOf(coreConfig.coreID))
                }
                if (activity.isFinishing || activity.isDestroyed) return@launch
                pendingLaunchProfile = com.swordfish.lemuroid.lib.preferences.PerformanceProfileManager.getActiveProfile(activity, game)
                gameLaunchTaskHandler.handleGameStart(activity.applicationContext)
                BaseGameActivity.launchGame(activity, coreConfig, game, loadSave, leanback)
            } catch (error: Exception) {
                onPreparationFailed()
                if (activity.isFinishing || activity.isDestroyed) return@launch
                timber.log.Timber.e(error, "Could not prepare selected core")
                android.app.AlertDialog.Builder(activity).setTitle("Não foi possível preparar o núcleo")
                    .setMessage("Verifique a conexão e tente novamente, ou selecione outro núcleo.")
                    .setPositiveButton(android.R.string.ok, null).show()
            }
        }

        return true
    }
    companion object {
        var pendingLaunchProfile: com.swordfish.lemuroid.lib.preferences.PerformanceProfile? = null
    }

}
