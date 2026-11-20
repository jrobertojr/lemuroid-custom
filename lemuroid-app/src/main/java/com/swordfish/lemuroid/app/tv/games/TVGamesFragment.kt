package com.swordfish.lemuroid.app.tv.games

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.compose.ui.graphics.toArgb
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.colorScheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.games.GroupedGameEntry
import com.swordfish.lemuroid.app.shared.games.MultiDiscGrouper
import com.swordfish.lemuroid.app.tv.shared.GamePresenter
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class TVGamesFragment : VerticalGridSupportFragment() {
    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    private val args: TVGamesFragmentArgs by navArgs()

    init {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = 4
        setGridPresenter(gridPresenter)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val factory = TVGamesViewModel.Factory(retrogradeDb)
        val gamesViewModel = ViewModelProvider(this, factory)[TVGamesViewModel::class.java]

        val cardSize = resources.getDimensionPixelSize(com.swordfish.lemuroid.lib.R.dimen.card_size)
        val multiDiscMap = HashMap<Int, GroupedGameEntry.MultiDisc>()
        val gamesAdapter = ArrayObjectAdapter(GamePresenter(cardSize, gameInteractor) { it.id !in multiDiscMap })
        this.adapter = gamesAdapter

        launchOnState(Lifecycle.State.RESUMED) {
            gamesViewModel.games.collect { rawGames ->
                val grouped = MultiDiscGrouper.group(rawGames)
                multiDiscMap.clear()
                val displayGames =
                    grouped.map { entry ->
                        when (entry) {
                            is GroupedGameEntry.Single -> entry.game
                            is GroupedGameEntry.MultiDisc -> {
                                multiDiscMap[entry.representative.id] = entry
                                entry.representative
                            }
                        }
                    }
                gamesAdapter.setItems(displayGames, null)
            }
        }

        args.metaSystemId.let {
            gamesViewModel.metaSystemId.value = MetaSystemID.valueOf(it)
        }

        onItemViewClickedListener =
            OnItemViewClickedListener { _, item, _, _ ->
                if (item is Game) {
                    val multiDisc = multiDiscMap[item.id]
                    if (multiDisc != null) {
                        val discNames = multiDisc.discs.map { multiDisc.itemLabel(it) }.toTypedArray()
                        val dialog = AlertDialog.Builder(requireContext())
                            .setTitle(multiDisc.folderName)
                            .setItems(discNames) { _, which ->
                                gameInteractor.onGamePlay(multiDisc.discs[which])
                            }
                            .setNegativeButton(android.R.string.cancel, null)
                            .create()
                        dialog.setOnKeyListener { _, keyCode, event ->
                            if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_X) {
                                if (event.action == android.view.KeyEvent.ACTION_UP) {
                                    val position = dialog.listView.selectedItemPosition
                                    multiDisc.discs.getOrNull(position)?.let { disc ->
                                        dialog.dismiss()
                                        gameInteractor.onGamePreferences(disc)
                                    }
                                }
                                true
                            } else false
                        }
                        dialog.show()
                    } else {
                        gameInteractor.onGamePlay(item)
                    }
                }
            }
    }

    override fun onResume() {
        super.onResume()
        val ctx = context ?: return
        val themeKey =
            com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
                .getSharedPreferences(ctx)
                .getString(getString(R.string.pref_key_frontend_theme), "default")
        FrontendTheme.fromKey(themeKey).colorScheme()?.let { scheme ->
            view?.setBackgroundColor(scheme.background.toArgb())
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    @dagger.Module
    class Module
}
