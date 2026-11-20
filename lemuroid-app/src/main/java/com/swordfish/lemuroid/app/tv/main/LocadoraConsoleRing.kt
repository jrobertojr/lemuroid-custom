package com.swordfish.lemuroid.app.tv.main

import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalFrontendMotion
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberFrontendLoop

import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.FrontendTheme
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadGlyph
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.GamepadHintBar
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocadoraRing3DView
import com.swordfish.lemuroid.app.shared.ui.FrontendSoundEngine

private val LocadoraWoodDark = Color(0xFF130B08)
private val LocadoraVelvetDeep = Color(0xFF1C100B)
private val LocadoraGold = Color(0xFFFFC107)
private val LocadoraGoldSoft = Color(0xFFFFE082)
private val LocadoraGoldDark = Color(0xFF8C5E00)
private val LocadoraCream = Color(0xFFF7EFE6)
private val LocadoraWarmMuted = Color(0xFFD1BBA6)

data class LocadoraConsoleItem(
    val key: String,
    val title: String,
    val gameCount: Int,
    val accentColor: Color,
)

@Composable
fun LocadoraConsoleRingScreen(
    consoles: List<LocadoraConsoleItem>,
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    onConsoleSelect: (LocadoraConsoleItem) -> Unit,
    onOpenSettings: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val safeSelectedIndex =
        if (consoles.isEmpty()) 0 else selectedIndex.coerceIn(0, consoles.lastIndex)
    val selectedConsole = consoles.getOrNull(safeSelectedIndex)
    val consoleKeys = remember(consoles) { consoles.map { it.key } }

    val activeAccent by animateColorAsState(
        targetValue = selectedConsole?.accentColor ?: LocadoraGold,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "locadoraRingAccent",
    )

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(consoles.size) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(LocadoraWoodDark)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { keyEvent ->
                    val nativeEvent = keyEvent.nativeKeyEvent
                    val keyCode = nativeEvent.keyCode

                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_BUTTON_L1,
                                -> {
                                    if (consoles.isNotEmpty()) {
                                        val nextIndex =
                                            (safeSelectedIndex - 1 + consoles.size) % consoles.size
                                        FrontendSoundEngine.playNavigate(
                                            context,
                                            FrontendTheme.LOCADORA,
                                        )
                                        onIndexChange(nextIndex)
                                    }
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_RIGHT,
                                KeyEvent.KEYCODE_BUTTON_R1,
                                -> {
                                    if (consoles.isNotEmpty()) {
                                        val nextIndex = (safeSelectedIndex + 1) % consoles.size
                                        FrontendSoundEngine.playNavigate(
                                            context,
                                            FrontendTheme.LOCADORA,
                                        )
                                        onIndexChange(nextIndex)
                                    }
                                    true
                                }

                                KeyEvent.KEYCODE_DPAD_UP,
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_BUTTON_A,
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER,
                                KeyEvent.KEYCODE_NUMPAD_ENTER,
                                KeyEvent.KEYCODE_BUTTON_SELECT,
                                KeyEvent.KEYCODE_BUTTON_START,
                                KeyEvent.KEYCODE_MENU,
                                -> true

                                else -> false
                            }
                        }

                        KeyEventType.KeyUp -> {
                            if (nativeEvent.isCanceled) {
                                false
                            } else {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_BUTTON_A,
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                    -> {
                                        if (consoles.isNotEmpty()) {
                                            FrontendSoundEngine.playConfirm(
                                                context,
                                                FrontendTheme.LOCADORA,
                                            )
                                            onConsoleSelect(consoles[safeSelectedIndex])
                                        }
                                        true
                                    }

                                    KeyEvent.KEYCODE_BUTTON_SELECT -> {
                                        FrontendSoundEngine.playConfirm(
                                            context,
                                            FrontendTheme.LOCADORA,
                                        )
                                        onRescan()
                                        true
                                    }

                                    KeyEvent.KEYCODE_BUTTON_START,
                                    KeyEvent.KEYCODE_MENU,
                                    -> {
                                        FrontendSoundEngine.playConfirm(
                                            context,
                                            FrontendTheme.LOCADORA,
                                        )
                                        onOpenSettings()
                                        true
                                    }

                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_DPAD_RIGHT,
                                    KeyEvent.KEYCODE_DPAD_UP,
                                    KeyEvent.KEYCODE_DPAD_DOWN,
                                    KeyEvent.KEYCODE_BUTTON_L1,
                                    KeyEvent.KEYCODE_BUTTON_R1,
                                    -> true

                                    else -> false
                                }
                            }
                        }

                        else -> false
                    }
                },
    ) {
        // Full-screen 3D OpenGL console ring (all consoles in 3D, center console spins)
        if (consoles.isNotEmpty()) {
            LocadoraRing3DView(
                consoleKeys = consoleKeys,
                selectedIndex = safeSelectedIndex,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(LocadoraWoodDark, LocadoraVelvetDeep, LocadoraWoodDark),
                            ),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                LocadoraEmptyRingMessage(onRescan = onRescan)
            }
        }

        // Subtle translucent top scrim so marquee HUD stays crisp over 3D scene
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xB80E0805), Color.Transparent),
                        ),
                    ),
        )

        // Subtle translucent bottom scrim so nameplate + hints stay crisp over 3D scene
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(196.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC0E0805)),
                        ),
                    ),
        )

        // Sleek translucent Locadora HUD overlay
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 36.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            LocadoraMarqueeHeader()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selectedConsole != null) {
                    LocadoraConsoleNameplateCard(
                        item = selectedConsole,
                        accentColor = activeAccent,
                        position = safeSelectedIndex + 1,
                        total = consoles.size,
                        onEnter = {
                            FrontendSoundEngine.playConfirm(context, FrontendTheme.LOCADORA)
                            onConsoleSelect(selectedConsole)
                        },
                    )
                }

                LocadoraLegendBar()
            }
        }
    }
}

