package com.swordfish.lemuroid.app.tv.shared

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.Game3DObject
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ThemedGameGridItem
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.WoodenShelfDivider
import com.swordfish.lemuroid.app.shared.GameContextMenuListener
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.covers.CoverUtils
import com.swordfish.lemuroid.app.utils.games.GameUtils
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

class GamePresenter(
    private val cardSize: Int,
    private val gameInteractor: GameInteractor,
    private val canConfigure: (Game) -> Boolean = { true },
) : Presenter() {
    override fun onBindViewHolder(
        viewHolder: Presenter.ViewHolder?,
        item: Any?,
    ) {
        if (item == null || viewHolder !is ViewHolder) return
        val game = item as Game
        val context = viewHolder.view.context
        val themeKey =
            SharedPreferencesHelper.getSharedPreferences(context)
                .getString(context.getString(com.swordfish.lemuroid.R.string.pref_key_frontend_theme), "default")
        val theme = FrontendTheme.fromKey(themeKey)

        if (viewHolder.mCardView != null && theme == FrontendTheme.DEFAULT) {
            viewHolder.mCardView.titleText = game.displayName()
            viewHolder.mCardView.contentText = GameUtils.getGameSubtitle(context, game)
            viewHolder.mCardView.setMainImageDimensions(cardSize, cardSize)
            viewHolder.updateCardViewImage(game)
        } else if (viewHolder.composeView != null) {
            val subtitle = buildTVGameDescription(context, game)
            viewHolder.boundGame = game
            viewHolder.boundTheme = theme
            viewHolder.boundSubtitle = subtitle
            viewHolder.renderCompose()
        }
        viewHolder.view.setOnCreateContextMenuListener(GameContextMenuListener(gameInteractor, game))
        viewHolder.view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_X) {
                if (event.action == android.view.KeyEvent.ACTION_UP && canConfigure(game)) gameInteractor.onGamePreferences(game)
                true
            } else false
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
        val context = parent.context
        val themeKey =
            SharedPreferencesHelper.getSharedPreferences(context)
                .getString(context.getString(com.swordfish.lemuroid.R.string.pref_key_frontend_theme), "default")
        val theme = FrontendTheme.fromKey(themeKey)

        if (theme == FrontendTheme.DEFAULT) {
            val cardView = ImageCardView(context)
            cardView.isFocusable = true
            cardView.isFocusableInTouchMode = true
            (cardView.findViewById<View>(androidx.leanback.R.id.content_text) as TextView).setTextColor(Color.LTGRAY)
            return ViewHolder(mCardView = cardView)
        }

        val container =
            FrameLayout(context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                clipChildren = false
                clipToPadding = false
            }
        val composeView =
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            }
        container.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        val holder = ViewHolder(container = container, composeView = composeView, cardSizePx = cardSize)
        container.setOnFocusChangeListener { _, hasFocus ->
            holder.isFocusedState.value = hasFocus
        }
        return holder
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) {
        val holder = viewHolder as? ViewHolder ?: return
        holder.mCardView?.mainImage = null
        holder.view.setOnCreateContextMenuListener(null)
        holder.view.setOnKeyListener(null)
    }

    private fun buildTVGameDescription(
        context: android.content.Context,
        game: Game,
    ): String {
        val systemTitle = runCatching { context.getString(GameSystem.findById(game.systemId).shortTitleResId) }.getOrNull()
        val dev = game.developer?.takeIf { it.isNotBlank() }
        val baseSubtitle = GameUtils.getGameSubtitle(context, game)
        return listOfNotNull(systemTitle, dev ?: baseSubtitle.takeIf { it != systemTitle }).distinct().joinToString(" • ")
    }

    class ViewHolder(
        val mCardView: ImageCardView? = null,
        val container: FrameLayout? = null,
        val composeView: ComposeView? = null,
        private val cardSizePx: Int = 0,
    ) : Presenter.ViewHolder(mCardView ?: container!!) {
        val isFocusedState = mutableStateOf(false)
        var boundGame: Game? = null
        var boundTheme: FrontendTheme = FrontendTheme.DEFAULT
        var boundSubtitle: String = ""

        fun updateCardViewImage(game: Game) {
            mCardView?.let { CoverUtils.loadCover(game, it.mainImageView) }
        }

        fun renderCompose() {
            val cv = composeView ?: return
            val game = boundGame ?: return
            val theme = boundTheme
            val subtitle = boundSubtitle

            cv.setContent {
                AppTheme {
                    val focused = isFocusedState.value
                    val scale by animateFloatAsState(if (focused) 1.07f else 1.0f, label = "TVFocusScale")
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val widthDp = with(density) { (cardSizePx * 1.15f).toDp() }.coerceAtLeast(168.dp)

                    TVThemedGameCard(
                        game = game,
                        theme = theme,
                        subtitle = subtitle,
                        focused = focused,
                        scale = scale,
                        widthDp = widthDp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TVThemedGameCard(
    game: Game,
    theme: FrontendTheme,
    subtitle: String,
    focused: Boolean,
    scale: Float,
    widthDp: androidx.compose.ui.unit.Dp,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    val borderWidth = if (focused) 2.5.dp else 0.dp
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier =
            Modifier
                .width(widthDp)
                .padding(6.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if (focused) {
                        Modifier.border(borderWidth, accentColor, shape)
                    } else {
                        Modifier
                    },
                )
                .clip(shape),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (theme == FrontendTheme.THREE_D) {
            Game3DObject(
                game = game,
                onClick = { },
                onLongClick = { },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ThemedGameGridItem(
                game = game,
                theme = theme,
                onClick = { },
                onLongClick = { },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (subtitle.isNotBlank()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (focused) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (theme == FrontendTheme.LOCADORA) {
            WoodenShelfDivider(modifier = Modifier.fillMaxWidth())
        }
    }
}
