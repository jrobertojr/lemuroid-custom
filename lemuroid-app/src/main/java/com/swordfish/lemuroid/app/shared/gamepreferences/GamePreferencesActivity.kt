package com.swordfish.lemuroid.app.shared.gamepreferences

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.GamePreferencesManager
import com.swordfish.lemuroid.lib.preferences.PerformanceProfile
import com.swordfish.lemuroid.lib.preferences.PerformanceProfileManager
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One controller-friendly editor for concrete ROMs, including individual discs/versions. */
class GamePreferencesActivity : ImmersiveActivity() {
    @Inject lateinit var coresSelection: CoresSelection
    @Inject lateinit var gameLauncher: GameLauncher
    @Inject lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler
    private lateinit var game: Game
    private lateinit var preferences: GamePreferencesManager
    private var launching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        game = intent.getSerializableExtra(GameMenuContract.EXTRA_GAME) as? Game ?: run { finish(); return }
        preferences = GamePreferencesManager(SharedPreferencesHelper.getSharedPreferences(this))
        render()
    }

    private fun profileName(profile: PerformanceProfile): String = when (profile) {
        PerformanceProfile.QUALITY -> "Qualidade"
        PerformanceProfile.BALANCED -> "Equilibrado"
        PerformanceProfile.PERFORMANCE -> "Desempenho"
    }

    private fun render(focusIndex: Int = 0) {
        val system = GameSystem.findById(game.systemId)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun text(value: String, size: Float = 18f) {
            column.addView(TextView(this).apply { text = value; textSize = size; setPadding(8, 8, 8, 12) })
        }
        val buttons = mutableListOf<Button>()
        fun button(label: String, action: () -> Unit) {
            val button = Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }
            buttons.add(button)
            column.addView(button, LinearLayout.LayoutParams(-1, -2))
        }
        text(game.displayName(), 24f)
        text("${getString(system.titleResId)} • Escolhas salvas para este arquivo de jogo")
        val core = preferences.getCoreOverride(game)
        val inheritedCore = coresSelection.getCurrentCoreForSystem(system)
        button("Núcleo do jogo: ${core?.coreDisplayName ?: "Usar o do sistema (${inheritedCore.coreDisplayName})"}") {
            val cores = system.systemCoreConfigs.map { it.coreID }
            val labels = listOf("Usar o núcleo do sistema") + cores.map { it.coreDisplayName }
            AlertDialog.Builder(this).setTitle("Núcleo deste jogo")
                .setSingleChoiceItems(labels.toTypedArray(), cores.indexOf(core) + 1) { dialog, which ->
                    if (preferences.setCoreOverride(game, cores.getOrNull(which - 1))) { dialog.dismiss(); render(0) }
                }.setNegativeButton("Cancelar", null).create().showForController()
        }
        val gameProfile = preferences.getGameProfileOverride(game)
        val systemProfile = preferences.getSystemProfileOverride(system.id)
        val global = PerformanceProfileManager.getActiveProfile(this)
        button("Modo do jogo: ${gameProfile?.let(::profileName) ?: "Usar o do sistema (${profileName(systemProfile ?: global)})"}") {
            chooseProfile("Modo deste jogo", gameProfile, "Usar o modo do sistema") {
                preferences.setGameProfileOverride(game, it).also { saved -> if (saved) render(1) }
            }
        }
        button("Modo do sistema: ${systemProfile?.let(::profileName) ?: "Usar o geral (${profileName(global)})"}") {
            chooseProfile("Modo de ${getString(system.titleResId)}", systemProfile, "Usar configuração geral / automática") {
                preferences.setSystemProfileOverride(system.id, it).also { saved -> if (saved) render(2) }
            }
        }
        text("Prioridade: jogo → sistema → geral. A moldura continua seguindo sua configuração. Estados rápidos são separados por núcleo; teste o mesmo trecho e compare imagem e áudio. Arcade exige um ROMset compatível.")
        button("Restaurar este jogo: usar as escolhas do sistema") {
            val prefs = SharedPreferencesHelper.getSharedPreferences(this)
            if (prefs.edit().remove(GamePreferencesManager.coreKey(game)).remove(GamePreferencesManager.gameProfileKey(game)).commit()) render(3)
        }
        if (intent.getBooleanExtra(EXTRA_ALLOW_LAUNCH, true)) {
            button("Iniciar com estas opções / testar núcleo") {
                if (!launching) launching = gameLauncher.launchGameAsync(this, game, true, TVHelper.isTV(this)) { launching = false }
            }
        } else {
            text("As alterações serão aplicadas na próxima abertura do jogo.")
        }
        button("Voltar (escolhas salvas)") { finish() }
        setContentView(ScrollView(this).apply { addView(column) })
        buttons.getOrNull(focusIndex)?.requestFocus()
    }

    private fun AlertDialog.showForController() {
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BUTTON_B) {
                if (event.action == KeyEvent.ACTION_UP) dismiss()
                true
            } else false
        }
        show()
    }

    private fun chooseProfile(title: String, selected: PerformanceProfile?, inherit: String, save: (PerformanceProfile?) -> Boolean) {
        val profiles = listOf(null) + PerformanceProfile.entries
        AlertDialog.Builder(this).setTitle(title)
            .setSingleChoiceItems((listOf(inherit) + PerformanceProfile.entries.map(::profileName)).toTypedArray(), profiles.indexOf(selected)) { dialog, which ->
                if (save(profiles[which])) dialog.dismiss()
            }.setNegativeButton("Cancelar", null).create().showForController()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_B) { finish(); return true }
        return super.onKeyUp(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == BaseGameActivity.REQUEST_PLAY_GAME) {
            lifecycleScope.launch {
                gameLaunchTaskHandler.handleGameFinish(false, this@GamePreferencesActivity, resultCode, data)
                finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ALLOW_LAUNCH = "ALLOW_LAUNCH"
        fun open(context: Context, game: Game, allowLaunch: Boolean = true) {
            context.startActivity(Intent(context, GamePreferencesActivity::class.java)
                .putExtra(GameMenuContract.EXTRA_GAME, game).putExtra(EXTRA_ALLOW_LAUNCH, allowLaunch))
        }
    }
}