@Composable
private fun LocadoraMarqueeHeader() {
    Surface(
        color = Color(0xBD21140D),
        shape = RoundedCornerShape(14.dp),
        border =
            BorderStroke(
                width = 1.6.dp,
                brush =
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                LocadoraGoldDark,
                                LocadoraGold,
                                LocadoraGoldSoft,
                                LocadoraGold,
                                LocadoraGoldDark,
                            ),
                    ),
            ),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 26.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LocadoraGold),
            )
            Text(
                text = "LOCADORA RETRÔ • ESCOLHA O CONSOLE",
                color = LocadoraGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.6.sp,
                fontFamily = FontFamily.Serif,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(LocadoraGold),
            )
        }
    }
}

@Composable
private fun LocadoraConsoleNameplateCard(
    item: LocadoraConsoleItem,
    accentColor: Color,
    position: Int,
    total: Int,
    onEnter: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xCC1E120C),
        border =
            BorderStroke(
                width = 1.6.dp,
                brush =
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                LocadoraGoldDark.copy(alpha = 0.8f),
                                accentColor.copy(alpha = 0.95f),
                                LocadoraGold.copy(alpha = 0.95f),
                                accentColor.copy(alpha = 0.95f),
                                LocadoraGoldDark.copy(alpha = 0.8f),
                            ),
                    ),
            ),
        shadowElevation = 12.dp,
        modifier =
            Modifier
                .focusProperties { canFocus = false }
                .clickable(onClick = onEnter),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accentColor),
            )

            Column(
                horizontalAlignment = Alignment.Start,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = item.title,
                        color = LocadoraCream,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = accentColor.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = "$position / $total",
                            color = LocadoraGoldSoft,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${item.gameCount} ${if (item.gameCount == 1) "jogo na prateleira" else "jogos na prateleira"}",
                    color = LocadoraWarmMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = LocadoraGold.copy(alpha = 0.18f),
                border = BorderStroke(1.3.dp, LocadoraGold),
            ) {
                Text(
                    text = "A Entrar ▶",
                    color = LocadoraGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun LocadoraEmptyRingMessage(onRescan: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xCC241610),
        border = BorderStroke(1.5.dp, LocadoraGoldDark),
        modifier =
            Modifier
                .focusProperties { canFocus = false }
                .clickable(onClick = onRescan),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Nenhum console encontrado",
                color = LocadoraCream,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pressione SELECT para re-escanear suas prateleiras",
                color = LocadoraWarmMuted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun LocadoraLegendBar() {
    GamepadHintBar(
        hints =
            listOf(
                GamepadGlyph.DPAD_LR to "Girar",
                GamepadGlyph.A to "Entrar",
                GamepadGlyph.START to "Opções",
                GamepadGlyph.SELECT to "Reescanear",
            ),
        textColor = LocadoraCream,
        containerColor = Color(0xCC1A100B),
        borderColor = LocadoraGoldDark.copy(alpha = 0.72f),
    )
}
